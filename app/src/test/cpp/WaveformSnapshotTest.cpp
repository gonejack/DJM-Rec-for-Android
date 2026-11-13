#include "WaveformAnalyzer.h"
#include <array>
#include <atomic>
#include <cassert>
#include <cmath>
#include <thread>
#include <vector>
#include <iostream>

int main() {
    djmrec::WaveformAnalyzer analyzer(48000);
    std::atomic<bool> done{false};
    std::thread producer([&] {
        std::vector<int32_t> frames(294 * 2);
        for (int bin = 1; bin <= 4096; ++bin) {
            std::fill(frames.begin(), frames.end(), (bin % 1000) * 1000000);
            analyzer.pushFrames(frames.data(), 294);
        }
        done.store(true);
    });
    do {
        std::array<float, 2048> snapshot{};
        uint32_t end = 0;
        analyzer.getBins(snapshot.data(), &end);
        for (int i = 0; i < 512; ++i) {
            const int bin = static_cast<int>(end) - 511 + i;
            const float expected = bin <= 0 ? 0.0f : (bin % 1000) * 1000000 / 2147483648.0f;
            assert(std::fabs(snapshot[i * 4] - expected) < 0.000001f);
        }
    } while (!done.load());
    producer.join();
    assert(std::fabs(analyzer.binDurationMillis() - 6.125f) < 0.001f);
    analyzer.reset();
    std::vector<int32_t> leftOnly(294 * 2 * 4, 0);
    for (size_t frame = 0; frame < leftOnly.size() / 2; ++frame)
        leftOnly[frame * 2] = static_cast<int32_t>(1000000000.0 * std::sin(frame * 0.1));
    analyzer.pushFrames(leftOnly.data(), leftOnly.size() / 2);
    std::array<float, 2048> left{}, right{}, combined{};
    uint32_t leftCursor = 0, rightCursor = 0;
    analyzer.getChannelBins(0, left.data(), &leftCursor);
    analyzer.getChannelBins(1, right.data(), &rightCursor);
    analyzer.getBins(combined.data());
    assert(leftCursor == 4 && rightCursor == 4);
    assert(left[2044] > 0.4f);
    for (int i = 0; i < 2048; ++i) {
        assert(right[i] == 0.0f);
        assert(combined[i] == left[i]);
    }
    analyzer.reset();
    analyzer.getChannelBins(0, left.data(), &leftCursor);
    analyzer.getChannelBins(1, right.data(), &rightCursor);
    assert(leftCursor == 0 && rightCursor == 0);
    for (int i = 0; i < 2048; ++i) assert(left[i] == 0.0f && right[i] == 0.0f);
    analyzer.pushFrames(leftOnly.data(), leftOnly.size() / 2, 2, 0, 1);
    analyzer.getChannelBins(0, left.data());
    analyzer.getChannelBins(1, right.data());
    assert(left == right);
    std::cout << "Concurrent waveform, isolated channels, reset and mono checks passed\n";
}
