#include <jni.h>
#include <algorithm>
#include <cstring>
#include <string>
#include <vector>
#include <utility>
#include <android/log.h>
#include <libusb.h>

#include "../UsbAudioEngine.h"

using djmrec::ContainerFormat;
using djmrec::UsbAudioEngine;

namespace {
std::string jstringToStdString(JNIEnv* env, jstring jStr) {
    if (!jStr) return {};
    const char* chars = env->GetStringUTFChars(jStr, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(jStr, chars);
    return result;
}
} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_audiopro_djmrec_usb_UsbAudioDriver_restoreSystemAudio(
    JNIEnv* env, jobject, jint fd, jint controlInterface, jintArray preservedCaptureInterfaces) {
    const jsize count = preservedCaptureInterfaces ? env->GetArrayLength(preservedCaptureInterfaces) : 0;
    if (count > 3) return JNI_FALSE;
    std::vector<jint> preserved(static_cast<size_t>(count));
    if (count > 0) {
        env->GetIntArrayRegion(preservedCaptureInterfaces, 0, count, preserved.data());
        if (env->ExceptionCheck()) return JNI_FALSE;
        for (const auto interfaceNumber : preserved) {
            if (interfaceNumber < 1 || interfaceNumber > 3) return JNI_FALSE;
        }
    }
    libusb_context* context = nullptr;
    libusb_init_option option{};
    option.option = LIBUSB_OPTION_NO_DEVICE_DISCOVERY;
    int rc = libusb_init_context(&context, &option, 1);
    if (rc != LIBUSB_SUCCESS) return JNI_FALSE;
    libusb_device_handle* handle = nullptr;
    rc = libusb_wrap_sys_device(context, static_cast<intptr_t>(fd), &handle);
    if (rc == LIBUSB_SUCCESS) {
        libusb_device_descriptor descriptor{};
        rc = libusb_get_device_descriptor(libusb_get_device(handle), &descriptor);
        if (rc == LIBUSB_SUCCESS &&
            (descriptor.idVendor != 0x0BDA || descriptor.idProduct != 0x4F26 || controlInterface != 0)) {
            rc = LIBUSB_ERROR_NOT_SUPPORTED;
        }
        if (rc == LIBUSB_SUCCESS) {
            // Reparse every available stream so the system card includes playback formats.
            // During capture, usbfs keeps the selected inputs; the kernel skips those and
            // binds the outputs. Playback then survives even if Android kills our process.
            for (int interfaceNumber = 1; interfaceNumber <= 5; ++interfaceNumber) {
                if (std::find(preserved.begin(), preserved.end(), interfaceNumber) != preserved.end()) continue;
                const int active = libusb_kernel_driver_active(handle, interfaceNumber);
                if (active == 1) {
                    const int detached = libusb_detach_kernel_driver(handle, interfaceNumber);
                    if (detached != LIBUSB_SUCCESS && rc == LIBUSB_SUCCESS) rc = detached;
                } else if (active < 0 && rc == LIBUSB_SUCCESS) {
                    rc = active;
                }
            }
            const int active = libusb_kernel_driver_active(handle, controlInterface);
            if (active == 1) {
                const int detached = libusb_detach_kernel_driver(handle, controlInterface);
                if (detached != LIBUSB_SUCCESS && rc == LIBUSB_SUCCESS) rc = detached;
            } else if (active < 0 && rc == LIBUSB_SUCCESS) {
                rc = active;
            }
            // Always attempt to return ownership even if an earlier detach failed.
            const int attached = libusb_attach_kernel_driver(handle, controlInterface);
            if (rc == LIBUSB_SUCCESS) rc = attached;
        }
        __android_log_print(rc == LIBUSB_SUCCESS ? ANDROID_LOG_INFO : ANDROID_LOG_ERROR,
            "UsbAudioDriver", "CB5 system driver rebind: preserved capture interfaces=%zu result=%s",
            preserved.size(), libusb_error_name(rc));
        libusb_close(handle);
    }
    libusb_exit(context);
    return rc == LIBUSB_SUCCESS ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_setRecordingGainDb(
    JNIEnv*, jobject, jfloat gainDb) {
    UsbAudioEngine::instance().setRecordingGainDb(gainDb);
}


JNIEXPORT jint JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_open(
    JNIEnv* /*env*/, jobject /*thiz*/,
    jint audioManagerDeviceId, jint sampleRateHint, jint channelCount, jint bitDepth) {
    return UsbAudioEngine::instance().open(audioManagerDeviceId, sampleRateHint, channelCount, bitDepth);
}

JNIEXPORT jint JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_openUsbIso(
    JNIEnv* env, jobject /*thiz*/,
    jint fd, jint interfaceNumber, jint alternateSetting, jint endpointAddress, jint maxPacketSize,
    jint totalChannels, jint subframeSize, jint bitResolution, jint extractChannelOffset,
    jint clockControlInterfaceNumber, jint clockSourceId, jboolean clockSupportsFrequencySet,
    jint feedbackEndpointAddress, jint feedbackMaxPacketSize, jint vendorId, jint productId,
    jbyteArray rawDescriptors,
    jint sampleRateHint, jboolean multitrack) {
    djmrec::UsbIsoAudioSource::Config config;
    config.fd = fd;
    config.interfaceNumber = interfaceNumber;
    config.alternateSetting = alternateSetting;
    config.endpointAddress = endpointAddress;
    config.maxPacketSize = maxPacketSize;
    config.totalChannels = totalChannels;
    config.subframeSize = subframeSize;
    config.bitResolution = bitResolution;
    config.extractChannelOffset = extractChannelOffset;
    config.clockControlInterfaceNumber = clockControlInterfaceNumber;
    config.clockSourceId = clockSourceId;
    config.clockSupportsFrequencySet = clockSupportsFrequencySet == JNI_TRUE;
    config.requestedSampleRate = sampleRateHint;
    config.feedbackEndpointAddress = feedbackEndpointAddress;
    config.feedbackMaxPacketSize = feedbackMaxPacketSize;
    config.vendorId = vendorId;
    config.productId = productId;
    config.emitAllChannels = multitrack == JNI_TRUE;
    config.disableRouteFallback = multitrack == JNI_TRUE;
    if (rawDescriptors) {
        const jsize length = env->GetArrayLength(rawDescriptors);
        const auto* bytes = env->GetByteArrayElements(rawDescriptors, nullptr);
        if (bytes && length > 0) {
            const auto* unsignedBytes = reinterpret_cast<const uint8_t*>(bytes);
            config.rawDescriptors.assign(unsignedBytes, unsignedBytes + length);
        }
        if (bytes) {
            env->ReleaseByteArrayElements(rawDescriptors, const_cast<jbyte*>(bytes), JNI_ABORT);
        }
    }
    return UsbAudioEngine::instance().openUsbIso(config, sampleRateHint);
}

JNIEXPORT jint JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_openUsbIsoMulti(
    JNIEnv* env, jobject, jintArray packedConfigs, jbyteArray rawDescriptors, jint sampleRateHint) {
    constexpr int kFields = 16;
    if (!packedConfigs || !rawDescriptors) return -1;
    const jsize length = env->GetArrayLength(packedConfigs);
    if (length < kFields || length > kFields * 3 || length % kFields != 0) return -1;
    std::vector<jint> values(static_cast<size_t>(length));
    env->GetIntArrayRegion(packedConfigs, 0, length, values.data());
    if (env->ExceptionCheck()) return -1;
    const jsize byteCount = env->GetArrayLength(rawDescriptors);
    std::vector<uint8_t> descriptors(static_cast<size_t>(byteCount));
    if (byteCount > 0) env->GetByteArrayRegion(rawDescriptors, 0, byteCount,
        reinterpret_cast<jbyte*>(descriptors.data()));
    if (env->ExceptionCheck()) return -1;

    std::vector<djmrec::UsbIsoAudioSource::Config> configs;
    for (jsize offset = 0; offset < length; offset += kFields) {
        const jint* p = values.data() + offset;
        if (p[14] != 0x0BDA || p[15] != 0x4F26 || p[5] != 2) return -1;
        djmrec::UsbIsoAudioSource::Config config;
        config.fd = p[0];
        config.interfaceNumber = p[1];
        config.alternateSetting = p[2];
        config.endpointAddress = p[3];
        config.maxPacketSize = p[4];
        config.totalChannels = p[5];
        config.subframeSize = p[6];
        config.bitResolution = p[7];
        config.extractChannelOffset = p[8];
        config.clockControlInterfaceNumber = p[9];
        config.clockSourceId = p[10];
        config.clockSupportsFrequencySet = p[11] != 0;
        config.feedbackEndpointAddress = p[12];
        config.feedbackMaxPacketSize = p[13];
        config.vendorId = p[14];
        config.productId = p[15];
        config.requestedSampleRate = sampleRateHint;
        config.rawDescriptors = descriptors;
        configs.push_back(std::move(config));
    }
    for (size_t i = 0; i < configs.size(); ++i) {
        for (size_t j = i + 1; j < configs.size(); ++j) {
            if (configs[i].interfaceNumber == configs[j].interfaceNumber) return -1;
        }
    }
    return UsbAudioEngine::instance().openUsbIso(configs.front(), sampleRateHint,
        {configs.begin() + 1, configs.end()}, /*independentInputs=*/true);
}

JNIEXPORT jboolean JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_startRecording(
    JNIEnv* env, jobject /*thiz*/,
    jstring outputPath, jint format) {
    const std::string path = jstringToStdString(env, outputPath);
    const auto container = static_cast<ContainerFormat>(format);
    return UsbAudioEngine::instance().startRecording(path, container) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_startRecordingFd(
    JNIEnv* /*env*/, jobject /*thiz*/, jint fd, jint format) {
    return UsbAudioEngine::instance().startRecordingFd(
        fd, static_cast<ContainerFormat>(format)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_rollRecordingFd(
    JNIEnv* /*env*/, jobject /*thiz*/, jint fd, jint format) {
    return UsbAudioEngine::instance().rollRecordingFd(
        fd, static_cast<ContainerFormat>(format)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_checkpointRecording(JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jlong>(UsbAudioEngine::instance().checkpointRecording());
}

JNIEXPORT jint JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getRecordingErrorCode(JNIEnv* /*env*/, jobject /*thiz*/) {
    return UsbAudioEngine::instance().getRecordingErrorCode();
}

JNIEXPORT jboolean JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_isStreamOpen(JNIEnv* /*env*/, jobject /*thiz*/) {
    return UsbAudioEngine::instance().isStreamOpen() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_startLivePcm(JNIEnv* /*env*/, jobject /*thiz*/) {
    return UsbAudioEngine::instance().startLivePcm() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_stopLivePcm(JNIEnv* /*env*/, jobject /*thiz*/) {
    UsbAudioEngine::instance().stopLivePcm();
}

JNIEXPORT jint JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_readLivePcm16(
    JNIEnv* env, jobject /*thiz*/, jbyteArray destination) {
    if (!destination) return 0;
    const jsize capacity = env->GetArrayLength(destination);
    if (capacity <= 0) return 0;
    static thread_local std::vector<uint8_t> buffer;
    if (buffer.size() < static_cast<size_t>(capacity)) buffer.resize(capacity);
    const size_t read = UsbAudioEngine::instance().readLivePcm16(buffer.data(), capacity);
    if (read > 0) {
        env->SetByteArrayRegion(
            destination, 0, static_cast<jsize>(read), reinterpret_cast<const jbyte*>(buffer.data()));
    }
    return static_cast<jint>(read);
}

JNIEXPORT void JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_pauseRecording(JNIEnv* /*env*/, jobject /*thiz*/) {
    UsbAudioEngine::instance().pauseRecording();
}

JNIEXPORT void JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_resumeRecording(JNIEnv* /*env*/, jobject /*thiz*/) {
    UsbAudioEngine::instance().resumeRecording();
}

JNIEXPORT jlong JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_stopRecording(JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jlong>(UsbAudioEngine::instance().stopRecording());
}

JNIEXPORT void JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_close(JNIEnv* /*env*/, jobject /*thiz*/) {
    UsbAudioEngine::instance().closeEngine();
}

JNIEXPORT jfloatArray JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getLevels(JNIEnv* env, jobject /*thiz*/) {
    float levels[4];
    UsbAudioEngine::instance().getLevels(levels);

    jfloatArray result = env->NewFloatArray(4);
    env->SetFloatArrayRegion(result, 0, 4, levels);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_isClipping(JNIEnv* /*env*/, jobject /*thiz*/) {
    return UsbAudioEngine::instance().isClipping() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getElapsedMillis(JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jlong>(UsbAudioEngine::instance().getElapsedMillis());
}

JNIEXPORT jint JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getXRunCount(JNIEnv* /*env*/, jobject /*thiz*/) {
    return UsbAudioEngine::instance().getXRunCount();
}

JNIEXPORT jlongArray JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getSecondaryInputBytes(JNIEnv* env, jobject) {
    const auto counts = UsbAudioEngine::instance().getSecondaryInputBytes();
    jlongArray result = env->NewLongArray(static_cast<jsize>(counts.size()));
    std::vector<jlong> values(counts.begin(), counts.end());
    if (!values.empty()) env->SetLongArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
    return result;
}

JNIEXPORT jlongArray JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getUsbIsoTransferStats(JNIEnv* env, jobject /*thiz*/) {
    uint64_t stats[7]{};
    UsbAudioEngine::instance().getUsbIsoTransferStats(stats);
    jlong values[7]{};
    for (int index = 0; index < 7; ++index) {
        values[index] = static_cast<jlong>(stats[index]);
    }
    jlongArray result = env->NewLongArray(7);
    env->SetLongArrayRegion(result, 0, 7, values);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getDiagnosticSummary(JNIEnv* env, jobject /*thiz*/) {
    const std::string summary = UsbAudioEngine::instance().getDiagnosticSummary();
    return env->NewStringUTF(summary.c_str());
}

JNIEXPORT jfloatArray JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getWaveformBins(JNIEnv* env, jobject /*thiz*/) {
    constexpr int kFloats = UsbAudioEngine::kWaveformBinCount * 4 + 2;
    float bins[kFloats];
    UsbAudioEngine::instance().getWaveformBins(bins);

    jfloatArray result = env->NewFloatArray(kFloats);
    env->SetFloatArrayRegion(result, 0, kFloats, bins);
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getMixChannelWaveformBins(
    JNIEnv* env, jobject /*thiz*/, jint channel) {
    constexpr int kFloats = UsbAudioEngine::kWaveformBinCount * 4 + 2;
    float bins[kFloats];
    UsbAudioEngine::instance().getMixChannelWaveformBins(channel, bins);
    jfloatArray result = env->NewFloatArray(kFloats);
    env->SetFloatArrayRegion(result, 0, kFloats, bins);
    return result;
}

JNIEXPORT void JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_setWaveformEnabled(
    JNIEnv* /*env*/, jobject /*thiz*/, jboolean enabled) {
    UsbAudioEngine::instance().setWaveformEnabled(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_setTrimLeadingSilence(
    JNIEnv* /*env*/, jobject /*thiz*/, jboolean enabled) {
    UsbAudioEngine::instance().setTrimLeadingSilence(enabled == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_isAwaitingAudio(JNIEnv* /*env*/, jobject /*thiz*/) {
    return UsbAudioEngine::instance().isAwaitingAudio() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_setLimiterEnabled(
    JNIEnv* /*env*/, jobject /*thiz*/, jboolean enabled) {
    UsbAudioEngine::instance().setLimiterEnabled(enabled == JNI_TRUE);
}

JNIEXPORT jfloat JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_takeLimiterReductionDb(JNIEnv* /*env*/, jobject /*thiz*/) {
    return UsbAudioEngine::instance().takeLimiterReductionDb();
}

JNIEXPORT void JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_setPreRecordEnabled(
    JNIEnv* /*env*/, jobject /*thiz*/, jboolean enabled) {
    UsbAudioEngine::instance().setPreRecordEnabled(enabled == JNI_TRUE);
}

JNIEXPORT jlong JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getTrailingSilenceMillis(JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jlong>(UsbAudioEngine::instance().getTrailingSilenceMillis());
}

JNIEXPORT jboolean JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_prepareCompanionFd(
    JNIEnv* /*env*/, jobject /*thiz*/, jint fd, jint format) {
    return UsbAudioEngine::instance().prepareCompanionFd(fd, static_cast<ContainerFormat>(format))
        ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_clearPendingCompanion(JNIEnv* /*env*/, jobject /*thiz*/) {
    UsbAudioEngine::instance().clearPendingCompanion();
}

JNIEXPORT jint JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getCompanionErrorCode(JNIEnv* /*env*/, jobject /*thiz*/) {
    return UsbAudioEngine::instance().getCompanionErrorCode();
}

// --- Multitrack ------------------------------------------------------------------------

JNIEXPORT jint JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getTrackChannelCount(JNIEnv* /*env*/, jobject /*thiz*/) {
    return UsbAudioEngine::instance().getTrackChannelCount();
}

JNIEXPORT jint JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getMixOutputChannelCount(JNIEnv* /*env*/, jobject /*thiz*/) {
    return UsbAudioEngine::instance().getMixOutputChannelCount();
}

JNIEXPORT jboolean JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_prepareTrackFd(
    JNIEnv* /*env*/, jobject /*thiz*/, jint track, jint fd, jint format) {
    return UsbAudioEngine::instance().prepareTrackFd(track, fd, static_cast<ContainerFormat>(format))
        ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_clearPendingTracks(JNIEnv* /*env*/, jobject /*thiz*/) {
    UsbAudioEngine::instance().clearPendingTracks();
}

JNIEXPORT jboolean JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_prepareTrackRollFd(
    JNIEnv* /*env*/, jobject /*thiz*/, jint track, jint fd, jint format) {
    return UsbAudioEngine::instance().prepareTrackRollFd(track, fd, static_cast<ContainerFormat>(format))
        ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getTrackErrorMask(JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jint>(UsbAudioEngine::instance().getTrackErrorMask());
}

JNIEXPORT void JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_setTrackGainDb(
    JNIEnv* /*env*/, jobject /*thiz*/, jint track, jfloat gainDb) {
    UsbAudioEngine::instance().setTrackGainDb(track, gainDb);
}

JNIEXPORT void JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_setMixSources(
    JNIEnv* /*env*/, jobject /*thiz*/, jint leftChannel, jint rightChannel) {
    UsbAudioEngine::instance().setMixSources(leftChannel, rightChannel);
}

JNIEXPORT void JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_setTrackWaveformsEnabled(
    JNIEnv* /*env*/, jobject /*thiz*/, jboolean enabled) {
    UsbAudioEngine::instance().setTrackWaveformsEnabled(enabled == JNI_TRUE);
}

JNIEXPORT jfloatArray JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getTrackLevels(JNIEnv* env, jobject /*thiz*/) {
    constexpr int kMaxFloats = djmrec::TrackBus::kMaxChannels * djmrec::TrackBus::kLevelStride;
    float levels[kMaxFloats];
    const int channels = UsbAudioEngine::instance().getTrackLevels(levels, djmrec::TrackBus::kMaxChannels);
    if (channels < 0) return nullptr; // busy: keep the previous reading
    const auto count = static_cast<jsize>(channels * djmrec::TrackBus::kLevelStride);
    jfloatArray result = env->NewFloatArray(count);
    if (count > 0) env->SetFloatArrayRegion(result, 0, count, levels);
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getTrackWaveformBins(
    JNIEnv* env, jobject /*thiz*/, jint track) {
    constexpr int kFloats = UsbAudioEngine::kWaveformBinCount * 4 + 2;
    float bins[kFloats];
    UsbAudioEngine::instance().getTrackWaveformBins(track, bins);
    jfloatArray result = env->NewFloatArray(kFloats);
    env->SetFloatArrayRegion(result, 0, kFloats, bins);
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getTrackChannelWaveformBins(
    JNIEnv* env, jobject /*thiz*/, jint channel) {
    constexpr int kFloats = UsbAudioEngine::kWaveformBinCount * 4 + 2;
    float bins[kFloats];
    UsbAudioEngine::instance().getTrackChannelWaveformBins(channel, bins);
    jfloatArray result = env->NewFloatArray(kFloats);
    env->SetFloatArrayRegion(result, 0, kFloats, bins);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getMasterChannelOffset(JNIEnv* /*env*/, jobject /*thiz*/) {
    return UsbAudioEngine::instance().getMasterChannelOffset();
}

JNIEXPORT jint JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_setPioneerTrackSource(
    JNIEnv* /*env*/, jobject /*thiz*/, jint output, jint source) {
    return UsbAudioEngine::instance().setPioneerTrackSource(output, source);
}

JNIEXPORT jint JNICALL
Java_com_audiopro_djmrec_audio_AudioEngine_getPioneerTrackSource(
    JNIEnv* /*env*/, jobject /*thiz*/, jint output) {
    return UsbAudioEngine::instance().getPioneerTrackSource(output);
}
} // extern "C"
