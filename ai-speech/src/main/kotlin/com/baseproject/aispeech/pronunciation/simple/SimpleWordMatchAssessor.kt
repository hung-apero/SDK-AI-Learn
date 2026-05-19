package com.baseproject.aispeech.pronunciation.simple

import com.baseproject.aispeech.pronunciation.PronunciationAssessor
import com.baseproject.aispeech.pronunciation.PronunciationLevel
import com.baseproject.aispeech.pronunciation.PronunciationResult
import com.baseproject.aispeech.pronunciation.WordDetail
import java.util.Locale

class SimpleWordMatchAssessor : PronunciationAssessor() {

    override fun assess(
        referenceText: String,
        spokenText: String,
        locale: Locale,
    ): PronunciationResult {
        val referenceWords = normalizeText(referenceText)
        val spokenWords = normalizeText(spokenText).toSet()

        if (referenceWords.isEmpty()) {
            return PronunciationResult(
                score = 0f,
                level = PronunciationLevel.TRY_AGAIN,
                matchedWords = emptyList(),
                unmatchedWords = emptyList(),
                wordDetails = emptyList(),
            )
        }

        val matched = mutableListOf<String>()
        val unmatched = mutableListOf<String>()
        val details = referenceWords.map { word ->
            val isMatched = word in spokenWords
            if (isMatched) matched.add(word) else unmatched.add(word)
            WordDetail(word = word, isMatched = isMatched)
        }

        val score = (matched.size.toFloat() / referenceWords.size * 100f)
            .coerceIn(0f, 100f)

        return PronunciationResult(
            score = score,
            level = PronunciationLevel.fromScore(score),
            matchedWords = matched,
            unmatchedWords = unmatched,
            wordDetails = details,
        )
    }
}
