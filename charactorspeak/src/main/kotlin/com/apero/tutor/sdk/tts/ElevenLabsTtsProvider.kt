package com.apero.tutor.sdk.tts

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * [TtsProvider] backed by the ElevenLabs `with-timestamps` endpoint.
 *
 * Pipeline per utterance:
 *   1. POST text → receive `{audio_base64, alignment}`.
 *   2. Decode audio bytes to a cache file, play with [MediaPlayer].
 *   3. Walk the alignment timestamps and emit one [TtsEvent.Range] per character.
 *
 * Internally serialises utterances through a [Channel] so that calling
 * [speak] multiple times in a row plays sentences one after another instead
 * of stacking concurrent MediaPlayers on top of each other.
 *
 * Uses [HttpURLConnection] + [JSONObject] only — zero extra deps.
 *
 * @param apiKey   ElevenLabs API key
 * @param voiceId  voice id (e.g. "EXAVITQu4vr4xnSDxMaL" for Sarah)
 * @param modelId  default "eleven_flash_v2_5" (low-latency, free-tier eligible)
 */
class ElevenLabsTtsProvider(
    private val apiKey: String,
    private val voiceId: String,
    private val modelId: String = "eleven_flash_v2_5",
    private val audioLatencyMs: Long = 80L,
    initialSpeechRate: Float = 1.0f
) : TtsProvider {

    private val _events = MutableSharedFlow<TtsEvent>(extraBufferCapacity = 256)
    override val events: Flow<TtsEvent> = _events.asSharedFlow()

    @Volatile private var _rate: Float = initialSpeechRate.coerceIn(0.5f, 2.0f)
    override var speechRate: Float
        get() = _rate
        set(value) { _rate = value.coerceIn(0.5f, 2.0f) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main  = Handler(Looper.getMainLooper())
    private val rangeToken = Object()

    private var cacheDir: File? = null
    private var player: MediaPlayer? = null

    /** Pending utterances. Single processor consumes in FIFO order. */
    private val requests = Channel<Request>(Channel.UNLIMITED)
    private var processorJob: Job? = null

    /** Signals end of currently-playing item so the processor can move on. */
    private var currentDone: CompletableDeferred<Unit>? = null

    override suspend fun prepare(context: Context) {
        cacheDir = File(context.cacheDir, "tts-elevenlabs").apply { mkdirs() }
        if (processorJob?.isActive != true) {
            processorJob = scope.launch { processLoop() }
        }
    }

    override fun speak(text: String, utteranceId: String) {
        if (cacheDir == null) {
            _events.tryEmit(TtsEvent.Error(utteranceId, "not prepared"))
            return
        }
        Log.d(TAG, "queue speak uttId=$utteranceId, text=\"${text.take(60)}\"")
        val audioDeferred: Deferred<SynthesisResponse> = scope.async {
            synthesize(text)
        }
        requests.trySend(Request(text, utteranceId, audioDeferred))
    }

    /** Drain pending queue + stop current playback. Processor itself keeps running. */
    override fun stop() {
        // Discard pending queued items AND cancel their prefetch HTTP work.
        while (true) {
            val r = requests.tryReceive()
            if (r.isSuccess) r.getOrNull()?.audioDeferred?.cancel()
            else break
        }
        // Tear down current player; this fires onCompletion which unblocks the loop.
        main.post {
            try { player?.takeIf { it.isPlaying }?.stop() } catch (_: Throwable) {}
            player?.release()
            player = null
            main.removeCallbacksAndMessages(rangeToken)
            currentDone?.complete(Unit)
        }
    }

    override fun shutdown() {
        stop()
        scope.cancel()
    }

    // ---- Sequential processor -------------------------------------------------

    private suspend fun processLoop() {
        for (req in requests) {
            try {
                processOne(req)
            } catch (t: Throwable) {
                Log.e(TAG, "processOne failed for ${req.uttId}", t)
                _events.tryEmit(TtsEvent.Error(req.uttId, t.message ?: "error"))
            }
        }
    }

    private suspend fun processOne(req: Request) {
        val dir = cacheDir ?: return
        Log.d(TAG, "awaiting prefetched audio uttId=${req.uttId}")
        val resp = req.audioDeferred.await()  // usually already complete
        Log.d(TAG, "audio ready uttId=${req.uttId}, alignment=${resp.alignment.size} chars")

        val audioBytes = Base64.decode(resp.audioB64, Base64.DEFAULT)
        val audioFile = File(dir, "${req.uttId}.mp3")
        audioFile.writeBytes(audioBytes)

        val done = CompletableDeferred<Unit>()
        currentDone = done
        withContext(Dispatchers.Main) {
            playAndSchedule(audioFile, req.uttId, req.text, resp.alignment, done)
        }
        done.await()         // suspend until MediaPlayer finishes / errors / stop()
        currentDone = null
    }

    private fun playAndSchedule(
        file: File,
        uttId: String,
        text: String,
        alignment: List<CharTiming>,
        done: CompletableDeferred<Unit>
    ) {
        // Snapshot rate so it can't change mid-playback (PlaybackParams takes
        // effect immediately but our schedule was computed against the snapshot).
        val rate = _rate
        val mp = MediaPlayer()
        player = mp
        mp.setDataSource(file.absolutePath)
        mp.setOnPreparedListener {
            // Apply rate before start so audio plays at the chosen speed
            if (rate != 1.0f) {
                try { mp.playbackParams = PlaybackParams().setSpeed(rate) }
                catch (t: Throwable) { Log.e(TAG, "setPlaybackParams failed", t) }
            }
            mp.start()
            // Audio plays faster at higher rate, so every char fires sooner
            // by 1/rate. Scale all schedule times accordingly.
            val audioStartUptime = SystemClock.uptimeMillis() + audioLatencyMs

            main.removeCallbacksAndMessages(rangeToken)

            // Defer Started until audio actually begins so the SDK's
            // LipSyncEngine.start() anchors against real audio time, not the
            // moment we *asked* MediaPlayer to play.
            main.postAtTime(
                { _events.tryEmit(TtsEvent.Started(uttId)) },
                rangeToken,
                audioStartUptime
            )

            // Group alignment by word so subtitle highlights one word at a
            // time (cleaner karaoke), and LipSyncEngine.correctDrift() runs
            // ~once per word instead of once per character.
            for (word in groupAlignmentByWord(alignment, text)) {
                val fireMs = ((word.startSec * 1000L) / rate).toLong()
                main.postAtTime(
                    {
                        _events.tryEmit(
                            TtsEvent.Range(uttId, word.start, word.end, fireMs)
                        )
                    },
                    rangeToken,
                    audioStartUptime + fireMs
                )
            }
        }
        mp.setOnCompletionListener {
            _events.tryEmit(TtsEvent.Done(uttId))
            it.release()
            if (player === it) player = null
            done.complete(Unit)
        }
        mp.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "MediaPlayer.onError what=$what extra=$extra")
            _events.tryEmit(TtsEvent.Error(uttId, "media error what=$what extra=$extra"))
            try { mp.release() } catch (_: Throwable) {}
            if (player === mp) player = null
            done.complete(Unit)
            true
        }
        mp.prepareAsync()
    }

    private fun synthesize(text: String): SynthesisResponse {
        val url = URL("https://api.elevenlabs.io/v1/text-to-speech/$voiceId/with-timestamps")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("xi-api-key", apiKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        val body = JSONObject().apply {
            put("text", text)
            put("model_id", modelId)
        }.toString()
        conn.outputStream.use { os: OutputStream -> os.write(body.toByteArray()) }
        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
            throw RuntimeException(err)
        }
        val respText = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(respText)
        val audioB64 = json.getString("audio_base64")
        val alignJson = json.getJSONObject("alignment")
        val chars     = alignJson.getJSONArray("characters")
        val starts    = alignJson.getJSONArray("character_start_times_seconds")
        val timings   = ArrayList<CharTiming>(chars.length())
        for (i in 0 until chars.length()) {
            timings += CharTiming(chars.getString(i), starts.getDouble(i))
        }
        return SynthesisResponse(audioB64, timings)
    }

    /**
     * Walk per-character alignment and emit one [WordTiming] per whitespace-
     * delimited word. `startSec` for a word is the start time of its first
     * non-whitespace character.
     */
    private fun groupAlignmentByWord(
        alignment: List<CharTiming>,
        text: String
    ): List<WordTiming> {
        val out = ArrayList<WordTiming>()
        val len = minOf(alignment.size, text.length)
        var i = 0
        while (i < len) {
            // Skip leading whitespace
            while (i < len && text[i].isWhitespace()) i++
            if (i >= len) break
            val start = i
            val startSec = alignment[i].startSec
            // Consume non-whitespace
            while (i < len && !text[i].isWhitespace()) i++
            out += WordTiming(start = start, end = i, startSec = startSec)
        }
        return out
    }

    private data class Request(
        val text: String,
        val uttId: String,
        val audioDeferred: Deferred<SynthesisResponse>
    )

    private data class WordTiming(val start: Int, val end: Int, val startSec: Double)
    private data class CharTiming(val ch: String, val startSec: Double)
    private data class SynthesisResponse(
        val audioB64: String,
        val alignment: List<CharTiming>
    )

    private companion object { const val TAG = "ElevenLabsTts" }
}
