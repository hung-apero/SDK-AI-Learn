package com.baseproject.aispeech.pronunciation

import java.util.Locale

abstract class PronunciationAssessor {

    abstract fun assess(
        referenceText: String,
        spokenText: String,
        locale: Locale = Locale.US,
    ): PronunciationResult

    protected fun normalizeText(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
}
