package com.baseproject.stt.core

/**
 * Describes the raw audio format of a [AudioSource.PcmStream] or [AudioSource.Bytes].
 *
 * Providers may resample or refuse based on this — check [SttProvider.supports] before sending.
 */
data class AudioFormat(
    val sampleRateHz: Int = 16_000,
    val channels: Int = 1,
    val encoding: Encoding = Encoding.PCM_16BIT,
) {
    enum class Encoding { PCM_16BIT, PCM_FLOAT, OPUS }
}
