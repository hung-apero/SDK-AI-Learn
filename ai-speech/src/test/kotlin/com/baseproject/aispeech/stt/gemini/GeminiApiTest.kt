package com.baseproject.aispeech.stt.gemini

import com.baseproject.aispeech.stt.gemini.internal.GeminiApi
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GeminiApiTest {

    private lateinit var server: MockWebServer
    private val json = Json { encodeDefaults = true }

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `200 response with inner JSON decodes`() {
        val inner = javaClass.classLoader!!.getResource("gemini-good-payload.json")!!.readText()
        val innerEncoded = json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(inner))
        val outer = """{"candidates":[{"content":{"parts":[{"text":$innerEncoded}]}}]}"""
        server.enqueue(MockResponse().setBody(outer).setResponseCode(200))

        val api = newApi()
        val tmp = File.createTempFile("audio", ".m4a").also { it.writeBytes(byteArrayOf(0, 1, 2, 3)) }
        try {
            val payload = api.generateContent(tmp, "audio/mp4")
            assertEquals("that room have a view of ocean", payload.transcript)
            assertEquals("en", payload.detectedLanguage)
        } finally {
            tmp.delete()
        }
    }

    @Test fun `non-2xx surfaces as IllegalStateException`() {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}""")
        )
        val api = newApi()
        val tmp = File.createTempFile("audio", ".m4a").also { it.writeBytes(byteArrayOf(0)) }
        try {
            api.generateContent(tmp, "audio/mp4")
            fail("expected IllegalStateException for 401 response")
        } catch (e: IllegalStateException) {
            assertEquals(true, e.message?.startsWith("HTTP 401"))
        } finally {
            tmp.delete()
        }
    }

    private fun newApi(): GeminiApi = GeminiApi(
        apiKey = "test-key",
        model = "test-model",
        systemPrompt = "test prompt",
        baseUrl = server.url("/v1beta").toString().removeSuffix("/"),
    )
}
