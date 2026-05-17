package com.baseproject.stt.core

/**
 * Events emitted by a [StreamingSttProvider].
 *
 * Typical lifecycle: many [Partial] → [EndOfSpeech] → one [Final] → flow completes.
 * On error: [Error] then flow completes.
 */
sealed class SttEvent<out R : SttResult> {
    data class Partial(val text: String) : SttEvent<Nothing>()
    data class Final<R : SttResult>(val result: R) : SttEvent<R>()
    data object EndOfSpeech : SttEvent<Nothing>()
    data class Error(val error: SttError) : SttEvent<Nothing>()
}
