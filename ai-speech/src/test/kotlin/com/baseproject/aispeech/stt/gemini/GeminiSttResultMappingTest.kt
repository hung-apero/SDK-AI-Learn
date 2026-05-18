package com.baseproject.aispeech.stt.gemini

import com.baseproject.aispeech.stt.gemini.internal.CoachPayload
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiSttResultMappingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test fun `good payload maps every field`() {
        val raw = readResource("gemini-good-payload.json")
        val payload = json.decodeFromString(CoachPayload.serializer(), raw)
        val result = GeminiSttResult.fromPayload(payload)

        assertEquals("that room have a view of ocean", result.text)
        assertEquals("en", result.languageCode)
        assertEquals(75, result.overall.pronunciation)
        assertEquals("A2", result.overall.cefrEstimate)
        assertEquals(7, result.words.size)
        assertEquals(WordLevel.BAD, result.words[2].level)
        assertEquals(WordLevel.OK, result.words[6].level)
        assertEquals(2, result.mistakes.size)
        assertEquals(MistakeType.GRAMMAR, result.mistakes[0].type)
        assertNotNull(result.nativeRewrites)
        assertEquals("Does that room have an ocean view?", result.nativeRewrites?.casual)
        assertNotNull(result.coachTip)
        assertNotNull(result.nextDrill)
    }

    @Test fun `unknown enum values fall back to UNKNOWN`() {
        val raw = readResource("gemini-malformed-payload.json")
        val payload = json.decodeFromString(CoachPayload.serializer(), raw)
        val result = GeminiSttResult.fromPayload(payload)
        assertTrue(result.words.any { it.level == WordLevel.UNKNOWN })
        assertTrue(result.mistakes.any { it.type == MistakeType.UNKNOWN })
    }

    private fun readResource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing test resource: $name" }
            .readText()
}
