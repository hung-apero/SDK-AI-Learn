package com.baseproject.stt.gemini

import android.content.Context
import com.baseproject.stt.SpeechToTextProvider
import com.baseproject.stt.SttErrorCode
import com.baseproject.stt.SttEvent
import com.baseproject.stt.gemini.internal.GeminiApi
import com.baseproject.stt.gemini.internal.GeminiAudioRecorder
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class GeminiSpeechToTextProvider(
    private val apiKey: String,
    private val model: String = "models/gemini-2.5-flash-lite",
    private val locale: Locale = Locale.US,
    private val nativeLocale: Locale = Locale.forLanguageTag("vi"),
    private val systemPrompt: String = DEFAULT_COACH_PROMPT,
) : SpeechToTextProvider {

    private val _events = MutableSharedFlow<SttEvent>(extraBufferCapacity = 16)
    override val events: Flow<SttEvent> = _events.asSharedFlow()

    override var isListening: Boolean = false
        private set

    private var api: GeminiApi? = null
    private var recorder: GeminiAudioRecorder? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var inflight: Job? = null

    override suspend fun prepare(context: Context) {
        if (api == null) api = GeminiApi(apiKey, model, systemPrompt)
        if (recorder == null) recorder = GeminiAudioRecorder(context.applicationContext)
    }

    override fun startListening() {
        if (isListening) return
        val rec = recorder ?: run {
            _events.tryEmit(SttEvent.Error(SttErrorCode.UNKNOWN, "provider not prepared"))
            return
        }
        try {
            rec.start()
            isListening = true
            _events.tryEmit(SttEvent.ReadyForSpeech)
            _events.tryEmit(SttEvent.BeginningOfSpeech)
        } catch (t: Throwable) {
            isListening = false
            _events.tryEmit(SttEvent.Error(SttErrorCode.AUDIO, t.message ?: "recorder start failed"))
        }
    }

    override fun stopListening() {
        if (!isListening) return
        val file = recorder?.stop()
        isListening = false
        _events.tryEmit(SttEvent.EndOfSpeech)
        if (file == null || !file.exists() || file.length() == 0L) {
            _events.tryEmit(SttEvent.Error(SttErrorCode.AUDIO, "empty recording"))
            return
        }
        val client = api ?: run {
            _events.tryEmit(SttEvent.Error(SttErrorCode.UNKNOWN, "provider not prepared"))
            return
        }
        inflight = scope.launch {
            try {
                val payload = client.generateContent(file, "audio/mp4")
                _events.tryEmit(SttEvent.Result(GeminiSttResult.fromPayload(payload)))
            } catch (t: Throwable) {
                _events.tryEmit(SttEvent.Error(mapError(t), t.message ?: "Gemini call failed"))
            } finally {
                file.delete()
            }
        }
    }

    override fun cancel() {
        inflight?.cancel(); inflight = null
        recorder?.cancel()
        isListening = false
    }

    override fun destroy() {
        cancel()
        api?.close(); api = null
        recorder = null
        scope.cancel()
    }

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    internal fun mapError(t: Throwable): SttErrorCode {
        val msg = t.message.orEmpty()
        val lower = msg.lowercase()
        return when {
            "HTTP 4" in msg || "HTTP 5" in msg -> SttErrorCode.SERVER
            "timeout" in lower || "unable to resolve" in lower -> SttErrorCode.NETWORK
            else -> SttErrorCode.UNKNOWN
        }
    }
}
