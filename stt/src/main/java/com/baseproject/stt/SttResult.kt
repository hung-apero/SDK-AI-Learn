package com.baseproject.stt

sealed interface SttEvent {
    data class Result(
        val text: String,
        val alternatives: List<String> = emptyList(),
        val confidence: Float = 0f,
        val isFinal: Boolean = true,
    ) : SttEvent

    data class PartialResult(val text: String) : SttEvent
    data object ReadyForSpeech : SttEvent
    data object BeginningOfSpeech : SttEvent
    data object EndOfSpeech : SttEvent
    data class Error(val code: SttErrorCode, val message: String) : SttEvent
}

enum class SttErrorCode {
    NETWORK,
    AUDIO,
    SERVER,
    NO_MATCH,
    RECOGNIZER_BUSY,
    INSUFFICIENT_PERMISSIONS,
    UNKNOWN,
}
