package com.baseproject.aispeech.character.lipsync

/**
 * A pre-computed mouth shape change.
 *
 * @param charPos position in the source text where the shape should appear
 *   (used to re-anchor when a [com.baseproject.aispeech.tts.TtsEvent.Range] arrives)
 * @param delayMs offset from utterance start at which to fire the change
 * @param shape   one of the values from [com.baseproject.aispeech.character.animation.AnimationContract.REQUIRED_VISEMES]
 *   without the `mouth_` prefix (e.g. `"A"`, `"rest"`)
 */
data class VisemeEvent(
    val charPos: Int,
    val delayMs: Long,
    val shape: String
)
