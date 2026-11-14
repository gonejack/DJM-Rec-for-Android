#include "ClockedStereoBuffer.h"
#include "StereoMixRouter.h"

#include <array>
#include <cassert>
#include <cmath>
#include <cstdint>
#include <iostream>
#include <limits>
#include <vector>

using djmrec::ClockedStereoBuffer;

static void directReadsStayExact() {
    ClockedStereoBuffer buffer;
    buffer.reset(48000);
    const std::array<int32_t, 8> input{0, -1, 1, -2,
        std::numeric_limits<int32_t>::max(), std::numeric_limits<int32_t>::min(), 1234, -5678};
    buffer.push(input.data(), 4);
    std::array<int32_t, 30> output;
    output.fill(99);
    buffer.readDirect(output.data() + 2, 5, 6);
    for (size_t frame = 0; frame < 4; ++frame) {
        assert(output[frame * 6 + 2] == input[frame * 2]);
        assert(output[frame * 6 + 3] == input[frame * 2 + 1]);
        assert(output[frame * 6] == 99 && output[frame * 6 + 5] == 99);
    }
    assert(output[26] == 0 && output[27] == 0);
    assert(buffer.missingFrames() == 1);
    assert(buffer.bufferedFrames() == 0);
}

static void masterDelayAndMixShareOneTimeline(int rate) {
    ClockedStereoBuffer master, track2, track3;
    master.reset(rate);
    track2.reset(rate);
    track3.reset(rate);
    const size_t block = static_cast<size_t>(rate / 100);
    std::vector<int32_t> input(block * 2), combined(block * 6), mix(block * 2);
    size_t outputFrames = 0;
    double squaredError = 0.0, squaredSignal = 0.0;
    for (size_t tick = 0; tick < 100; ++tick) {
        for (size_t frame = 0; frame < block; ++frame) {
            input[frame * 2] = static_cast<int32_t>(std::llround(
                1'000'000'000.0 * std::sin((tick * block + frame) * 0.05)));
            input[frame * 2 + 1] = -input[frame * 2];
        }
        master.push(input.data(), block);
        track2.push(input.data(), block);
        track3.push(input.data(), block);
        if (master.bufferedFrames() < master.targetFrames() + block) continue;
        master.readDirect(combined.data(), block, 6);
        track2.readSynced(combined.data() + 2, block, 6);
        track3.readSynced(combined.data() + 4, block, 6);
        assert(djmrec::renderStereoMix(combined.data(), block, 6, 0, 5, mix.data()));
        for (size_t frame = 0; frame < block; ++frame) {
            const int32_t expected = static_cast<int32_t>(std::llround(
                1'000'000'000.0 * std::sin((outputFrames + frame) * 0.05)));
            assert(combined[frame * 6] == expected); // Track 1 is never resampled.
            assert(combined[frame * 6 + 1] == -expected);
            assert(combined[frame * 6 + 2] == combined[frame * 6 + 4]);
            assert(combined[frame * 6 + 3] == -combined[frame * 6 + 2]);
            assert(mix[frame * 2] == expected);
            assert(mix[frame * 2 + 1] == combined[frame * 6 + 5]);
            if (outputFrames + frame > 64) {
                const double error = static_cast<double>(expected) - combined[frame * 6 + 2];
                squaredError += error * error;
                squaredSignal += static_cast<double>(expected) * expected;
            }
        }
        outputFrames += block;
    }
    assert(outputFrames > static_cast<size_t>(rate * 9 / 10));
    assert(master.missingFrames() == 0 && track2.missingFrames() == 0 && track3.missingFrames() == 0);
    assert(track2.droppedFrames() == 0 && track3.droppedFrames() == 0);
    assert(squaredError / squaredSignal < 1e-8);
}

// Ten minutes in Track 1 frame time, with one USB source delivering 20 ms bursts.
// A reduced sample rate keeps this deterministic long-run controller test fast.
static void independentClocksConverge(double ppm) {
    constexpr int rate = 1000;
    constexpr size_t block = 5;
    ClockedStereoBuffer master, synced, unsynced;
    master.reset(rate);
    synced.reset(rate);
    unsynced.reset(rate);
    const double ratio = 1.0 + ppm / 1'000'000.0;
    std::array<int32_t, block * 2> primary{}, output{}, raw{};
    std::vector<int32_t> input;
    size_t pushed = 0, rendered = 0;
    double correctionSum = 0.0;
    size_t correctionCount = 0;
    for (size_t tick = 1; tick <= 120'000; ++tick) {
        if (tick % 4 == 0) {
            const size_t total = static_cast<size_t>(std::floor(tick * block * ratio));
            input.resize((total - pushed) * 2);
            for (size_t frame = 0; frame < total - pushed; ++frame) {
                const int32_t value = 100'000'000 + static_cast<int32_t>((pushed + frame) * 64);
                input[frame * 2] = value;
                input[frame * 2 + 1] = -value;
            }
            synced.push(input.data(), total - pushed);
            unsynced.push(input.data(), total - pushed);
            pushed = total;
        }
        master.push(primary.data(), block);
        if (master.bufferedFrames() < master.targetFrames() + block) continue;
        master.readDirect(primary.data(), block, 2);
        synced.readSynced(output.data(), block, 2);
        unsynced.readDirect(raw.data(), block, 2);
        for (size_t frame = 0; frame < block; ++frame)
            assert(output[frame * 2] == -output[frame * 2 + 1]);
        rendered += block;
        if (tick > 118'000) {
            correctionSum += synced.correctionPpm();
            ++correctionCount;
            assert(std::abs(static_cast<double>(synced.bufferedFrames()) - synced.targetFrames()) < 25);
        }
    }
    std::cout << "clock " << ppm << " ppm: correction=" << correctionSum / correctionCount
              << ", buffered=" << synced.bufferedFrames()
              << ", missing=" << synced.missingFrames() << ", dropped=" << synced.droppedFrames() << '\n';
    assert(rendered == 600'000 - master.targetFrames());
    assert(synced.missingFrames() == 0 && synced.droppedFrames() == 0);
    assert(std::abs(correctionSum / correctionCount - ppm) < 20.0);
    assert(unsynced.missingFrames() > 0 || unsynced.droppedFrames() > 0);
}

static void interruptionsRecoverAndResetClearsHistory() {
    ClockedStereoBuffer buffer;
    buffer.reset(48000);
    std::vector<int32_t> input(4800 * 2, 123456);
    std::array<int32_t, 960> output{};
    buffer.push(input.data(), 2400);
    buffer.readSynced(output.data(), 480, 2);
    for (int i = 0; i < 20; ++i) buffer.readSynced(output.data(), 480, 2);
    assert(buffer.missingFrames() > 0);
    for (int32_t value : output) assert(value == 0);
    for (int tick = 0; tick < 20; ++tick) {
        buffer.push(input.data(), 480);
        buffer.readSynced(output.data(), 480, 2);
    }
    for (int32_t value : output) assert(value == 123456);
    buffer.push(input.data(), 4800);
    buffer.push(input.data(), 4800);
    buffer.push(input.data(), 4800);
    assert(buffer.droppedFrames() > 0);
    buffer.readSynced(output.data(), 480, 2);
    assert(buffer.bufferedFrames() <= buffer.targetFrames() + 16);

    buffer.reset(48000);
    assert(buffer.bufferedFrames() == 0);
    assert(buffer.missingFrames() == 0 && buffer.droppedFrames() == 0);
    assert(buffer.correctionPpm() == 0.0);
    input.assign(4800 * 2, 0);
    buffer.push(input.data(), 2400);
    buffer.readSynced(output.data(), 480, 2);
    for (int32_t value : output) assert(value == 0);
}

static void fullScaleCannotWrap() {
    for (int32_t value : {std::numeric_limits<int32_t>::min(), std::numeric_limits<int32_t>::max()}) {
        ClockedStereoBuffer buffer;
        buffer.reset(48000);
        std::vector<int32_t> input(2400 * 2, value);
        std::array<int32_t, 960> output{};
        buffer.push(input.data(), 2400);
        buffer.readSynced(output.data(), 480, 2);
        for (size_t i = 64; i < output.size(); ++i)
            assert(std::abs(static_cast<double>(output[i]) - value) <= 1.0);
    }
}

int main() {
    directReadsStayExact();
    for (int rate : {44100, 48000, 96000, 192000}) masterDelayAndMixShareOneTimeline(rate);
    for (double ppm : {-2000.0, -500.0, 500.0, 2000.0}) independentClocksConverge(ppm);
    interruptionsRecoverAndResetClearsHistory();
    fullScaleCannotWrap();
    std::cout << "Input clock sync checks passed\n";
}
