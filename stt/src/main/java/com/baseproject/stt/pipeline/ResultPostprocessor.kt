package com.baseproject.stt.pipeline

import com.baseproject.stt.core.SttConfig
import com.baseproject.stt.core.SttResult

/**
 * Result-side cross-cutting concerns: normalization, profanity masking,
 * filler-word removal, capitalization, etc.
 *
 * Generic over [R] so the concrete result subtype is preserved through the pipeline.
 */
interface ResultPostprocessor {
    suspend fun <R : SttResult> process(result: R, config: SttConfig): R
}

/** No-op default. */
class NoopPostprocessor : ResultPostprocessor {
    override suspend fun <R : SttResult> process(result: R, config: SttConfig): R = result
}
