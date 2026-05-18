package com.baseproject.sample.stt

import com.baseproject.aispeech.stt.android.AndroidSttResult
import com.baseproject.aispeech.stt.gemini.GeminiSttResult

fun AndroidSttResult.toDisplayText(): String = buildString {
    appendLine("Transcript: $text")
    languageCode?.let { append("Language: $it") }
}

fun GeminiSttResult.toDisplayText(): String = buildString {
    appendLine("Transcript: $text")
    languageCode?.let { appendLine("Language: $it") }
    appendLine()
    appendLine("Scores — pron: ${overall.pronunciation}, fluency: ${overall.fluency}, grammar: ${overall.grammar} (${overall.cefrEstimate})")
    if (mistakes.isNotEmpty()) {
        appendLine()
        appendLine("Mistakes:")
        mistakes.forEach { m ->
            appendLine("  ${m.original} → ${m.correction}")
            appendLine("    ${m.explainNative}")
        }
    }
    coachTip?.let { appendLine().also { appendLine("Tip: $it") } }
    nextDrill?.let { append("Next drill: $it") }
}
