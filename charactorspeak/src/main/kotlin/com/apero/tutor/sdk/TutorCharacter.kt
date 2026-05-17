package com.apero.tutor.sdk

import android.content.Context
import com.apero.tutor.sdk.animation.SpineController
import com.apero.tutor.sdk.lipsync.LipSyncEngine
import com.apero.tutor.sdk.sse.toSentences
import com.apero.tutor.sdk.subtitle.SubtitleRenderer
import com.apero.tutor.sdk.tts.TtsEvent
import com.apero.tutor.sdk.tts.TtsProvider
import com.esotericsoftware.spine.android.SpineController as SpineSdkController
import com.esotericsoftware.spine.android.SpineView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Public entry point for the Tutor SDK.
 *
 * Wires four collaborators around a single source of truth — the [TtsProvider]
 * event stream — so that lip sync, subtitle highlight, and audio playback never
 * drift apart:
 *
 * ```
 *   TtsEvent.Started → start LipSyncEngine + show subtitle + head bob
 *   TtsEvent.Range   → correct lip drift + highlight word
 *   TtsEvent.Done    → stop head bob + clear subtitle (when queue empty)
 *   TtsEvent.Error   → log + stop head bob
 * ```
 *
 * Threading: must be constructed and used from the main thread. Internal work
 * (TTS callbacks, drift correction) is dispatched to the main thread.
 *
 * Lifecycle: call [load] once before [speak] / [speakFromFlow]. Call [release]
 * when the host view/activity is destroyed.
 *
 * @param spineView host SpineView from the app's layout
 * @param config local file locations for skeleton + atlas + texture
 * @param ttsProvider any [TtsProvider] implementation
 * @param subtitleRenderer optional. Pass `null` if no on-screen subtitle is wanted
 * @param scope coroutine scope to collect TTS events. Defaults to an internal
 *   [SupervisorJob] scope that is cancelled on [release]. Pass `lifecycleScope`
 *   to tie collection to the host activity automatically.
 */
class TutorCharacter(
    private val spineView: SpineView,
    private val config: TutorCharacterConfig,
    initialTtsProvider: TtsProvider,
    private val subtitleRenderer: SubtitleRenderer? = null,
    scope: CoroutineScope? = null
) {
    private val internalJob = SupervisorJob()
    private val scope: CoroutineScope = scope ?: CoroutineScope(Dispatchers.Main + internalJob)
    private val ownsScope: Boolean = scope == null

    /** Currently-active TTS provider. Switch at runtime via [setTtsProvider]. */
    var ttsProvider: TtsProvider = initialTtsProvider
        private set

    /**
     * Speech rate multiplier (1.0 = normal). Persists across [setTtsProvider]
     * calls so swapping providers doesn't reset to default speed.
     */
    var speechRate: Float
        get() = ttsProvider.speechRate
        set(value) {
            val clamped = value.coerceIn(0.5f, 2.0f)
            _speechRate = clamped
            ttsProvider.speechRate = clamped
        }
    private var _speechRate: Float = initialTtsProvider.speechRate

    private var spine: SpineController? = null
    private var lipsync: LipSyncEngine? = null

    private val skeletonReady = AtomicBoolean(false)
    private val uttCounter    = AtomicInteger(0)
    private val pendingCount  = AtomicInteger(0)
    private val utteranceText = HashMap<String, String>()

    private var eventsJob: Job? = null

    // ---- Lifecycle -----------------------------------------------------------

    /**
     * Resolve files, attach SpineView controller, initialise TTS, and start
     * collecting TTS events. Safe to call multiple times — only the first
     * invocation does work.
     */
    suspend fun load(context: Context) {
        require(config.atlasFile.exists()) { "atlas file missing: ${config.atlasFile}" }
        require(config.jsonFile.exists())  { "json file missing: ${config.jsonFile}" }
        require(config.pngFile.exists())   { "png file missing: ${config.pngFile}" }

        if (eventsJob == null) {
            eventsJob = scope.launch { collectTtsEvents(ttsProvider.events) }
        }
        ttsProvider.prepare(context)

        val ready = CompletableDeferred<Unit>()
        withContext(Dispatchers.Main) {
            spineView.controller = SpineSdkController { sdkCtrl ->
                if (skeletonReady.getAndSet(true)) return@SpineSdkController
                spine = SpineController(sdkCtrl.drawable).also {
                    it.startIdle()
                    it.startAutoBlink()
                }
                lipsync = LipSyncEngine { shape ->
                    spine?.setMouth(shape)
                }
                ready.complete(Unit)
            }
            spineView.loadFromFile(config.atlasFile, config.jsonFile)
        }
        ready.await()
    }

    /** Queue a single utterance. Suspends only to switch to main thread. */
    suspend fun speak(text: String) = withContext(Dispatchers.Main) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext
        val id = "utt_${uttCounter.incrementAndGet()}"
        utteranceText[id] = trimmed
        pendingCount.incrementAndGet()
        ttsProvider.speak(trimmed, id)
    }

    /**
     * Consume an upstream stream of raw text chunks (e.g. SSE), split into
     * sentences, and speak each one. Suspends until the upstream completes.
     */
    suspend fun speakFromFlow(textChunks: Flow<String>) {
        textChunks.toSentences().onEach { speak(it) }.collect { /* drain */ }
    }

    /**
     * Hot-swap the TTS provider while keeping Spine, lip sync, and subtitle
     * collaborators alive. Cancels any in-flight speech and resets the queue
     * before attaching the new provider.
     *
     * The new provider's [TtsProvider.prepare] is invoked with [context].
     */
    suspend fun setTtsProvider(context: Context, newProvider: TtsProvider) {
        // 1. Tear down the current provider + its event collection
        stop()
        ttsProvider.shutdown()
        eventsJob?.cancel()
        eventsJob = null

        // 2. Attach the new provider
        ttsProvider = newProvider
        newProvider.prepare(context)
        // Propagate the user-selected rate to the freshly-attached provider
        newProvider.speechRate = _speechRate
        eventsJob = scope.launch { collectTtsEvents(newProvider.events) }
    }

    /** Set or clear a named emotion (e.g. `"happy"`). No-op if missing. */
    fun setEmotion(name: String?) { spine?.setEmotion(name) }

    /** Trigger a manual blink. */
    fun triggerBlink() { spine?.blinkOnce() }

    /** Stop current speech and pending queue without releasing resources. */
    fun stop() {
        ttsProvider.stop()
        lipsync?.stop()
        spine?.stopTalking()
        subtitleRenderer?.clear()
        utteranceText.clear()
        pendingCount.set(0)
    }

    /** Release every collaborator. Use when the host view is destroyed. */
    fun release() {
        stop()
        spine?.release()
        ttsProvider.shutdown()
        eventsJob?.cancel()
        eventsJob = null
        if (ownsScope) internalJob.cancel()
    }

    // ---- TTS event wiring ----------------------------------------------------

    /**
     * Single point that maps [TtsEvent]s to side-effects on the four collaborators.
     * Runs on the main dispatcher (see scope construction).
     */
    private suspend fun collectTtsEvents(events: Flow<TtsEvent>) {
        events.collect { ev ->
            when (ev) {
                is TtsEvent.Started -> onStarted(ev.uttId)
                is TtsEvent.Range   -> onRange(ev.start, ev.end, ev.frameMs)
                is TtsEvent.Done    -> onDone(ev.uttId)
                is TtsEvent.Error   -> onError(ev.uttId)
            }
        }
    }

    private fun onStarted(uttId: String) {
        val text = utteranceText[uttId] ?: return
        lipsync?.scheduleFor(text)
        subtitleRenderer?.show(text)
        spine?.startTalking()
        lipsync?.start()
    }

    private fun onRange(start: Int, end: Int, frameMs: Long) {
        subtitleRenderer?.highlight(start, end)
        lipsync?.correctDrift(start, frameMs)
    }

    private fun onDone(uttId: String) {
        utteranceText.remove(uttId)
        // Only fully reset when the entire queue is drained — otherwise the next
        // queued utterance is about to start and we'd flash an empty state.
        if (pendingCount.decrementAndGet() <= 0) {
            spine?.stopTalking()
            lipsync?.stop()
            subtitleRenderer?.clear()
        }
    }

    private fun onError(uttId: String) {
        utteranceText.remove(uttId)
        pendingCount.decrementAndGet()
        spine?.stopTalking()
        lipsync?.stop()
    }
}
