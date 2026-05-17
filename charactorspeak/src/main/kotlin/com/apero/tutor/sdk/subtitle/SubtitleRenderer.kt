package com.apero.tutor.sdk.subtitle

/**
 * Pluggable subtitle rendering surface.
 *
 * The SDK drives this with the same [com.apero.tutor.sdk.tts.TtsEvent] stream
 * that drives lip sync, guaranteeing that mouth movement and on-screen text
 * highlight stay in sync. Implementations decide how to present the text
 * (karaoke colouring, plain caption, translation pair, etc.).
 */
interface SubtitleRenderer {

    /** Display [text] (typically dim/unhighlighted) at the start of an utterance. */
    fun show(text: String)

    /**
     * Highlight the character range currently being spoken.
     *
     * @param start inclusive index in the most recent [show] text
     * @param end   exclusive index
     */
    fun highlight(start: Int, end: Int)

    /** Hide / clear the subtitle. */
    fun clear()
}
