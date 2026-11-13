#pragma once

#include <cstddef>
#include <cstdint>

namespace djmrec {

constexpr int kMixNone = -1;

// Encode None as zero, keeping UINT32_MAX available for legacy USB pair extraction.
constexpr uint32_t packMixRoute(int left, int right) {
    return static_cast<uint32_t>(left + 1) | (static_cast<uint32_t>(right + 1) << 16);
}
constexpr int mixLeftSource(uint32_t route) { return static_cast<int>(route & 0xFFFF) - 1; }
constexpr int mixRightSource(uint32_t route) { return static_cast<int>(route >> 16) - 1; }
constexpr int mixOutputChannels(int left, int right) {
    return left != kMixNone && left == right ? 1 : 2;
}

/** Internal processing stays stereo; the encoder packs identical sources into mono. */
inline bool renderStereoMix(const int32_t* tracks, size_t frames, int channels,
                            int left, int right, int32_t* output) {
    if (!tracks || !output || left < kMixNone || right < kMixNone || left >= channels || right >= channels) {
        return false;
    }
    for (size_t frame = 0; frame < frames; ++frame) {
        output[frame * 2] = left == kMixNone ? 0 : tracks[frame * channels + left];
        output[frame * 2 + 1] = right == kMixNone ? 0 : tracks[frame * channels + right];
    }
    return true;
}

} // namespace djmrec
