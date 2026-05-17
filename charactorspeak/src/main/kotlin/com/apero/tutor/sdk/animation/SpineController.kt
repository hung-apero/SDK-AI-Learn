package com.apero.tutor.sdk.animation

import android.os.Handler
import android.os.Looper
import com.apero.tutor.sdk.animation.AnimationContract.ANIM_BLINK
import com.apero.tutor.sdk.animation.AnimationContract.ANIM_IDLE
import com.apero.tutor.sdk.animation.AnimationContract.ANIM_TALK_LOOP
import com.apero.tutor.sdk.animation.AnimationContract.REQUIRED_VISEMES
import com.apero.tutor.sdk.animation.AnimationContract.TRACK_BLINK
import com.apero.tutor.sdk.animation.AnimationContract.TRACK_BODY
import com.apero.tutor.sdk.animation.AnimationContract.TRACK_EMOTION
import com.apero.tutor.sdk.animation.AnimationContract.TRACK_HEAD
import com.apero.tutor.sdk.animation.AnimationContract.TRACK_MOUTH
import com.esotericsoftware.spine.android.AndroidSkeletonDrawable
import kotlin.random.Random

/**
 * Wraps the 5-track Spine animation state used by the SDK.
 *
 * Each track has a fixed semantic role — see [AnimationContract]. The controller
 * is intentionally minimal: it doesn't know about TTS or text; callers drive it
 * with high-level verbs ([startTalking], [setMouth], [setEmotion], etc.).
 *
 * @param drawable the underlying Spine skeleton drawable obtained from the SpineView
 *   after the skeleton has finished loading
 * @param defaultMix global animation blend duration in seconds. 0.2 is a balanced
 *   default — long enough for body/head transitions to look smooth, short enough
 *   to not "smear" rapid talk-loop movement
 * @param visemeMix faster blend used between mouth viseme changes. Lip sync needs
 *   crisp switches; 0.05 (50ms) keeps mouth shapes readable at speech rate
 */
class SpineController(
    private val drawable: AndroidSkeletonDrawable,
    private val defaultMix: Float = 0.2f,
    private val visemeMix: Float = 0.05f
) {
    private val handler = Handler(Looper.getMainLooper())
    private var blinkScheduled = false

    init {
        drawable.animationStateData.defaultMix = defaultMix
        // Set fast crossfade for every viseme→viseme transition
        for (a in REQUIRED_VISEMES) for (b in REQUIRED_VISEMES) {
            if (a != b) drawable.animationStateData.setMix(a, b, visemeMix)
        }
    }

    // ---- Public API ----------------------------------------------------------

    /** Set the body idle loop. Call once after the skeleton is ready. */
    fun startIdle() = setAnimation(TRACK_BODY, ANIM_IDLE, loop = true)

    /** Start head-bob animation (call when TTS starts speaking). */
    fun startTalking() = setAnimation(TRACK_HEAD, ANIM_TALK_LOOP, loop = true)

    /** Clear the head-bob (call when TTS finishes or errors). */
    fun stopTalking() = clearTrack(TRACK_HEAD)

    /**
     * Swap mouth shape. Accepts shape *suffix* without the `mouth_` prefix,
     * e.g. `"A"`, `"rest"`. Combined with the viseme cross-fade configured in
     * the constructor, calls can fire at speech rate without visual snapping.
     */
    fun setMouth(shape: String) {
        val name = if (shape.startsWith("mouth_")) shape else "mouth_$shape"
        setAnimation(TRACK_MOUTH, name, loop = false)
    }

    /**
     * Apply an emotion animation. Pass `null` to clear. If [name] isn't present
     * in the skeleton this call is silently ignored — emotions are optional per
     * the contract.
     */
    fun setEmotion(name: String?) {
        if (name == null) {
            clearTrack(TRACK_EMOTION, mixDuration = 0.5f)
            return
        }
        if (!hasAnimation(name)) return
        setAnimation(TRACK_EMOTION, name, loop = false)
    }

    /** Play a single blink. */
    fun blinkOnce() = setAnimation(TRACK_BLINK, ANIM_BLINK, loop = false)

    /**
     * Start randomized auto-blink every 3–6s. Idempotent: subsequent calls do
     * nothing while already scheduled. Use [stopAutoBlink] to cancel.
     */
    fun startAutoBlink() {
        if (blinkScheduled) return
        blinkScheduled = true
        scheduleNextBlink()
    }

    fun stopAutoBlink() {
        blinkScheduled = false
        handler.removeCallbacksAndMessages(BLINK_TOKEN)
    }

    /** Release scheduled work. The drawable itself is owned by the SpineView. */
    fun release() {
        stopAutoBlink()
        handler.removeCallbacksAndMessages(null)
    }

    // ---- Internals -----------------------------------------------------------

    private fun setAnimation(track: Int, name: String, loop: Boolean) {
        drawable.animationState.setAnimation(track, name, loop)
    }

    private fun clearTrack(track: Int, mixDuration: Float = 0.3f) {
        drawable.animationState.setEmptyAnimation(track, mixDuration)
    }

    private fun hasAnimation(name: String): Boolean =
        drawable.skeletonData.findAnimation(name) != null

    private fun scheduleNextBlink() {
        if (!blinkScheduled) return
        handler.postAtTime(
            { blinkOnce(); scheduleNextBlink() },
            BLINK_TOKEN,
            android.os.SystemClock.uptimeMillis() + Random.nextLong(3000L, 6000L)
        )
    }

    private companion object {
        val BLINK_TOKEN = Any()
    }
}
