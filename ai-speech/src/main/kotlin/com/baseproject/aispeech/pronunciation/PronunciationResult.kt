package com.baseproject.aispeech.pronunciation

data class PronunciationResult(
    val score: Float,
    val level: PronunciationLevel,
    val matchedWords: List<String>,
    val unmatchedWords: List<String>,
    val wordDetails: List<WordDetail>,
)

data class WordDetail(
    val word: String,
    val isMatched: Boolean,
)

enum class PronunciationLevel(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    TRY_AGAIN("Try Again");

    companion object {
        fun fromScore(score: Float): PronunciationLevel = when {
            score >= 90f -> EXCELLENT
            score >= 70f -> GOOD
            score >= 50f -> FAIR
            else -> TRY_AGAIN
        }
    }
}
