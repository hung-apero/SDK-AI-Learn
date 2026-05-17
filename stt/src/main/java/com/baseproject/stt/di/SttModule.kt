package com.baseproject.stt.di

import com.baseproject.stt.core.SttResult
import com.baseproject.stt.facade.SttFacade
import com.baseproject.stt.pipeline.DefaultSttPipeline
import com.baseproject.stt.pipeline.SttPipeline
import com.baseproject.stt.providers.android.AndroidSpeechRecognizerProvider
import com.baseproject.stt.registry.FallbackProvider
import com.baseproject.stt.registry.SttRegistry
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Default Koin module: registers only the Android SpeechRecognizer provider
 * (zero extra config required). To enable Azure or InHouse, pass an
 * [SttConfigParams] override or extend this module in your app.
 */
val sttModule = module {

    // OkHttp shared by network providers.
    single<OkHttpClient> {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
    }

    // Default provider — Android SpeechRecognizer (on-device, no creds).
    single { AndroidSpeechRecognizerProvider(androidContext()) }

    // Registry pre-loaded with the Android provider. Add more in your app module.
    single<SttRegistry> {
        SttRegistry().apply {
            register(get<AndroidSpeechRecognizerProvider>())
        }
    }

    // Single-provider pipeline by default. Override with FallbackProvider in your app
    // once you wire up Azure / InHouse credentials.
    single<SttPipeline<SttResult>> {
        @Suppress("UNCHECKED_CAST")
        DefaultSttPipeline(
            provider = get<AndroidSpeechRecognizerProvider>() as com.baseproject.stt.core.SttProvider<SttResult>,
        )
    }

    singleOf(::SttFacade)
}

/**
 * Example of how to compose all 3 providers + fallback in your app's DI module.
 * Call this from your `Application.onCreate` after providing your Azure + InHouse
 * credentials and token provider.
 *
 * ```kotlin
 * val appSttModule = module {
 *     single { SttBuilder.full(
 *         context = androidContext(),
 *         http = get(),
 *         azureKey = BuildConfig.AZURE_KEY,
 *         azureRegion = BuildConfig.AZURE_REGION,
 *         inhouseBaseUrl = BuildConfig.STT_API,
 *         inhouseWssUrl = BuildConfig.STT_WSS,
 *         tokenProvider = { firebaseAuth.currentUser!!.getIdToken(false).await().token!! },
 *     ) }
 * }
 * ```
 */
object SttBuilder {

    fun full(
        context: android.content.Context,
        http: OkHttpClient,
        azureKey: String,
        azureRegion: String,
        inhouseBaseUrl: String,
        inhouseWssUrl: String,
        tokenProvider: suspend () -> String,
    ): SttFacade {
        val android = AndroidSpeechRecognizerProvider(context)
        val azure = com.baseproject.stt.providers.azure.AzureSpeechProvider(azureKey, azureRegion)
        val inhouseRest = com.baseproject.stt.providers.inhouse.InHouseRestProvider(inhouseBaseUrl, http, tokenProvider)
        val inhouseWs = com.baseproject.stt.providers.inhouse.InHouseWsProvider(inhouseWssUrl, http, tokenProvider)

        @Suppress("UNCHECKED_CAST")
        val fallback = FallbackProvider(
            listOf(
                inhouseWs as com.baseproject.stt.core.SttProvider<SttResult>,
                azure as com.baseproject.stt.core.SttProvider<SttResult>,
                android as com.baseproject.stt.core.SttProvider<SttResult>,
            )
        )
        return SttFacade(DefaultSttPipeline(fallback))
    }
}
