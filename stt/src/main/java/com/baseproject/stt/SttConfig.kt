package com.baseproject.stt

import java.util.Locale

data class SttConfig(
    val locale: Locale = Locale.US,
    val partialResults: Boolean = true,
    val maxResults: Int = 5,
)
