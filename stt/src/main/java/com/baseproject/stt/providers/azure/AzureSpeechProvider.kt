package com.baseproject.stt.providers.azure

import com.baseproject.stt.core.AudioSource
import com.baseproject.stt.core.SttConfig
import com.baseproject.stt.core.SttError
import com.baseproject.stt.core.SttEvent
import com.baseproject.stt.core.StreamingSttProvider
import com.baseproject.stt.core.Word
import com.microsoft.cognitiveservices.speech.CancellationDetails
import com.microsoft.cognitiveservices.speech.OutputFormat
import com.microsoft.cognitiveservices.speech.ProfanityOption
import com.microsoft.cognitiveservices.speech.PropertyId
import com.microsoft.cognitiveservices.speech.ResultReason
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechRecognitionResult
import com.microsoft.cognitiveservices.speech.SpeechRecognizer
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import com.microsoft.cognitiveservices.speech.audio.AudioInputStream
import com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat
import com.microsoft.cognitiveservices.speech.audio.PushAudioInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.CoroutineContext

/**
 * Azure Cognitive Services — Speech-to-Text provider.
 *
 * Supports [AudioSource.PcmStream] (streaming), [AudioSource.File] and [AudioSource.Bytes] (one-shot).
 * Cannot open the system mic — caller must capture mic into a PCM flow first.
 */
class AzureSpeechProvider(
    private val subscriptionKey: String,
    private val region: String,
) : StreamingSttProvider<AzureSttResult> {

    override val name: String = "azure"

    override fun supports(source: AudioSource): Boolean = when (source) {
        is AudioSource.PcmStream, is AudioSource.File, is AudioSource.Bytes -> true
        is AudioSource.SystemMicrophone -> false
    }

    override suspend fun recognize(audio: AudioSource, config: SttConfig): AzureSttResult =
        withContext(Dispatchers.IO) {
            val speechConfig = buildSpeechConfig(config)
            val (audioConfig, _) = audio.toAudioConfig(coroutineContext)
            val recognizer = SpeechRecognizer(speechConfig, audioConfig)
            try {
                val result = recognizer.recognizeOnceAsync().get()
                if (result.reason == ResultReason.Canceled) {
                    val cancel = CancellationDetails.fromResult(result)
                    throw SttError.ProviderFailure(name, message = "Canceled: ${cancel.errorDetails}")
                }
                parseDetailedResult(result, config.languageTag)
            } finally {
                recognizer.close()
                audioConfig.close()
                speechConfig.close()
            }
        }

    override fun recognizeStream(
        audio: AudioSource,
        config: SttConfig,
    ): Flow<SttEvent<AzureSttResult>> = callbackFlow {

        if (!supports(audio)) {
            trySend(SttEvent.Error(SttError.UnsupportedSource(audio, name)))
            close()
            return@callbackFlow
        }

        val speechConfig = buildSpeechConfig(config)
        val (audioConfig, pumpJob) = audio.toAudioConfig(coroutineContext)
        val recognizer = SpeechRecognizer(speechConfig, audioConfig)

        recognizer.recognizing.addEventListener { _, e ->
            val text = e.result.text
            if (!text.isNullOrBlank()) trySend(SttEvent.Partial(text))
        }
        recognizer.recognized.addEventListener { _, e ->
            if (e.result.reason == ResultReason.RecognizedSpeech) {
                trySend(SttEvent.Final(parseDetailedResult(e.result, config.languageTag)))
            }
        }
        recognizer.canceled.addEventListener { _, e ->
            trySend(SttEvent.Error(SttError.ProviderFailure(name, message = "Canceled: ${e.errorDetails}")))
            close()
        }
        recognizer.sessionStopped.addEventListener { _, _ -> close() }
        recognizer.speechEndDetected.addEventListener { _, _ -> trySend(SttEvent.EndOfSpeech) }

        runCatching { recognizer.startContinuousRecognitionAsync().get() }
            .onFailure {
                trySend(SttEvent.Error(SttError.Network(it)))
                close()
            }

        awaitClose {
            runCatching { recognizer.stopContinuousRecognitionAsync().get() }
            pumpJob?.cancel()
            recognizer.close()
            audioConfig.close()
            speechConfig.close()
        }
    }

    /* ---------------- helpers ---------------- */

    private fun buildSpeechConfig(config: SttConfig): SpeechConfig =
        SpeechConfig.fromSubscription(subscriptionKey, region).apply {
            speechRecognitionLanguage = config.languageTag
            setProfanity(if (config.profanityFilter) ProfanityOption.Masked else ProfanityOption.Raw)
            outputFormat = OutputFormat.Detailed
            requestWordLevelTimestamps()
        }

    /**
     * Returns the Azure AudioConfig and (for streaming sources) a Job that pumps the flow.
     * Caller is responsible for cancelling the Job when done.
     */
    private fun AudioSource.toAudioConfig(parent: CoroutineContext): Pair<AudioConfig, Job?> = when (this) {
        is AudioSource.File -> AudioConfig.fromWavFileInput(path) to null

        is AudioSource.Bytes -> {
            val tmp = File.createTempFile("azure_in", ".wav").apply { writeBytes(data) }
            AudioConfig.fromWavFileInput(tmp.absolutePath) to null
        }

        is AudioSource.PcmStream -> {
            val format = AudioStreamFormat.getWaveFormatPCM(
                /* samplesPerSecond = */ this.format.sampleRateHz.toLong(),
                /* bitsPerSample = */ 16.toShort(),
                /* channels = */ this.format.channels.toShort(),
            )
            val pushStream: PushAudioInputStream = AudioInputStream.createPushStream(format)
            val job = CoroutineScope(parent + Dispatchers.IO).launch {
                try {
                    frames.collect { pushStream.write(it) }
                } finally {
                    pushStream.close()
                }
            }
            AudioConfig.fromStreamInput(pushStream) to job
        }

        AudioSource.SystemMicrophone -> error("unreachable")
    }

    private fun parseDetailedResult(result: SpeechRecognitionResult, lang: String): AzureSttResult {
        val rawJson = result.properties
            .getProperty(PropertyId.SpeechServiceResponse_JsonResult)
            .orEmpty()

        val root = if (rawJson.isNotEmpty()) JSONObject(rawJson) else JSONObject()
        val nBestArr = root.optJSONArray("NBest")
        val best = nBestArr?.optJSONObject(0)

        val words: List<Word> = best?.optJSONArray("Words")?.let { arr ->
            (0 until arr.length()).map { i ->
                val w = arr.getJSONObject(i)
                val offsetMs = (w.optLong("Offset") / 10_000).toInt()
                val durMs = (w.optLong("Duration") / 10_000).toInt()
                Word(
                    text = w.optString("Word"),
                    startMs = offsetMs,
                    endMs = offsetMs + durMs,
                    confidence = w.optDouble("Confidence", Double.NaN).takeIf { !it.isNaN() },
                )
            }
        }.orEmpty()

        val nBest: List<AzureSttResult.NBestEntry> = nBestArr?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AzureSttResult.NBestEntry(
                    text = o.optString("Display"),
                    confidence = o.optDouble("Confidence", 0.0),
                )
            }
        }.orEmpty()

        // Azure returns duration as BigInteger of 100-nanosecond ticks. Fall back to
        // parsing from the detailed JSON if the SDK API shape changes between versions.
        val durationMs: Long = runCatching {
            val durationTicks = result.javaClass.getMethod("duration").invoke(result) as? java.math.BigInteger
            (durationTicks?.longValueExact() ?: 0L) / 10_000L
        }.getOrElse {
            runCatching { root.optLong("Duration") / 10_000L }.getOrDefault(0L)
        }

        return AzureSttResult(
            transcript = result.text.orEmpty(),
            confidence = nBest.firstOrNull()?.confidence,
            languageCode = lang,
            durationMs = durationMs,
            words = words,
            nBest = nBest,
            detectedLanguage = result.properties.getProperty(
                PropertyId.SpeechServiceConnection_AutoDetectSourceLanguageResult
            ),
            rawJson = rawJson,
        )
    }
}
