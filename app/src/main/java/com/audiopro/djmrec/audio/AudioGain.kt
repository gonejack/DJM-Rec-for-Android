package com.audiopro.djmrec.audio

import java.util.Locale
import kotlin.math.roundToInt

/** Shared gain range, half-decibel steps and labels for Track and Mix. */
object AudioGain {
    const val MIN_DB = 0f
    const val MAX_DB = 12f
    const val SLIDER_STEPS = 23
    const val PREFERENCE_KEY = "recording_gain_db"

    /** Gain remains live during recording, pause and streaming. */
    fun canAdjust(state: RecordingState, saving: Boolean): Boolean = !saving && state !is RecordingState.Preparing

    fun snap(db: Float): Float = if (db.isFinite()) (db.coerceIn(MIN_DB, MAX_DB) * 2f).roundToInt() / 2f else MIN_DB

    /** Accepts the old integer preference as well as the current float preference. */
    fun fromStored(value: Any?, defaultDb: Float = 12f): Float = snap((value as? Number)?.toFloat() ?: defaultDb)

    fun format(db: Float): String = String.format(Locale.US, "%s%.1f dB", if (db > 0f) "+" else "", db)
}
