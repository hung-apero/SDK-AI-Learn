package com.baseproject.aispeech.stt.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.baseproject.aispeech.stt.SpeechToTextProvider
import com.baseproject.aispeech.stt.SttErrorCode
import com.baseproject.aispeech.stt.SttEvent
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AndroidSpeechToTextProvider(
    private val context: Context,
    private val locale: Locale = Locale.US,
    private val partialResults: Boolean = true,
) : SpeechToTextProvider {

    private val _events = MutableSharedFlow<SttEvent>(extraBufferCapacity = 16)
    override val events: Flow<SttEvent> = _events.asSharedFlow()

    private var recognizer: SpeechRecognizer? = null
    override var isListening: Boolean = false
        private set

    override suspend fun prepare(context: Context) = Unit

    override fun startListening() {
        cancel()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createListener())
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResults)
        }
        recognizer?.startListening(intent)
        isListening = true
    }

    override fun stopListening() {
        recognizer?.stopListening()
        isListening = false
    }

    override fun cancel() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        isListening = false
    }

    override fun destroy() {
        cancel()
    }

    override fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _events.tryEmit(SttEvent.ReadyForSpeech)
        }

        override fun onBeginningOfSpeech() {
            _events.tryEmit(SttEvent.BeginningOfSpeech)
        }

        override fun onEndOfSpeech() {
            isListening = false
            _events.tryEmit(SttEvent.EndOfSpeech)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val best = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _events.tryEmit(
                SttEvent.Result(
                    AndroidSttResult(
                        text = best,
                        isFinal = true,
                        languageCode = locale.toLanguageTag(),
                    )
                )
            )
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val best = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _events.tryEmit(
                SttEvent.PartialResult(
                    AndroidSttResult(
                        text = best,
                        isFinal = false,
                        languageCode = locale.toLanguageTag(),
                    )
                )
            )
        }

        override fun onError(error: Int) {
            isListening = false
            _events.tryEmit(SttEvent.Error(mapErrorCode(error), "SpeechRecognizer error: $error"))
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun mapErrorCode(error: Int): SttErrorCode = when (error) {
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SttErrorCode.NETWORK
        SpeechRecognizer.ERROR_AUDIO -> SttErrorCode.AUDIO
        SpeechRecognizer.ERROR_SERVER -> SttErrorCode.SERVER
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SttErrorCode.NO_MATCH
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SttErrorCode.RECOGNIZER_BUSY
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SttErrorCode.INSUFFICIENT_PERMISSIONS
        else -> SttErrorCode.UNKNOWN
    }
}
