package com.baseproject.stt.core

import kotlinx.coroutines.flow.Flow

/**
 * Provider-agnostic STT contract.
 *
 * @param R Concrete result subtype this provider returns. Generic so callers
 *          that talk to a specific provider get strong typing for free.
 */
interface SttProvider<out R : SttResult> {

    /** Stable identifier — used in registry lookup and analytics. */
    val name: String

    /** Reject unsupported inputs early. */
    fun supports(source: AudioSource): Boolean

    /** One-shot recognition. Suspends until the final result is ready. */
    suspend fun recognize(audio: AudioSource, config: SttConfig): R
}

/**
 * Optional capability — providers that can emit partial/intermediate results.
 */
interface StreamingSttProvider<out R : SttResult> : SttProvider<R> {
    fun recognizeStream(audio: AudioSource, config: SttConfig): Flow<SttEvent<R>>
}
