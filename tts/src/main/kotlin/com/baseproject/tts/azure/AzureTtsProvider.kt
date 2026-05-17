package com.baseproject.tts.azure

import android.content.Context
import com.baseproject.tts.TtsEvent
import com.baseproject.tts.TtsProvider
import com.microsoft.cognitiveservices.speech.ResultReason
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.Executors

/**
 * [TtsProvider] backed by Azure Cognitive Services Speech.
 *
 * Emits high-fidelity timing via WordBoundary events (text offset + audio offset
 * in ticks). Optionally, native viseme events provide phoneme-level timing.
 *
 * Requires the Microsoft Speech SDK as a runtime dependency in the consuming app:
 *   `implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.37.0")`
 *
 * @param speechKey  Azure resource key
 * @param region     Azure region, e.g. "eastus"
 * @param voiceName  e.g. "en-US-JennyNeural"
 */
class AzureTtsProvider(
    private val speechKey: String,
    private val region: String,
    private val voiceName: String = "en-US-JennyNeural",
    initialSpeechRate: Float = 1.0f
) : TtsProvider {

    private val _events = MutableSharedFlow<TtsEvent>(extraBufferCapacity = 256)
    override val events: Flow<TtsEvent> = _events.asSharedFlow()

    private var synthesizer: SpeechSynthesizer? = null
    private val worker = Executors.newSingleThreadExecutor()
    private var currentUttId: String? = null

    @Volatile private var _rate: Float = initialSpeechRate.coerceIn(0.5f, 2.0f)
    override var speechRate: Float
        get() = _rate
        set(value) { _rate = value.coerceIn(0.5f, 2.0f) }

    override suspend fun prepare(context: Context) {
        if (synthesizer != null) return
        val config = SpeechConfig.fromSubscription(speechKey, region).apply {
            speechSynthesisVoiceName = voiceName
        }
        synthesizer = SpeechSynthesizer(config).also { s ->
            s.WordBoundary.addEventListener { _, e ->
                val id = currentUttId ?: return@addEventListener
                val frameMs = e.audioOffset / 10_000L  // ticks (100ns) → ms
                val start = e.textOffset.toInt()
                val end   = (e.textOffset + e.wordLength).toInt()
                _events.tryEmit(TtsEvent.Range(id, start, end, frameMs))
            }
            s.SynthesisStarted.addEventListener { _, _ ->
                currentUttId?.let { _events.tryEmit(TtsEvent.Started(it)) }
            }
            s.SynthesisCompleted.addEventListener { _, _ ->
                currentUttId?.let { _events.tryEmit(TtsEvent.Done(it)) }
            }
            s.SynthesisCanceled.addEventListener { _, e ->
                currentUttId?.let {
                    _events.tryEmit(TtsEvent.Error(it, e.result.reason.name))
                }
            }
        }
    }

    override fun speak(text: String, utteranceId: String) {
        val s = synthesizer ?: run {
            _events.tryEmit(TtsEvent.Error(utteranceId, "azure tts not prepared"))
            return
        }
        val rateSnapshot = _rate
        worker.execute {
            currentUttId = utteranceId
            // Use SSML with <prosody rate> when rate != 1.0; this also makes
            // Azure scale WordBoundary timing to match the actual playback rate.
            val result = if (rateSnapshot == 1.0f) {
                s.SpeakText(text)
            } else {
                s.SpeakSsml(buildSsml(text, rateSnapshot))
            }
            if (result.reason != ResultReason.SynthesizingAudioCompleted) {
                _events.tryEmit(TtsEvent.Error(utteranceId, result.reason.name))
            }
        }
    }

    /**
     * Wrap [text] in an SSML document with `<prosody rate>` so Azure speeds
     * up or slows down speech AND scales WordBoundary offsets accordingly.
     */
    private fun buildSsml(text: String, rate: Float): String {
        // Azure accepts decimal `rate` like "1.25" or percentage like "+25%".
        // Decimal is more intuitive — passes straight through from speechRate.
        val escaped = text.escapeXml()
        val rateStr = "%.2f".format(rate)
        val lang = voiceName.substringBefore('-', "en-US").let { v ->
            if (v.length == 2) "$v-${voiceName.substringAfter('-').substringBefore('-', "US")}"
            else voiceName.substringBeforeLast('-', "en-US")
        }
        return """<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="en-US">
              |<voice name="$voiceName"><prosody rate="$rateStr">$escaped</prosody></voice>
              |</speak>""".trimMargin()
    }

    private fun String.escapeXml(): String = buildString(length + 16) {
        for (c in this@escapeXml) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }

    override fun stop() {
        synthesizer?.StopSpeakingAsync()
    }

    override fun shutdown() {
        synthesizer?.close()
        synthesizer = null
        worker.shutdownNow()
    }
}
