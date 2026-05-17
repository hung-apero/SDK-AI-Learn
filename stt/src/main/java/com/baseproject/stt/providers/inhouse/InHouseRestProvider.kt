package com.baseproject.stt.providers.inhouse

import com.baseproject.stt.core.AudioSource
import com.baseproject.stt.core.Segment
import com.baseproject.stt.core.SttConfig
import com.baseproject.stt.core.SttError
import com.baseproject.stt.core.SttProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File

/**
 * One-shot HTTP/REST STT provider for your own backend.
 *
 * Uploads a finished audio file (or in-memory bytes) as multipart/form-data,
 * expects a JSON response — schema:
 * ```json
 * {
 *   "transcript": "...",
 *   "confidence": 0.92,
 *   "language": "en-US",
 *   "duration_ms": 4320,
 *   "model_version": "v3",
 *   "processing_time_ms": 540,
 *   "segments": [{"text": "...", "start_ms": 0, "end_ms": 1200}],
 *   "request_id": "..."
 * }
 * ```
 * @param baseUrl       e.g. `https://stt.example.com`
 * @param http          OkHttpClient (configure timeouts, interceptors externally)
 * @param tokenProvider Suspending source of bearer token (e.g. Firebase ID token)
 */
class InHouseRestProvider(
    private val baseUrl: String,
    private val http: OkHttpClient,
    private val tokenProvider: suspend () -> String,
) : SttProvider<InHouseSttResult> {

    override val name: String = "inhouse-rest"

    override fun supports(source: AudioSource): Boolean =
        source is AudioSource.File || source is AudioSource.Bytes

    override suspend fun recognize(audio: AudioSource, config: SttConfig): InHouseSttResult =
        withContext(Dispatchers.IO) {
            val (bodyPart, mime) = when (audio) {
                is AudioSource.File -> {
                    val f = File(audio.path)
                    f.asRequestBody(audio.mime.toMediaType()) to audio.mime
                }

                is AudioSource.Bytes ->
                    audio.data.toRequestBody(audio.mime.toMediaType()) to audio.mime

                else -> throw SttError.UnsupportedSource(audio, name)
            }

            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("language", config.languageTag)
                .addFormDataPart("model", config.model.orEmpty())
                .addFormDataPart("punctuation", config.enablePunctuation.toString())
                .addFormDataPart("audio", "audio.bin", bodyPart)
                .also { mb -> config.hints.forEach { mb.addFormDataPart("hints[]", it) } }
                .build()

            val token = tokenProvider()
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/v1/stt")
                .header("Authorization", "Bearer $token")
                .header("X-Mime-Type", mime)
                .post(multipart)
                .build()

            val response = runCatching { http.newCall(req).execute() }
                .getOrElse { throw SttError.Network(it) }

            response.use { r ->
                if (!r.isSuccessful) throw mapHttpError(r)
                val raw = r.body?.string().orEmpty()
                parse(raw, config.languageTag)
            }
        }

    private fun parse(raw: String, fallbackLang: String): InHouseSttResult {
        val j = JSONObject(raw)
        val segs: List<Segment> = j.optJSONArray("segments")?.let { arr ->
            (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                Segment(
                    text = s.optString("text"),
                    startMs = s.optInt("start_ms"),
                    endMs = s.optInt("end_ms"),
                )
            }
        }.orEmpty()

        return InHouseSttResult(
            transcript = j.optString("transcript"),
            confidence = j.optDouble("confidence", Double.NaN).takeIf { !it.isNaN() },
            languageCode = j.optString("language", fallbackLang),
            durationMs = j.optLong("duration_ms"),
            modelVersion = j.optString("model_version"),
            processingTimeMs = j.optLong("processing_time_ms"),
            segments = segs,
            requestId = j.optString("request_id"),
            raw = raw,
        )
    }

    private fun mapHttpError(r: Response): SttError = when (r.code) {
        401, 403 -> SttError.Auth("HTTP ${r.code}")
        429 -> SttError.Quota("HTTP 429")
        408, 504 -> SttError.Timeout(0L)
        in 500..599 -> SttError.Network(RuntimeException("HTTP ${r.code}"))
        else -> SttError.ProviderFailure(name, message = "HTTP ${r.code}")
    }
}
