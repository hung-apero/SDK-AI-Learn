package com.baseproject.aispeech.tts

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over a text-to-speech engine.
 *
 * Implement this interface to plug in any TTS provider (Android system TTS,
 * Azure Cognitive Services, ElevenLabs, etc.). The SDK consumes only [events]
 * and the [speak]/[stop]/[shutdown] lifecycle — all timing & playback details
 * are the provider's responsibility.
 *
 * Implementations must:
 *   - Be safe to call [speak] multiple times (queue or drop policy is up to the impl).
 *   - Always emit [TtsEvent.Started] before any [TtsEvent.Range]/[TtsEvent.Done].
 *   - Always emit terminal [TtsEvent.Done] or [TtsEvent.Error] per utterance id.
 *   - Treat [prepare] as idempotent (callable multiple times safely).
 */
interface TtsProvider {

    /** Hot stream of normalized events. Suitable for `collect` from coroutines. */
    val events: Flow<TtsEvent>

    /**
     * Speaking rate as a multiplier — `1.0` = normal, `0.5` = half speed,
     * `2.0` = double. Implementations should clamp to a sensible range
     * (typically `[0.5, 2.0]`).
     *
     * Setting this property applies the rate to the next utterance. Whether
     * an in-flight utterance picks up the new rate is implementation-defined
     * (Android TTS: yes; Azure / ElevenLabs: only the next utterance).
     */
    var speechRate: Float

    /** Prepare the engine (e.g. init Android TTS, fetch tokens). Idempotent. */
    suspend fun prepare(context: Context)

    /**
     * Queue [text] for speech.
     *
     * @param utteranceId unique id used to correlate emitted [TtsEvent]s.
     */
    fun speak(text: String, utteranceId: String)

    /** Cancel current playback and clear any pending queue. */
    fun stop()

    /** Release engine resources. Provider is unusable after this call. */
    fun shutdown()
}
