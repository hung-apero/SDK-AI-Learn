package com.baseproject.stt.providers.inhouse

import com.baseproject.stt.core.Segment
import com.baseproject.stt.core.SttResult

/**
 * Result subtype for [InHouseRestProvider] / [InHouseWsProvider]. Adds the
 * fields your backend returns (model version, processing time, segments, request id).
 */
data class InHouseSttResult(
    override val transcript: String,
    override val confidence: Double?,
    override val languageCode: String,
    override val durationMs: Long,
    val modelVersion: String,
    val processingTimeMs: Long,
    val segments: List<Segment>,
    val requestId: String,
    val raw: String,
) : SttResult() {
    override val provider: String = "inhouse"
}
