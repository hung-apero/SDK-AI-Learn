# Integrating the tutor character (Spine + lip-sync + subtitles)

For Gradle wiring and manifest basics, see the parent `SKILL.md`. This file focuses on the animated tutor character. It assumes you've already read `references/integrate-tts.md` — the character is driven by a `TtsProvider`.

## Public surface

Package `com.baseproject.aispeech.character`:

```kotlin
class TutorCharacter(
    spineView: SpineView,                          // from com.esotericsoftware.spine
    config: TutorCharacterConfig,
    initialTtsProvider: TtsProvider,
    subtitleRenderer: SubtitleRenderer? = null,
    scope: CoroutineScope? = null
) {
    var ttsProvider: TtsProvider
    var speechRate: Float
    suspend fun load(context: Context)
    suspend fun speak(text: String)
    suspend fun speakFromFlow(textChunks: Flow<String>)
    suspend fun setTtsProvider(context: Context, newProvider: TtsProvider)
    fun setEmotion(name: String?)
    fun triggerBlink()
    fun stop()
    fun release()
}

data class TutorCharacterConfig(
    val atlasFile: File,    // Spine .atlas — texture region map
    val jsonFile: File,     // Spine .json — skeleton/animations
    val pngFile: File       // Spine texture atlas image
)
```

Sub-systems (you usually don't touch these directly — `TutorCharacter` composes them):

- `animation.SpineController` — sets animations on tracks 0–4 (body, head, mouth, emotion, blink).
- `animation.AnimationContract` — required animation names (`idle`, `talk_loop`, `blink`) and mouth shapes (`mouth_rest`, `mouth_A/E/O/U/M`).
- `lipsync.LipSyncEngine(onShape: (String) -> Unit)` — schedules viseme changes from text and corrects drift against TTS `Range` events.
- `lipsync.VisemeEvent(charPos, delayMs, shape)` — single mouth-shape change.
- `subtitle.SubtitleRenderer` — interface for showing text + highlight ranges.
- `subtitle.KaraokeSubtitleRenderer(textView, spokenColor, currentColor, pendingColor)` — drop-in `TextView` highlighter.
- `sse.toSentences()` — `Flow<String>` → `Flow<String>` sentence splitter (used by `speakFromFlow`).

## Required Spine model layout

Your Spine artist must export an `.atlas` + `.json` + `.png` triple whose skeleton contains:

| Animation name | Track | Loop | Purpose |
|---|---|---|---|
| `idle` | 0 (body) | yes | Default standing pose |
| `talk_loop` | 0 (body) | yes | Plays while speaking |
| `blink` | 4 | no | Triggered on blink schedule + manual |
| `mouth_rest`, `mouth_A`, `mouth_E`, `mouth_O`, `mouth_U`, `mouth_M` | 2 (mouth) | no | Visemes — required |
| Emotion names (free-form) | 3 | no | Optional — pass via `setEmotion("happy")` etc. |

If the skeleton is missing a required animation, `SpineController` silently falls back to `mouth_rest` for unknown visemes and skips missing emotion/blink calls. The character will appear to "talk with a closed mouth" — first thing to check when integration looks wrong.

## Wiring

### Runtime dep

Spine is `compileOnly` in the library:

```kotlin
dependencies {
    implementation("apero-inhouse:ai-speech:0.1.0")
    implementation("com.esotericsoftware.spine:spine-android:4.2.12")
}
```

### Ship the Spine assets

Drop the three files into `app/src/main/assets/tutor/` (or wherever your asset pipeline puts them):

```
assets/
└── tutor/
    ├── tutor.atlas
    ├── tutor.json
    └── tutor.png
```

`TutorCharacterConfig` wants `java.io.File` — copy from `assets` to `cacheDir` once at first launch:

```kotlin
private fun copyAsset(ctx: Context, assetPath: String, dst: File) {
    if (dst.exists()) return
    ctx.assets.open(assetPath).use { input ->
        dst.outputStream().use { input.copyTo(it) }
    }
}

fun loadTutorConfig(ctx: Context): TutorCharacterConfig {
    val dir = File(ctx.cacheDir, "tutor").apply { mkdirs() }
    val atlas = File(dir, "tutor.atlas").also { copyAsset(ctx, "tutor/tutor.atlas", it) }
    val json  = File(dir, "tutor.json").also  { copyAsset(ctx, "tutor/tutor.json",  it) }
    val png   = File(dir, "tutor.png").also   { copyAsset(ctx, "tutor/tutor.png",   it) }
    return TutorCharacterConfig(atlas, json, png)
}
```

If your assets ship via download / Play Asset Delivery, just hand `TutorCharacter` the final on-disk paths once they're available.

## Minimal Compose recipe

`SpineView` is a regular Android `View` — wrap it in `AndroidView` for Compose:

```kotlin
@Composable
fun TutorScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var character by remember { mutableStateOf<TutorCharacter?>(null) }
    var subtitleView by remember { mutableStateOf<TextView?>(null) }

    Column(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                val spine = SpineView(context)
                val subtitle = TextView(context).also { subtitleView = it }
                val tts = AndroidTtsProvider(locale = Locale.US)

                character = TutorCharacter(
                    spineView           = spine,
                    config              = loadTutorConfig(context),
                    initialTtsProvider  = tts,
                    subtitleRenderer    = KaraokeSubtitleRenderer(subtitle),
                    scope               = scope,
                )
                scope.launch {
                    character?.load(context)
                    // character is ready — idle animation is running
                }
                spine
            }
        )

        AndroidView(factory = { subtitleView ?: TextView(it) })

        Row {
            Button(onClick = {
                scope.launch { character?.speak("Hello! Repeat after me: practice makes perfect.") }
            }) { Text("Speak") }

            Button(onClick = { character?.stop() }) { Text("Stop") }
            Button(onClick = { character?.triggerBlink() }) { Text("Blink") }
            Button(onClick = { character?.setEmotion("happy") }) { Text("😊") }
        }
    }

    DisposableEffect(character) {
        onDispose { character?.release() }
    }
}
```

### Streaming speech (LLM token stream → live talking character)

```kotlin
scope.launch {
    character?.speakFromFlow(llmTokenStream)   // splits into sentences internally
}
```

Internally this uses `Flow<String>.toSentences()` from `sse` + queued `tts.speak()` calls — each sentence appears in the subtitle and animates lipsync on its own range stream.

## How the pieces talk to each other

```
TtsProvider.events ──┐
                     │  TtsEvent.Started   → SpineController.startTalking()
                     │  TtsEvent.Range     → LipSyncEngine.correctDrift()
                     │                       SubtitleRenderer.highlight()
                     │  TtsEvent.Done      → SpineController.stopTalking()
                     │  TtsEvent.Error     → SpineController.stopTalking()
                     ▼
              TutorCharacter (orchestrator)
                     │
                     ├─ schedules visemes:  LipSyncEngine.scheduleFor(text)
                     │   then               LipSyncEngine.start() at TtsEvent.Started
                     │
                     └─ subtitle:  SubtitleRenderer.show(text)
                                   .highlight(start, end) on each Range
```

Three clocks must stay aligned: audio playback, viseme schedule, subtitle highlight. `LipSyncEngine.correctDrift(charStart, actualMs)` pulls the viseme clock toward the audio's reported timestamp on every `Range` event — that's why providers must emit `Range` events with accurate `frameMs`. If they don't (some Android voices), the mouth runs ahead or behind the audio.

## Swapping TTS provider at runtime

```kotlin
suspend fun switchToAzure(ctx: Context) {
    val azure = AzureTtsProvider(speechKey = …, region = …)
    character?.setTtsProvider(ctx, azure)
    // Old provider is shut down internally, new event flow is collected.
}
```

`setTtsProvider` is preferable to constructing a new `TutorCharacter` — it keeps the Spine view warm.

## Custom subtitle renderer

`KaraokeSubtitleRenderer` is one implementation. Roll your own for non-`TextView` UIs:

```kotlin
class ComposeSubtitleState : SubtitleRenderer {
    var text by mutableStateOf("")
        private set
    var range by mutableStateOf(0..-1)        // empty range = no highlight
        private set
    override fun show(text: String) { this.text = text; range = 0..-1 }
    override fun highlight(start: Int, end: Int) { range = start..end }
    override fun clear() { text = ""; range = 0..-1 }
}

// Compose side
val sub = remember { ComposeSubtitleState() }
val character = remember { TutorCharacter(spine, cfg, tts, subtitleRenderer = sub, scope = scope) }
Text(buildAnnotatedString {
    append(sub.text.substring(0, sub.range.first.coerceAtLeast(0)))
    withStyle(SpanStyle(background = Color.Yellow)) {
        append(sub.text.substring(
            sub.range.first.coerceAtLeast(0),
            (sub.range.last + 1).coerceAtMost(sub.text.length)
        ))
    }
    append(sub.text.substring((sub.range.last + 1).coerceAtMost(sub.text.length)))
})
```

## Gotchas

- **`load(context)` is suspending and required.** Skipping it leaves the SpineView blank — no skeleton, no animations. Always wrap `speak` in a coroutine that's preceded by `load`.
- **Missing animations = silent fallback.** If the Spine model lacks `talk_loop` or any viseme, the character keeps idling with mouth closed. Verify against `AnimationContract.REQUIRED_VISEMES` early in integration.
- **Don't recreate `TutorCharacter` per recomposition.** Hold it in `remember { … }` outside the `AndroidView` factory's reuse path; `SpineView` itself stays cached by `AndroidView` if you don't change keys.
- **`release()` is non-optional.** Spine holds native texture handles. Skipping `release()` leaks the atlas PNG every time the screen leaves.
- **`stop()` vs `release()`.** `stop()` halts current speak and resets to idle but keeps the character loaded for the next `speak`. `release()` tears down everything — use only on permanent screen exit.
- **`triggerBlink` is manual; auto-blink starts on `load`.** If you want one-shot character expressions during a non-speaking pause, use this.
- **`setEmotion(null)` clears the emotion track.** Pass the emotion name as configured in the Spine skeleton; unknown names are silently ignored.
- **Subtitle highlight requires `Range` events** — the Android system TTS emits these via `UtteranceProgressListener.onRangeStart`, but support varies by device. Test on the lowest-end target device before assuming karaoke works everywhere.
- **`speakFromFlow` buffers full sentences before speaking** — the character won't start talking on the first token. That's by design (it sounds robotic to speak word-by-word) but you may need to surface a "thinking" state in your UI.
- **No Compose `Modifier` for SpineView size.** Use the `AndroidView` modifier (`Modifier.weight`, `.aspectRatio`, fixed `.size`) — sizing the view from inside Spine itself is fragile.

## Pairing with STT

Conversation-style screens that listen + speak: drive `TutorCharacter` from the TTS side and use a separate `SpeechToTextProvider` to capture the user's turn. See `references/integrate-stt.md`. The two pipelines don't share state — coordinate them in your screen's ViewModel/state.
