package com.baseproject.aispeech.stt

open class SttResult(
    val text: String,
    val isFinal: Boolean = true,
    val languageCode: String? = null,
)
