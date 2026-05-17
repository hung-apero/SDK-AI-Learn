package com.apero.tutor.sdk.animation

/**
 * The Spine skeleton consumed by the SDK MUST provide all of these animations.
 *
 * Track usage convention used by [SpineController]:
 *   - Track 0 (BODY)    → [ANIM_IDLE] looping body breathing
 *   - Track 1 (HEAD)    → [ANIM_TALK_LOOP] while audio is playing
 *   - Track 2 (MOUTH)   → one of [REQUIRED_VISEMES], swapped per TTS range event
 *   - Track 3 (EMOTION) → optional named emotions (e.g. "happy"), via [SpineController.setEmotion]
 *   - Track 4 (BLINK)   → [ANIM_BLINK] one-shot
 *
 * If an emotion animation isn't present in the skeleton the call is silently no-op'd.
 */
object AnimationContract {
    const val ANIM_IDLE      = "idle"
    const val ANIM_TALK_LOOP = "talk_loop"
    const val ANIM_BLINK     = "blink"

    val REQUIRED_VISEMES = listOf(
        "mouth_rest", "mouth_A", "mouth_E", "mouth_O", "mouth_U", "mouth_M"
    )

    const val TRACK_BODY    = 0
    const val TRACK_HEAD    = 1
    const val TRACK_MOUTH   = 2
    const val TRACK_EMOTION = 3
    const val TRACK_BLINK   = 4
}
