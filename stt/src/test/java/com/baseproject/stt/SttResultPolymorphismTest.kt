package com.baseproject.stt

import com.baseproject.stt.android.AndroidSttResult
import com.baseproject.stt.gemini.GeminiSttResult
import com.baseproject.stt.gemini.OverallScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SttResultPolymorphismTest {

    @Test fun `base SttResult reference exposes common fields`() {
        val gemini: SttResult = GeminiSttResult(
            text = "hello",
            languageCode = "en",
            overall = OverallScore(80, 80, 80, "B1"),
            words = emptyList(),
            mistakes = emptyList(),
            nativeRewrites = null,
            coachTip = null,
            nextDrill = null,
        )
        assertEquals("hello", gemini.text)
        assertTrue(gemini.isFinal)
        assertEquals("en", gemini.languageCode)
    }

    @Test fun `android result is a thin marker carrying languageCode`() {
        val a: SttResult = AndroidSttResult(text = "hi", isFinal = true, languageCode = "en-US")
        assertEquals("hi", a.text)
        assertEquals("en-US", a.languageCode)
        assertTrue(a is AndroidSttResult)
    }

    @Test fun `boxed in SttEvent_Result preserves subclass`() {
        val ev: SttEvent = SttEvent.Result(
            AndroidSttResult(text = "x", isFinal = true)
        )
        val r = (ev as SttEvent.Result).result
        assertTrue(r is AndroidSttResult)
    }
}
