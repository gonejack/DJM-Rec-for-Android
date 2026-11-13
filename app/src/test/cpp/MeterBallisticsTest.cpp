#include "MeterBallistics.h"

#include <cassert>
#include <cmath>
#include <iostream>

using djmrec::ChannelMeter;
using djmrec::meterRmsAlpha;

int main() {
    for (int sampleRate : {44100, 48000, 96000}) {
        ChannelMeter mainInput, otherInput;
        // Different USB block sizes must produce the same response after the same duration.
        for (int block = 0; block < 300; ++block) {
            const int frames = sampleRate / 100;
            mainInput.push(0.5f, 0.25, meterRmsAlpha(frames, sampleRate));
            for (int smaller = 0; smaller < 10; ++smaller) {
                const int chunk = smaller == 9 ? frames - 9 * (frames / 10) : frames / 10;
                otherInput.push(0.5f, 0.25, meterRmsAlpha(chunk, sampleRate));
            }
        }
        assert(std::fabs(mainInput.rmsDb() - otherInput.rmsDb()) < 0.0001f);
        assert(std::fabs(mainInput.takePeakDb() + 6.0206f) < 0.0001f);
        assert(mainInput.takePeakDb() == djmrec::kMeterFloorDb);
        assert(!mainInput.takeClipping());
        mainInput.push(1.0f, 1.0, meterRmsAlpha(1, sampleRate));
        mainInput.push(0.0f, 0.0, meterRmsAlpha(sampleRate / 10, sampleRate));
        assert(mainInput.takePeakDb() == 0.0f); // transient survives until the UI polls
        assert(mainInput.takeClipping());
        assert(!mainInput.takeClipping());
        mainInput.reset();
        assert(mainInput.rmsDb() == djmrec::kMeterFloorDb);
    }
    ChannelMeter first, second;
    for (int i = 0; i < 300; ++i) first.push(0.25f, 0.0625, meterRmsAlpha(441, 44100));
    for (int i = 0; i < 441; ++i) second.push(0.25f, 0.0625, meterRmsAlpha(300, 44100));
    assert(std::fabs(first.rmsDb() - second.rmsDb()) < 0.0001f);
    assert(second.takePeakDb() < -12.0f); // inputs retain independent peaks
    const float beforeSilence = first.rmsDb();
    first.push(0.0f, 0.0, meterRmsAlpha(44100 * 3 / 10, 44100));
    assert(std::fabs((beforeSilence - first.rmsDb()) - 4.342945f) < 0.0001f);
    assert(std::fabs(second.rmsDb() - beforeSilence) < 0.0001f);
    std::cout << "Meter ballistics checks passed: callback sizes, rates, RMS release, peak/clip hold, reset, isolation\n";
}
