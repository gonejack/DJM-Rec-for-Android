#pragma once

#include <algorithm>
#include <array>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <limits>
#include <vector>

namespace djmrec {

/**
 * Bounded stereo FIFO with optional asynchronous sample-rate conversion.
 * The caller serializes push/read/reset. All storage and filter tables are prepared in reset(),
 * before capture starts; reading never allocates. Both stereo channels share one fractional
 * position, so correcting a clock cannot move L and R apart.
 */
class ClockedStereoBuffer {
public:
    static constexpr int kLatencyMs = 40;

    void reset(int sampleRate) {
        mSampleRate = std::max(1, sampleRate);
        mTargetFrames = static_cast<size_t>(mSampleRate * kLatencyMs / 1000);
        mCapacity = static_cast<size_t>(mSampleRate / 5) + kTaps * 2;
        mSamples.assign(mCapacity * 2, 0);
        mRead = mWrite = mAvailable = 0;
        mDroppedFrames = mMissingFrames = 0;
        mRecovering = false;
        resetController();
        (void)filterTable(); // Build the shared polyphase table outside the audio callback.
    }

    void push(const int32_t* stereo, size_t frameCount) {
        for (size_t frame = 0; frame < frameCount; ++frame) {
            // Retain enough old samples for the interpolation filter's left wing.
            if (mAvailable == mCapacity - kTaps) {
                advance();
                ++mDroppedFrames;
                mRecovering = true;
                resetController();
            }
            mSamples[mWrite * 2] = stereo[frame * 2];
            mSamples[mWrite * 2 + 1] = stereo[frame * 2 + 1];
            mWrite = (mWrite + 1) % mCapacity;
            ++mAvailable;
        }
    }

    /** Exact FIFO read, also used for Track 1 after its fixed delay has accumulated. */
    void readDirect(int32_t* output, size_t frameCount, size_t stride) {
        for (size_t frame = 0; frame < frameCount; ++frame) {
            if (mAvailable == 0) {
                output[frame * stride] = output[frame * stride + 1] = 0;
                ++mMissingFrames;
            } else {
                output[frame * stride] = sample(0, 0);
                output[frame * stride + 1] = sample(0, 1);
                advance();
            }
        }
    }

    /** Emits exactly frameCount frames on Track 1's clock, gently steering the FIFO fill. */
    void readSynced(int32_t* output, size_t frameCount, size_t stride) {
        if (frameCount == 0) return;
        if (mRecovering) {
            if (mAvailable < mTargetFrames + frameCount + kHalfTaps) {
                silence(output, 0, frameCount, stride);
                return;
            }
            // After an actual USB interruption, resume at the current latency, not stale audio.
            while (mAvailable > mTargetFrames + frameCount + kHalfTaps) {
                advance();
                ++mDroppedFrames;
            }
            mRecovering = false;
            resetController();
        }

        const double seconds = static_cast<double>(frameCount) / mSampleRate;
        const double errorSeconds = (static_cast<double>(mAvailable) - mFraction -
            static_cast<double>(frameCount + mTargetFrames)) / mSampleRate;
        // Smooth USB callback bursts over half a second. A critically damped PI controller
        // corrects sustained clock drift; a short scheduling delay must not change the pitch.
        mFilteredError += seconds / (0.5 + seconds) * (errorSeconds - mFilteredError);
        mIntegral = std::clamp(mIntegral + mFilteredError * seconds * 0.01, -kMaxCorrection, kMaxCorrection);
        mRatio = 1.0 + std::clamp(mFilteredError * 0.2 + mIntegral, -kMaxCorrection, kMaxCorrection);

        const auto& table = filterTable();
        for (size_t frame = 0; frame < frameCount; ++frame) {
            if (mAvailable <= kHalfTaps) {
                silence(output, frame, frameCount, stride);
                mRecovering = true;
                resetController();
                return;
            }
            const double phase = mFraction * kPhases;
            const auto index = static_cast<size_t>(phase);
            const double blend = phase - static_cast<double>(index);
            double left = 0.0, right = 0.0;
            for (int tap = 0; tap < kTaps; ++tap) {
                const double weight = table[index][tap] +
                    (table[index + 1][tap] - table[index][tap]) * blend;
                left += sample(tap - kHalfTaps + 1, 0) * weight;
                right += sample(tap - kHalfTaps + 1, 1) * weight;
            }
            output[frame * stride] = pcm(left);
            output[frame * stride + 1] = pcm(right);
            mFraction += mRatio;
            const auto consumed = static_cast<size_t>(mFraction);
            mFraction -= static_cast<double>(consumed);
            for (size_t i = 0; i < consumed; ++i) advance();
        }
    }

    [[nodiscard]] size_t bufferedFrames() const { return mAvailable; }
    [[nodiscard]] size_t targetFrames() const { return mTargetFrames; }
    [[nodiscard]] uint64_t droppedFrames() const { return mDroppedFrames; }
    [[nodiscard]] uint64_t missingFrames() const { return mMissingFrames; }
    [[nodiscard]] double correctionPpm() const { return (mRatio - 1.0) * 1'000'000.0; }

private:
    static constexpr int kHalfTaps = 16;
    static constexpr int kTaps = kHalfTaps * 2;
    static constexpr int kPhases = 1024;
    static constexpr double kMaxCorrection = 0.005; // +/-5000 ppm, never a format conversion.
    using FilterTable = std::array<std::array<double, kTaps>, kPhases + 1>;

    static const FilterTable& filterTable() {
        static const FilterTable table = [] {
            FilterTable result{};
            constexpr double pi = 3.14159265358979323846;
            // Leave room for the largest permitted downsampling ratio.
            constexpr double cutoff = 0.99;
            for (int phase = 0; phase <= kPhases; ++phase) {
                const double fraction = static_cast<double>(phase) / kPhases;
                double sum = 0.0;
                for (int tap = 0; tap < kTaps; ++tap) {
                    const double x = tap - kHalfTaps + 1 - fraction;
                    const double sinc = std::abs(x) < 1e-12 ? cutoff :
                        std::sin(pi * cutoff * x) / (pi * x);
                    const double window = 0.42 + 0.5 * std::cos(pi * x / kHalfTaps) +
                        0.08 * std::cos(2.0 * pi * x / kHalfTaps);
                    result[phase][tap] = sinc * window;
                    sum += result[phase][tap];
                }
                for (double& weight : result[phase]) weight /= sum;
            }
            return result;
        }();
        return table;
    }

    [[nodiscard]] int32_t sample(int offset, int channel) const {
        const size_t index = (mRead + mCapacity + offset) % mCapacity;
        return mSamples[index * 2 + channel];
    }

    void advance() {
        mRead = (mRead + 1) % mCapacity;
        --mAvailable;
    }

    static int32_t pcm(double value) {
        return static_cast<int32_t>(std::llround(std::clamp(value,
            static_cast<double>(std::numeric_limits<int32_t>::min()),
            static_cast<double>(std::numeric_limits<int32_t>::max()))));
    }

    void silence(int32_t* output, size_t begin, size_t end, size_t stride) {
        for (size_t frame = begin; frame < end; ++frame)
            output[frame * stride] = output[frame * stride + 1] = 0;
        mMissingFrames += end - begin;
    }

    void resetController() {
        mFraction = mFilteredError = mIntegral = 0.0;
        mRatio = 1.0;
    }

    std::vector<int32_t> mSamples;
    size_t mCapacity = 0, mRead = 0, mWrite = 0, mAvailable = 0, mTargetFrames = 0;
    int mSampleRate = 48000;
    double mFraction = 0.0, mFilteredError = 0.0, mIntegral = 0.0, mRatio = 1.0;
    bool mRecovering = false;
    uint64_t mDroppedFrames = 0, mMissingFrames = 0;
};

} // namespace djmrec
