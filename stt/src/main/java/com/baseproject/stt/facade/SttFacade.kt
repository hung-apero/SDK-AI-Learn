package com.baseproject.stt.facade

import com.baseproject.stt.core.AudioSource
import com.baseproject.stt.core.SttConfig
import com.baseproject.stt.core.SttEvent
import com.baseproject.stt.core.SttResult
import com.baseproject.stt.pipeline.SttPipeline
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point exposed to app code (ViewModels, use cases).
 *
 * Wraps a [SttPipeline] so callers never touch providers directly. Swap providers
 * (e.g. add Google Cloud STT) by re-wiring the pipeline — no call-site changes.
 */
class SttFacade(
    private val pipeline: SttPipeline<SttResult>,
) {
    suspend fun transcribe(
        audio: AudioSource,
        config: SttConfig = SttConfig(),
    ): SttResult = pipeline.run(audio, config)

    fun transcribeStream(
        audio: AudioSource,
        config: SttConfig = SttConfig(),
    ): Flow<SttEvent<SttResult>> = pipeline.runStream(audio, config)
}
