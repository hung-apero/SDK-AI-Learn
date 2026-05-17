package com.baseproject.stt.core

/**
 * Provider-agnostic recognition configuration.
 *
 * Most fields are best-effort hints — providers ignore options they don't support.
 * Use [extras] for provider-specific options (e.g. `"preferOffline" to true` for Android).
 */
data class SttConfig(
    val languageTag: String = "en-US",
    val model: String? = null,
    val enablePartialResults: Boolean = true,
    val enablePunctuation: Boolean = true,
    val profanityFilter: Boolean = false,
    val hints: List<String> = emptyList(),
    val maxAlternatives: Int = 1,
    val timeoutMs: Long = 30_000,
    val extras: Map<String, Any> = emptyMap(),
)
