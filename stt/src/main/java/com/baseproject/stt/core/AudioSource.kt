package com.baseproject.stt.core

import kotlinx.coroutines.flow.Flow

/**
 * Abstract input to an [SttProvider]. Providers declare which variants they handle
 * via [SttProvider.supports].
 *
 *  - [PcmStream]   — caller supplies a [Flow] of raw PCM frames (typical for streaming).
 *  - [File]        — finished audio file on disk.
 *  - [Bytes]       — finished audio buffer in memory.
 *  - [SystemMicrophone] — provider opens the OS microphone itself (Android SpeechRecognizer).
 */
sealed interface AudioSource {

    data class PcmStream(
        val frames: Flow<ByteArray>,
        val format: AudioFormat,
    ) : AudioSource

    data class File(val path: String, val mime: String) : AudioSource

    data class Bytes(val data: ByteArray, val mime: String) : AudioSource {
        override fun equals(other: Any?): Boolean =
            other is Bytes && mime == other.mime && data.contentEquals(other.data)

        override fun hashCode(): Int = 31 * data.contentHashCode() + mime.hashCode()
    }

    data object SystemMicrophone : AudioSource
}
