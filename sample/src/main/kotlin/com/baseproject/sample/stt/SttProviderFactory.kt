package com.baseproject.sample.stt

import android.content.Context
import com.baseproject.sample.BuildConfig
import com.baseproject.aispeech.stt.SpeechToTextProvider
import com.baseproject.aispeech.stt.android.AndroidSpeechToTextProvider
import com.baseproject.aispeech.stt.gemini.GeminiSpeechToTextProvider
import java.util.Locale

enum class SttProviderKind { ANDROID, GEMINI }

object SttProviderFactory {

    private val sttLocale: Locale by lazy { Locale.forLanguageTag(BuildConfig.STT_LOCALE) }

    fun defaultKind(): SttProviderKind = when (BuildConfig.STT_PROVIDER.lowercase()) {
        "gemini" -> if (BuildConfig.GEMINI_API_KEY.isNotBlank()) SttProviderKind.GEMINI
                    else SttProviderKind.ANDROID
        else -> SttProviderKind.ANDROID
    }

    fun create(context: Context, kind: SttProviderKind): SpeechToTextProvider = when (kind) {
        SttProviderKind.ANDROID -> AndroidSpeechToTextProvider(
            context = context.applicationContext,
            locale = sttLocale,
        )
        SttProviderKind.GEMINI -> GeminiSpeechToTextProvider(
            apiKey = BuildConfig.GEMINI_API_KEY,
            model = BuildConfig.GEMINI_STT_MODEL,
            locale = sttLocale,
            nativeLocale = Locale.forLanguageTag(BuildConfig.GEMINI_NATIVE_LOCALE),
        )
    }
}
