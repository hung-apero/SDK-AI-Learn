package com.baseproject.stt.gemini

import com.baseproject.stt.SttErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiProviderErrorMappingTest {

    private val provider = GeminiSpeechToTextProvider(apiKey = "x")

    @Test fun `http 401 maps to SERVER`() {
        assertEquals(SttErrorCode.SERVER, provider.mapError(Throwable("HTTP 401: bad key")))
    }

    @Test fun `http 500 maps to SERVER`() {
        assertEquals(SttErrorCode.SERVER, provider.mapError(Throwable("HTTP 500: internal")))
    }

    @Test fun `timeout maps to NETWORK`() {
        assertEquals(SttErrorCode.NETWORK, provider.mapError(Throwable("read timeout")))
    }

    @Test fun `dns failure maps to NETWORK`() {
        assertEquals(SttErrorCode.NETWORK, provider.mapError(Throwable("Unable to resolve host")))
    }

    @Test fun `unknown failure maps to UNKNOWN`() {
        assertEquals(SttErrorCode.UNKNOWN, provider.mapError(Throwable("some other failure")))
    }
}
