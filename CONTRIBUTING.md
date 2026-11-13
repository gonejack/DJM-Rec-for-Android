# Contributing to DJM Rec for Android

## Setup

```bash
git clone https://github.com/P2GR/DJM-Rec-for-Android.git
cd DJM-Rec-for-Android
```

JDK 17, Android SDK, NDK 26.1, CMake 3.22.1 required. Open in Android Studio or build via CLI:

```bash
./gradlew assembleDebug
```

## Before submitting

- Lint must pass: `./gradlew lintDebug`
- Tests must pass: `./gradlew testDebugUnitTest`
- Keep ProGuard rules up to date if adding JNI or reflection-based code

## Code style

- Kotlin: official style (configured in `gradle.properties`)
- C++: C++17, `.clang-format` is project-standard
- Commit messages: [Conventional Commits](https://www.conventionalcommits.org/)

## Mixer diagnostics

Use **Support & diagnostics > Export diagnostic report** to collect phone details,
USB descriptors, selected format/rate/channel pair, native capture health and recent
local logs. Reports stay on the device until explicitly shared. Audio recordings are
excluded and sensitive log text is redacted.

Raw USB channel activity uses approximate one-second peak windows, before gain and
stereo extraction. Check window age for stale data after a stall. Active means at least
-60 dBFS; activity alone does not prove correct master routing. Test stereo separation
and saved timing on physical hardware when adding a new mixer profile.

Standalone channel-measurement regression:

```sh
c++ -std=c++17 -Iapp/src/main/cpp app/src/test/cpp/ChannelActivityTest.cpp -o channel-test
./channel-test
```

All build variants work without cloud service configuration. The `local` variant uses
the debug signing key and release optimizations for personal installation:

```bash
./gradlew assembleLocal
```

YouTube streaming uses a server address and stream key copied from YouTube Studio.

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
