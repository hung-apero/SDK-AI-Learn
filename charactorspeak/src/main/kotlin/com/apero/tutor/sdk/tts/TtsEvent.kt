package com.apero.tutor.sdk.tts

/**
 * Normalized TTS events emitted by every [TtsProvider] implementation.
 *
 * Different TTS engines produce different timing data:
 *   - Android TTS:  per-word callbacks with audio frame position
 *   - Azure TTS:    native viseme events (id + offset)
 *   - ElevenLabs:   per-character alignment timestamps
 *
 * They all collapse into [Range] so the [com.apero.tutor.sdk.TutorCharacter] orchestrator
 * can drive lip sync + subtitle highlight from one stream.
 */
sealed class TtsEvent {

    /** Fired when audio playback for this utterance begins. */
    data class Started(val uttId: String) : TtsEvent()

    /**
     * Fired when the engine has progressed to a new range in the text.
     *
     * @param start  inclusive character index in the original text
     * @param end    exclusive character index in the original text
     * @param frameMs elapsed milliseconds from utterance start to this range
     */
    data class Range(
        val uttId: String,
        val start: Int,
        val end: Int,
        val frameMs: Long
    ) : TtsEvent()

    /** Fired when audio playback for this utterance completes. */
    data class Done(val uttId: String) : TtsEvent()

    /** Fired when the engine fails for this utterance. */
    data class Error(val uttId: String, val message: String) : TtsEvent()
}
