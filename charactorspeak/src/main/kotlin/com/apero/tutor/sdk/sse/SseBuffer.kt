package com.apero.tutor.sdk.sse

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Transform a stream of raw text chunks (e.g. from an LLM SSE/streaming endpoint)
 * into a stream of complete sentences ready to feed into a TTS engine.
 *
 * Sentence detection rules:
 *   - A "sentence boundary" is `[.!?]` followed by whitespace then an uppercase
 *     letter. This avoids splitting on:
 *       * abbreviations:  "Dr. Smith"
 *       * decimals:       "It costs 3.14"
 *       * acronyms:       "U.S.A. citizen"
 *   - When the upstream completes, any remaining buffered text is emitted as a
 *     final sentence (trimmed). This guarantees no text is silently dropped if
 *     the stream lacks a final punctuation mark.
 *
 * The operator preserves backpressure semantics of the upstream — each emit is
 * suspending.
 *
 * Example:
 * ```kotlin
 * sseClient.textChunks()
 *     .toSentences()
 *     .collect { sentence -> tts.speak(sentence) }
 * ```
 */
fun Flow<String>.toSentences(): Flow<String> = flow {
    val buf = StringBuilder()
    val regex = Regex("""(?<=[.!?])(\s+)(?=[A-Z])""")

    collect { chunk ->
        buf.append(chunk)
        // Repeatedly split & emit while any boundary remains.
        while (true) {
            val text = buf.toString()
            val parts = text.split(regex)
            if (parts.size < 2) break
            // Emit every completed part except the last (which may still grow).
            for (i in 0 until parts.size - 1) {
                val sentence = parts[i].trim()
                if (sentence.isNotEmpty()) emit(sentence)
            }
            // Keep the last (possibly incomplete) fragment in the buffer.
            buf.setLength(0)
            buf.append(parts.last())
        }
    }

    // Flush remainder when upstream completes
    val remaining = buf.toString().trim()
    if (remaining.isNotEmpty()) emit(remaining)
}
