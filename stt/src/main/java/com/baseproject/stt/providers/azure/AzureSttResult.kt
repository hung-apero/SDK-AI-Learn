package com.baseproject.stt.providers.azure

import com.baseproject.stt.core.SttResult
import com.baseproject.stt.core.Word

/**
 * Result subtype for [AzureSpeechProvider]. Adds word-level timestamps,
 * n-best alternatives, and the raw JSON for advanced consumers.
 */
data class AzureSttResult(
    override val transcript: String,
    override val confidence: Double?,
    override val languageCode: String,
    override val durationMs: Long,
    val words: List<Word>,
    val nBest: List<NBestEntry>,
    val detectedLanguage: String?,
    val rawJson: String,
) : SttResult() {
    override val provider: String = "azure"

    data class NBestEntry(val text: String, val confidence: Double)
}
