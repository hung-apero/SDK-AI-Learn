package com.baseproject.stt.providers.inhouse

import com.baseproject.stt.core.AudioSource
import com.baseproject.stt.core.Segment
import com.baseproject.stt.core.SttConfig
import com.baseproject.stt.core.SttError
import com.baseproject.stt.core.SttEvent
import com.baseproject.stt.core.StreamingSttProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

/**
 * Streaming STT provider for your own backend over WebSocket.
 *
 * Wire protocol (one direction = JSON text frames, audio = binary frames):
 *  - Client opens WS with `Authorization: Bearer <token>`.
 *  - Client sends JSON `{"type":"start","language":"en-US","sample_rate":16000,...}`.
 *  - Client streams binary PCM frames.
 *  - Client sends JSON `{"type":"end"}` when done.
 *  - Server emits JSON events: `{"type":"partial","text":"..."}`,
 *    `{"type":"final","transcript":"...", "segments":[...], ...}`, `{"type":"error","message":"..."}`,
 *    or `{"type":"endOfSpeech"}`.
 */
class InHouseWsProvider(
    private val wssUrl: String,
    private val http: OkHttpClient,
    private val tokenProvider: suspend () -> String,
) : StreamingSttProvider<InHouseSttResult> {

    override val name: String = "inhouse-ws"

    override fun supports(source: AudioSource): Boolean = source is AudioSource.PcmStream

    override suspend fun recognize(audio: AudioSource, config: SttConfig): InHouseSttResult =
        recognizeStream(audio, config)
            .mapNotNull { (it as? SttEvent.Final)?.result }
            .first()

    override fun recognizeStream(
        audio: AudioSource,
        config: SttConfig,
    ): Flow<SttEvent<InHouseSttResult>> = callbackFlow {

        if (audio !is AudioSource.PcmStream) {
            trySend(SttEvent.Error(SttError.UnsupportedSource(audio, name)))
            close()
            return@callbackFlow
        }

        val token = runCatching { tokenProvider() }
            .getOrElse {
                trySend(SttEvent.Error(SttError.Auth("Token provider failed: ${it.message}")))
                close()
                return@callbackFlow
            }

        val request = Request.Builder()
            .url(wssUrl)
            .header("Authorization", "Bearer $token")
            .header("X-Language", config.languageTag)
            .build()

        var pumpJob: Job? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                val openFrame = JSONObject().apply {
                    put("type", "start")
                    put("language", config.languageTag)
                    put("sample_rate", audio.format.sampleRateHz)
                    put("channels", audio.format.channels)
                    put("encoding", audio.format.encoding.name)
                    put("punctuation", config.enablePunctuation)
                    put("max_alternatives", config.maxAlternatives)
                }.toString()
                ws.send(openFrame)

                pumpJob = CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        audio.frames.collect { frame -> ws.send(frame.toByteString()) }
                        ws.send(JSONObject().put("type", "end").toString())
                    }.onFailure {
                        trySend(SttEvent.Error(SttError.Network(it)))
                    }
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (json.optString("type")) {
                    "partial" -> trySend(SttEvent.Partial(json.optString("text")))
                    "endOfSpeech" -> trySend(SttEvent.EndOfSpeech)
                    "final" -> trySend(SttEvent.Final(parseFinal(json, config.languageTag, text)))
                    "error" -> {
                        trySend(SttEvent.Error(SttError.ProviderFailure(name, message = json.optString("message"))))
                        ws.close(/* code = */ 1000, /* reason = */ "server error")
                    }
                }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                // Server normally uses text frames; ignore binary.
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(code, reason)
                close()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                trySend(SttEvent.Error(SttError.Network(t)))
                close()
            }
        }

        val socket: WebSocket = http.newWebSocket(request, listener)

        awaitClose {
            pumpJob?.cancel()
            socket.close(/* code = */ 1000, /* reason = */ "client closed")
        }
    }

    private fun parseFinal(json: JSONObject, fallbackLang: String, raw: String): InHouseSttResult {
        val segs: List<Segment> = json.optJSONArray("segments")?.let { arr ->
            (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                Segment(s.optString("text"), s.optInt("start_ms"), s.optInt("end_ms"))
            }
        }.orEmpty()

        return InHouseSttResult(
            transcript = json.optString("transcript"),
            confidence = json.optDouble("confidence", Double.NaN).takeIf { !it.isNaN() },
            languageCode = json.optString("language", fallbackLang),
            durationMs = json.optLong("duration_ms"),
            modelVersion = json.optString("model_version"),
            processingTimeMs = json.optLong("processing_time_ms"),
            segments = segs,
            requestId = json.optString("request_id"),
            raw = raw,
        )
    }
}
