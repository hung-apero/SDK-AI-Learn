package com.baseproject.aispeech.tts.android

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.baseproject.aispeech.tts.TtsEvent
import com.baseproject.aispeech.tts.TtsProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Locale

/**
 * [TtsProvider] backed by Android's built-in [TextToSpeech].
 *
 * Free, offline, available on every device. Lip sync timing is derived from
 * [UtteranceProgressListener.onRangeStart] callbacks, which give per-word
 * audio frame positions on API 26+.
 *
 * Voices are device-specific. Use [availableVoices] to enumerate what the
 * current device has installed; pass a [Voice.getName] to [voiceName] /
 * the [initialVoiceName] constructor argument to pick one.
 *
 * @param locale spoken language. Defaults to US English.
 * @param sampleRateHz audio sample rate assumed when converting [onRangeStart]
 *   `frame` to milliseconds. Google TTS defaults to 22050 Hz on modern devices.
 * @param initialVoiceName specific voice name (e.g. "en-us-x-tpf-local"). If
 *   `null`, the engine default for [locale] is used.
 * @param initialSpeechRate starting speech rate; can be changed later via
 *   [speechRate].
 */
class AndroidTtsProvider(
    private val locale: Locale = Locale.US,
    private val sampleRateHz: Int = 22050,
    initialVoiceName: String? = null,
    initialSpeechRate: Float = 1.0f
) : TtsProvider {

    private val _events = MutableSharedFlow<TtsEvent>(extraBufferCapacity = 64)
    override val events: Flow<TtsEvent> = _events.asSharedFlow()

    private var tts: TextToSpeech? = null
    private var ready = false

    private var _rate: Float = initialSpeechRate.coerceIn(0.5f, 2.0f)
    override var speechRate: Float
        get() = _rate
        set(value) {
            _rate = value.coerceIn(0.5f, 2.0f)
            tts?.setSpeechRate(_rate)
        }

    /**
     * Current voice name. Setting this looks up the voice on the device by
     * [Voice.getName] and applies it. Silent no-op if the name isn't installed.
     */
    var voiceName: String? = initialVoiceName
        set(value) {
            field = value
            applyVoice(value)
        }

    override suspend fun prepare(context: Context) {
        if (ready) return
        val initDone = CompletableDeferred<Boolean>()
        tts = TextToSpeech(context.applicationContext) { status ->
            initDone.complete(status == TextToSpeech.SUCCESS)
        }
        ready = initDone.await()
        if (!ready) return
        tts?.language = locale
        tts?.setSpeechRate(_rate)
        applyVoice(voiceName)
        // Log available voices once so callers can discover names. Filter to
        // the requested locale to keep the dump short.
        runCatching {
            val matching = tts?.voices.orEmpty()
                .filter { it.locale.language == locale.language }
                .sortedBy { it.name }
            Log.i(TAG, "Available voices for ${locale.language} (${matching.size}):")
            for (v in matching) {
                Log.i(TAG, "  ${v.name}  q=${v.quality} lat=${v.latency} network=${v.isNetworkConnectionRequired}")
            }
        }
        tts?.setOnUtteranceProgressListener(progressListener)
    }

    private fun applyVoice(name: String?) {
        if (!ready || name == null) return
        val voice = tts?.voices?.find { it.name == name }
        if (voice != null) {
            tts?.voice = voice
            Log.i(TAG, "Voice set to: $name")
        } else {
            Log.w(TAG, "Voice '$name' not found on this device; using default")
        }
    }

    /**
     * Voices installed on the device. Empty until [prepare] has completed.
     * Useful for building a voice picker UI.
     */
    fun availableVoices(): List<Voice> = tts?.voices?.toList().orEmpty()

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            _events.tryEmit(TtsEvent.Started(utteranceId))
        }
        override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
            val frameMs = frame.toLong() * 1000L / sampleRateHz
            _events.tryEmit(TtsEvent.Range(utteranceId, start, end, frameMs))
        }
        override fun onDone(utteranceId: String) {
            _events.tryEmit(TtsEvent.Done(utteranceId))
        }
        @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, errorCode)"))
        override fun onError(utteranceId: String) {
            _events.tryEmit(TtsEvent.Error(utteranceId, "tts error"))
        }
        override fun onError(utteranceId: String, errorCode: Int) {
            _events.tryEmit(TtsEvent.Error(utteranceId, "tts error code=$errorCode"))
        }
    }

    override fun speak(text: String, utteranceId: String) {
        if (!ready) {
            _events.tryEmit(TtsEvent.Error(utteranceId, "tts not ready"))
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }

    private companion object { const val TAG = "AndroidTtsProvider" }
}
