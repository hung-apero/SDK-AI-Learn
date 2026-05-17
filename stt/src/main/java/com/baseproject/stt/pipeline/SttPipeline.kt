package com.baseproject.stt.pipeline

import com.baseproject.stt.core.AudioSource
import com.baseproject.stt.core.SttConfig
import com.baseproject.stt.core.SttEvent
import com.baseproject.stt.core.SttResult
import kotlinx.coroutines.flow.Flow

/**
 * Orchestrates: preprocessors → provider → postprocessors.
 *
 * Hides the provider behind a single surface so app code doesn't change
 * when you swap or add providers.
 */
interface SttPipeline<out R : SttResult> {
    suspend fun run(audio: AudioSource, config: SttConfig): R
    fun runStream(audio: AudioSource, config: SttConfig): Flow<SttEvent<R>>
}
