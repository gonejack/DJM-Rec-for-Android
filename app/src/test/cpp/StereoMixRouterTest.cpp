#include "StereoMixRouter.h"

#include <array>
#include <cassert>
#include <iostream>

int main() {
    const std::array<int32_t, 18> tracks{11, -12, 21, -22, 31, -32,
                                      111, -112, 121, -122, 131, -132,
                                      211, -212, 221, -222, 231, -232};
    const auto original = tracks;
    std::array<int32_t, 6> mix{};
    assert(djmrec::renderStereoMix(tracks.data(), 3, 6, 0, 1, mix.data()));
    assert((mix == std::array<int32_t, 6>{11, -12, 111, -112, 211, -212}));
    // Mix L = Track 1 L; Mix R = Track 2 R, with no summing or channel swap.
    assert(djmrec::renderStereoMix(tracks.data(), 3, 6, 0, 3, mix.data()));
    assert((mix == std::array<int32_t, 6>{11, -22, 111, -122, 211, -222}));
    assert(djmrec::renderStereoMix(tracks.data(), 3, 6, 5, 2, mix.data()));
    assert((mix == std::array<int32_t, 6>{-32, 21, -132, 121, -232, 221}));
    // Mono retains a stereo processing buffer but has one encoded channel.
    assert(djmrec::renderStereoMix(tracks.data(), 3, 6, 4, 4, mix.data()));
    assert((mix == std::array<int32_t, 6>{31, 31, 131, 131, 231, 231}));
    assert(djmrec::mixOutputChannels(4, 4) == 1);
    assert(djmrec::mixOutputChannels(0, 1) == 2);
    assert(djmrec::renderStereoMix(tracks.data(), 3, 6, 0, -1, mix.data()));
    assert((mix == std::array<int32_t, 6>{11, 0, 111, 0, 211, 0}));
    assert(djmrec::mixOutputChannels(0, -1) == 2);
    assert(djmrec::renderStereoMix(tracks.data(), 3, 6, -1, 3, mix.data()));
    assert((mix == std::array<int32_t, 6>{0, -22, 0, -122, 0, -222}));
    assert(djmrec::renderStereoMix(tracks.data(), 3, 6, -1, -1, mix.data()));
    assert((mix == std::array<int32_t, 6>{0, 0, 0, 0, 0, 0}));
    assert(djmrec::mixOutputChannels(-1, -1) == 2);
    for (int left = -1; left < 6; ++left) {
        for (int right = -1; right < 6; ++right) {
            const auto route = djmrec::packMixRoute(left, right);
            assert(route != UINT32_MAX);
            assert(djmrec::mixLeftSource(route) == left);
            assert(djmrec::mixRightSource(route) == right);
        }
    }
    assert(tracks == original); // routing never mutates independently recorded tracks
    const auto previous = mix;
    assert(!djmrec::renderStereoMix(tracks.data(), 3, 6, -2, 0, mix.data()));
    assert(!djmrec::renderStereoMix(tracks.data(), 3, 6, 0, 6, mix.data()));
    assert(mix == previous);
    const std::array<int32_t, 4> singleInput{100, -100, 200, -200};
    std::array<int32_t, 4> singleMix{};
    assert(djmrec::renderStereoMix(singleInput.data(), 2, 2, 0, 1, singleMix.data()));
    assert(singleInput == singleMix);
    std::cout << "Stereo Mix checks passed: default, cross-track channels, mono, None silence, route packing, isolation, bounds, single input\n";
}
