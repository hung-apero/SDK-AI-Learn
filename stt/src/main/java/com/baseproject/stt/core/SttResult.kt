package com.baseproject.stt.core

/**
 * Abstract base for recognition results. Every provider returns its own subclass
 * enriched with provider-specific fields (e.g. Azure word timestamps, Android nBest).
 *
 * Callers that only need the transcript can stay at this type; callers that need
 * provider-specific data should downcast or use [com.baseproject.stt.registry.SttRegistry.getTyped].
 */
abstract class SttResult {
    abstract val transcript: String
    abstract val confidence: Double?
    abstract val languageCode: String
    abstract val durationMs: Long
    abstract val provider: String
}

/** Word with timestamps — reusable across providers that expose word-level info. */
data class Word(
    val text: String,
    val startMs: Int,
    val endMs: Int,
    val confidence: Double?,
)

/** Coarser-grained segment — reusable across providers that expose phrase-level info. */
data class Segment(
    val text: String,
    val startMs: Int,
    val endMs: Int,
)
