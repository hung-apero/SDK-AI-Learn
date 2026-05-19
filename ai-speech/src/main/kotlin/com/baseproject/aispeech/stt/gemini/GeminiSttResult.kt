package com.baseproject.aispeech.stt.gemini

import com.baseproject.aispeech.stt.SttResult
import com.baseproject.aispeech.stt.gemini.internal.CoachPayload

class GeminiSttResult(
    text: String,
    languageCode: String?,
    val overall: OverallScore,
    val words: List<WordFeedback>,
    val mistakes: List<Mistake>,
    val nativeRewrites: NativeRewrites?,
    val coachTip: String?,
    val nextDrill: String?,
) : SttResult(
    text = text,
    isFinal = true,
    languageCode = languageCode,
) {
    internal companion object {
        fun fromPayload(p: CoachPayload) = GeminiSttResult(
            text = p.transcript,
            languageCode = p.detectedLanguage,
            overall = OverallScore(
                pronunciation = p.overall.pronunciation,
                fluency = p.overall.fluency,
                grammar = p.overall.grammar,
                cefrEstimate = p.overall.cefrEstimate,
            ),
            words = p.words.map {
                WordFeedback(
                    word = it.word,
                    score = it.score,
                    level = WordLevel.parse(it.level),
                    issue = it.issue,
                )
            },
            mistakes = p.mistakes.map {
                Mistake(
                    type = MistakeType.parse(it.type),
                    original = it.original,
                    correction = it.correction,
                    explainNative = it.explainNative,
                )
            },
            nativeRewrites = p.nativeRewrites?.let {
                NativeRewrites(casual = it.casual, formal = it.formal)
            },
            coachTip = p.coachTip,
            nextDrill = p.nextDrill,
        )
    }
}

data class OverallScore(
    val pronunciation: Int,
    val fluency: Int,
    val grammar: Int,
    val cefrEstimate: String,
)

data class WordFeedback(
    val word: String,
    val score: Int,
    val level: WordLevel,
    val issue: String?,
)

enum class WordLevel {
    GOOD, OK, BAD, UNKNOWN;

    companion object {
        fun parse(raw: String): WordLevel = when (raw.lowercase()) {
            "good" -> GOOD
            "ok" -> OK
            "bad" -> BAD
            else -> UNKNOWN
        }
    }
}

data class Mistake(
    val type: MistakeType,
    val original: String,
    val correction: String,
    val explainNative: String,
)

enum class MistakeType {
    GRAMMAR, VOCAB, PRONUNCIATION, OTHER, UNKNOWN;

    companion object {
        fun parse(raw: String): MistakeType = when (raw.lowercase()) {
            "grammar" -> GRAMMAR
            "vocab" -> VOCAB
            "pronunciation" -> PRONUNCIATION
            "other" -> OTHER
            else -> UNKNOWN
        }
    }
}

data class NativeRewrites(
    val casual: String?,
    val formal: String?,
)
