#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <oboe/Oboe.h>

#include "MeterBallistics.h"

namespace djmrec {

struct StereoMeterReading {
    float leftPeakDb = kMeterFloorDb;
    float leftRmsDb = kMeterFloorDb;
    float rightPeakDb = kMeterFloorDb;
    float rightRmsDb = kMeterFloorDb;
    bool clipping = false;
};

/**
 * Computes peak + RMS levels per stereo channel directly from the raw interleaved buffer
 * pulled off the ring buffer, without any allocation — called every audio callback so it
 * must stay allocation-free and branch-predictable.
 */
class MeterCalculator {
public:
    static StereoMeterReading analyze(const void* interleaved, int32_t frameCount, oboe::AudioFormat format,
                                      ChannelMeter* meters = nullptr, double rmsAlpha = 1.0) {
        StereoMeterReading reading;
        float leftPeak = 0.0f, rightPeak = 0.0f;
        double leftSumSq = 0.0, rightSumSq = 0.0;

        switch (format) {
            case oboe::AudioFormat::I16: {
                const auto* samples = static_cast<const int16_t*>(interleaved);
                constexpr float scale = 1.0f / 32768.0f;
                for (int32_t i = 0; i < frameCount; ++i) {
                    const float l = samples[i * 2] * scale;
                    const float r = samples[i * 2 + 1] * scale;
                    leftPeak = std::max(leftPeak, std::fabs(l));
                    rightPeak = std::max(rightPeak, std::fabs(r));
                    leftSumSq += static_cast<double>(l) * l;
                    rightSumSq += static_cast<double>(r) * r;
                }
                break;
            }
            case oboe::AudioFormat::I32: {
                const auto* samples = static_cast<const int32_t*>(interleaved);
                constexpr float scale = 1.0f / 2147483648.0f;
                for (int32_t i = 0; i < frameCount; ++i) {
                    const float l = samples[i * 2] * scale;
                    const float r = samples[i * 2 + 1] * scale;
                    leftPeak = std::max(leftPeak, std::fabs(l));
                    rightPeak = std::max(rightPeak, std::fabs(r));
                    leftSumSq += static_cast<double>(l) * l;
                    rightSumSq += static_cast<double>(r) * r;
                }
                break;
            }
            case oboe::AudioFormat::Float: {
                const auto* samples = static_cast<const float*>(interleaved);
                for (int32_t i = 0; i < frameCount; ++i) {
                    const float l = samples[i * 2];
                    const float r = samples[i * 2 + 1];
                    leftPeak = std::max(leftPeak, std::fabs(l));
                    rightPeak = std::max(rightPeak, std::fabs(r));
                    leftSumSq += static_cast<double>(l) * l;
                    rightSumSq += static_cast<double>(r) * r;
                }
                break;
            }
            default:
                // Unsupported format for direct metering (e.g. I24_PACKED handled by caller
                // via pre-expansion to I32 before reaching here).
                break;
        }

        if (frameCount > 0) {
            if (meters) {
                meters[0].push(leftPeak, leftSumSq / frameCount, rmsAlpha);
                meters[1].push(rightPeak, rightSumSq / frameCount, rmsAlpha);
            }
            reading.leftRmsDb = amplitudeToDb(static_cast<float>(std::sqrt(leftSumSq / frameCount)));
            reading.rightRmsDb = amplitudeToDb(static_cast<float>(std::sqrt(rightSumSq / frameCount)));
        }
        reading.leftPeakDb = amplitudeToDb(leftPeak);
        reading.rightPeakDb = amplitudeToDb(rightPeak);
        reading.clipping = leftPeak >= kClipThreshold || rightPeak >= kClipThreshold;
        return reading;
    }
};

} // namespace djmrec
