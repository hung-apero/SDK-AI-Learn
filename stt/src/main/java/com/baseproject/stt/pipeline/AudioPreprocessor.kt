package com.baseproject.stt.pipeline

import com.baseproject.stt.core.AudioSource
import com.baseproject.stt.core.SttConfig

/**
 * Audio-side cross-cutting concerns: resampling, VAD trimming, gain, noise gate.
 *
 * Implementations transform an [AudioSource] before it reaches the provider.
 */
interface AudioPreprocessor {
    suspend fun process(audio: AudioSource, config: SttConfig): AudioSource
}

/** No-op default. Use as the identity in builders/tests. */
class NoopPreprocessor : AudioPreprocessor {
    override suspend fun process(audio: AudioSource, config: SttConfig): AudioSource = audio
}
