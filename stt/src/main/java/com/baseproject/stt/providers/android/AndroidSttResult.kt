package com.baseproject.stt.providers.android

import com.baseproject.stt.core.SttResult

/**
 * Result subtype for [AndroidSpeechRecognizerProvider]. Adds the n-best list and
 * raw confidence array exposed by Android's [android.speech.SpeechRecognizer].
 */
data class AndroidSttResult(
    override val transcript: String,
    override val confidence: Double?,
    override val languageCode: String,
    override val durationMs: Long,
    val nBest: List<String>,
    val confidenceScores: FloatArray?,
) : SttResult() {
    override val provider: String = "android"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AndroidSttResult) return false
        return transcript == other.transcript &&
            confidence == other.confidence &&
            languageCode == other.languageCode &&
            durationMs == other.durationMs &&
            nBest == other.nBest &&
            (confidenceScores?.contentEquals(other.confidenceScores) ?: (other.confidenceScores == null))
    }

    override fun hashCode(): Int {
        var result = transcript.hashCode()
        result = 31 * result + (confidence?.hashCode() ?: 0)
        result = 31 * result + languageCode.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + nBest.hashCode()
        result = 31 * result + (confidenceScores?.contentHashCode() ?: 0)
        return result
    }
}
