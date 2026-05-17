package com.baseproject.stt.providers.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.baseproject.stt.core.AudioSource
import com.baseproject.stt.core.SttConfig
import com.baseproject.stt.core.SttError
import com.baseproject.stt.core.SttEvent
import com.baseproject.stt.core.StreamingSttProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull

/**
 * Wraps Android's system [SpeechRecognizer].
 *
 * Only accepts [AudioSource.SystemMicrophone] — the OS owns mic capture.
 * Supports streaming partial results.
 */
class AndroidSpeechRecognizerProvider(
    private val context: Context,
) : StreamingSttProvider<AndroidSttResult> {

    override val name: String = "android"

    override fun supports(source: AudioSource): Boolean =
        source is AudioSource.SystemMicrophone

    override suspend fun recognize(audio: AudioSource, config: SttConfig): AndroidSttResult =
        recognizeStream(audio, config)
            .mapNotNull { (it as? SttEvent.Final)?.result }
            .first()

    override fun recognizeStream(
        audio: AudioSource,
        config: SttConfig,
    ): Flow<SttEvent<AndroidSttResult>> = callbackFlow {

        if (audio !is AudioSource.SystemMicrophone) {
            trySend(SttEvent.Error(SttError.UnsupportedSource(audio, name)))
            close()
            return@callbackFlow
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SttEvent.Error(SttError.ProviderFailure(name, message = "Not available on device")))
            close()
            return@callbackFlow
        }

        val mainHandler = Handler(Looper.getMainLooper())
        var recognizer: SpeechRecognizer? = null

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle) {
                val text = partialResults
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotEmpty()) trySend(SttEvent.Partial(text))
            }

            override fun onEndOfSpeech() {
                trySend(SttEvent.EndOfSpeech)
            }

            override fun onResults(results: Bundle) {
                val all = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val scores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                val result = AndroidSttResult(
                    transcript = all.firstOrNull().orEmpty(),
                    confidence = scores?.firstOrNull()?.toDouble(),
                    languageCode = config.languageTag,
                    durationMs = 0L,
                    nBest = all,
                    confidenceScores = scores,
                )
                trySend(SttEvent.Final(result))
                close()
            }

            override fun onError(error: Int) {
                val mapped: SttError = when (error) {
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER -> SttError.Network(RuntimeException("code=$error"))
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        SttError.PermissionDenied(android.Manifest.permission.RECORD_AUDIO)
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SttError.Timeout(config.timeoutMs)
                    else -> SttError.ProviderFailure(name, message = "code=$error")
                }
                trySend(SttEvent.Error(mapped))
                close()
            }
        }

        // SpeechRecognizer requires main thread for create/startListening/destroy.
        mainHandler.post {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.languageTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, config.enablePartialResults)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, config.maxAlternatives.coerceAtLeast(1))
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, config.extras["preferOffline"] as? Boolean ?: false)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }
                startListening(intent)
            }
        }

        awaitClose {
            mainHandler.post { recognizer?.destroy() }
        }
    }
}
