package com.baseproject.aispeech.stt

sealed interface SttEvent {
    data class Result(val result: SttResult) : SttEvent
    data class PartialResult(val result: SttResult) : SttEvent
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
