#pragma once

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstddef>

namespace djmrec {

constexpr float kMeterFloorDb = -60.0f;
constexpr float kClipThreshold = 0.966f;

inline float amplitudeToDb(float amplitude) {
    if (amplitude <= 0.0f) return kMeterFloorDb;
    return std::clamp(20.0f * std::log10(amplitude), kMeterFloorDb, 0.0f);
}

/** The same 300 ms RMS response for every input, independent of USB callback size. */
inline double meterRmsAlpha(size_t frames, int sampleRate) {
    return -std::expm1(-static_cast<double>(frames) / (std::max(1, sampleRate) * 0.3));
}

/** One audio producer; atomic snapshots for the UI. Never modifies recorded samples. */
class ChannelMeter {
public:
    /** Call only after the audio producer has stopped. */
    void reset() {
        mMeanSquare = 0.0;
        mPeak.store(0.0f, std::memory_order_relaxed);
        mRms.store(0.0f, std::memory_order_relaxed);
        mClip.store(false, std::memory_order_relaxed);
    }

    void push(float peak, double meanSquare, double alpha) {
        float previous = mPeak.load(std::memory_order_relaxed);
        while (peak > previous &&
               !mPeak.compare_exchange_weak(previous, peak, std::memory_order_relaxed)) {
        }
        if (peak >= kClipThreshold) mClip.store(true, std::memory_order_relaxed);
        mMeanSquare += (meanSquare - mMeanSquare) * alpha;
        mRms.store(static_cast<float>(std::sqrt(std::max(0.0, mMeanSquare))), std::memory_order_relaxed);
    }

    float takePeakDb() { return amplitudeToDb(mPeak.exchange(0.0f, std::memory_order_relaxed)); }
    float rmsDb() const { return amplitudeToDb(mRms.load(std::memory_order_relaxed)); }
    bool takeClipping() { return mClip.exchange(false, std::memory_order_relaxed); }

private:
    double mMeanSquare = 0.0; // audio producer only
    std::atomic<float> mPeak{0.0f};
    std::atomic<float> mRms{0.0f};
    std::atomic<bool> mClip{false};
};

} // namespace djmrec
