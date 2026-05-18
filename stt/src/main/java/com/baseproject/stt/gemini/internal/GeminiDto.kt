package com.baseproject.stt.gemini.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GeminiRequest(
    @SerialName("system_instruction") val systemInstruction: SystemInstruction,
    val contents: List<Content>,
    @SerialName("generation_config") val generationConfig: GenerationConfig,
)

@Serializable
internal data class SystemInstruction(val parts: List<TextPart>)

@Serializable
internal data class TextPart(val text: String)

@Serializable
internal data class Content(
    val role: String = "user",
    val parts: List<Part>,
)

@Serializable
internal data class Part(
    val text: String? = null,
    @SerialName("inline_data") val inlineData: InlineData? = null,
)

@Serializable
internal data class InlineData(
    @SerialName("mime_type") val mimeType: String,
    val data: String,
)

@Serializable
internal data class GenerationConfig(
    @SerialName("response_mime_type") val responseMimeType: String = "application/json",
)

@Serializable
internal data class GeminiResponse(
    val candidates: List<Candidate> = emptyList(),
) {
    @Serializable
    data class Candidate(val content: Content)
}

@Serializable
internal data class CoachPayload(
    val transcript: String,
    @SerialName("detected_language") val detectedLanguage: String? = null,
    val overall: OverallDto,
    val words: List<WordDto> = emptyList(),
    val mistakes: List<MistakeDto> = emptyList(),
    @SerialName("native_rewrites") val nativeRewrites: NativeRewritesDto? = null,
    @SerialName("coach_tip_vi") val coachTip: String? = null,
    @SerialName("next_drill") val nextDrill: String? = null,
)

@Serializable
internal data class OverallDto(
    val pronunciation: Int,
    val fluency: Int,
    val grammar: Int,
    @SerialName("cefr_estimate") val cefrEstimate: String,
)

@Serializable
internal data class WordDto(
    val word: String,
    val score: Int,
    val level: String,
    val issue: String? = null,
)

@Serializable
internal data class MistakeDto(
    val type: String,
    val original: String,
    val correction: String,
    @SerialName("explain_vi") val explainNative: String,
)

@Serializable
internal data class NativeRewritesDto(
    val casual: String? = null,
    val formal: String? = null,
)
