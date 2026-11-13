package com.audiopro.djmrec.streaming

/** PCM16 sample clock avoids timestamp jitter when several USB buffers arrive together. */
internal class PcmFrameClock(private val sampleRate: Int, channelCount: Int = 2) {
    private val bytesPerFrame = channelCount * 2

    init { require(sampleRate > 0 && channelCount in 1..2) }
    private var origin: Long? = null
    private var frames = 0L

    fun next(bytes: Int, nowMicros: Long): Long {
        require(bytes > 0 && bytes % bytesPerFrame == 0)
        val duration = bytes.toLong() / bytesPerFrame * 1_000_000 / sampleRate
        if (origin == null) origin = nowMicros - duration
        var time = origin!! + frames * 1_000_000 / sampleRate
        // Preserve a real capture gap rather than slowly playing queued audio to catch up.
        if (nowMicros - time > 250_000) {
            origin = nowMicros - duration
            frames = 0
            time = origin!!
        }
        frames += bytes / bytesPerFrame
        return time
    }
}
