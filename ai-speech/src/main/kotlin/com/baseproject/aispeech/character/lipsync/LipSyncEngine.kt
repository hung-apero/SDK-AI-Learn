package com.baseproject.aispeech.character.lipsync

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Computes and schedules viseme (mouth shape) changes synchronized with TTS audio.
 *
 * Workflow:
 *   1. [scheduleFor] builds a list of [VisemeEvent]s from raw text using a fast
 *      rule-based grapheme → viseme mapping.
 *   2. [start] anchors the schedule to the actual TTS playback start time and
 *      posts every event to the main thread via [Handler.postAtTime].
 *   3. [correctDrift] re-anchors the remaining (un-fired) events when a TTS
 *      word-boundary event arrives, eliminating accumulated drift.
 *
 * The engine never produces or plays audio — it only emits mouth shape names
 * through [onShape]. The caller (typically [com.baseproject.aispeech.character.TutorCharacter])
 * forwards each shape to the Spine controller.
 *
 * Thread-safety: all public methods MUST be called from the main thread.
 *
 * @param onShape callback invoked on the main thread with a viseme key
 *   ("rest", "A", "E", "O", "U", "M"). Pass to [com.baseproject.aispeech.character.animation.SpineController.setMouth].
 */
class LipSyncEngine(
    private val onShape: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val token   = Object()

    private var schedule: List<VisemeEvent> = emptyList()
    private var startUptime: Long = 0L

    /** Returns the latest schedule (read-only). Useful for tests. */
    val currentSchedule: List<VisemeEvent> get() = schedule

    /** Pre-compute the schedule but do NOT post anything yet. Call [start] when TTS actually begins. */
    fun scheduleFor(text: String) {
        schedule = textToSchedule(text)
        startUptime = 0L
    }

    /** Anchor the schedule to "now" and post every event. */
    fun start() {
        startUptime = SystemClock.uptimeMillis()
        apply(schedule, drift = 0L)
    }

    /**
     * Adjust pending events to match an actual audio frame position reported
     * by the TTS engine.
     *
     * @param charStart starting character index of the just-spoken range
     * @param actualMs  elapsed ms from utterance start to that range (from TTS)
     */
    fun correctDrift(charStart: Int, actualMs: Long) {
        if (startUptime == 0L) return
        val expected = schedule.firstOrNull { it.charPos >= charStart }?.delayMs ?: return
        val drift = actualMs - expected
        handler.removeCallbacksAndMessages(token)
        apply(schedule.filter { it.charPos >= charStart }, drift)
    }

    /** Cancel all pending shape changes and force mouth back to rest. */
    fun stop() {
        handler.removeCallbacksAndMessages(token)
        onShape("rest")
    }

    private fun apply(events: List<VisemeEvent>, drift: Long) {
        val now = SystemClock.uptimeMillis()
        for (e in events) {
            val fireAt = startUptime + e.delayMs + drift
            if (fireAt >= now) {
                handler.postAtTime({ onShape(e.shape) }, token, fireAt)
            }
        }
    }

    /**
     * Rule-based text → viseme schedule.
     *
     * Per-character timings:
     *   - normal char  → 65ms
     *   - space        → 100ms
     *   - comma/semi   → 150ms
     *   - sentence end → 200ms
     *
     * Shapes follow the standard Disney 6-viseme set:
     *   - vowels      → E / O / U
     *   - bilabials   → M  (p, b, m)
     *   - labiodental → M  (f, v, also closed-mouth)
     *   - default     → A
     *   - whitespace  → rest
     */
    private fun textToSchedule(text: String): List<VisemeEvent> {
        val out = ArrayList<VisemeEvent>(text.length)
        var time = 0L
        for ((i, ch) in text.lowercase().withIndex()) {
            val shape = when (ch) {
                'a', 'e', 'i', 'y' -> "E"
                'o'                -> "O"
                'u', 'w'           -> "U"
                'p', 'b', 'm'      -> "M"
                'f', 'v'           -> "M"
                ' ', ',', ';'      -> "rest"
                '.', '!', '?'      -> "rest"
                else               -> "A"
            }
            out += VisemeEvent(i, time, shape)
            time += when (ch) {
                ' '           -> 100L
                ',', ';'      -> 150L
                '.', '!', '?' -> 200L
                else          -> 65L
            }
        }
        return out
    }
}
