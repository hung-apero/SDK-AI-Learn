# Integrating TTS (Text-to-Speech)

For Gradle wiring and manifest basics, see the parent `SKILL.md`. This file focuses on the TTS capability only.

## Public surface

Package `com.baseproject.aispeech.tts`:

```kotlin
interface TtsProvider {
    val events: Flow<TtsEvent>
    var speechRate: Float                 // 0.5f .. 2.0f typical
    suspend fun prepare(context: Context)
    fun speak(text: String, utteranceId: String)
    fun stop()
    fun shutdown()
}

sealed class TtsEvent {
    data class Started(val uttId: String) : TtsEvent()
    data class Range(val uttId: String, val start: Int, val end: Int, val frameMs: Long) : TtsEvent()
    data class Done(val uttId: String) : TtsEvent()
    data class Error(val uttId: String, val message: String) : TtsEvent()
}
```

`Range` events stream char positions within the spoken text as audio plays — that's what drives lipsync and karaoke subtitles in `TutorCharacter`.

Providers:

- `AndroidTtsProvider(locale = Locale.US, sampleRateHz = 22050, initialVoiceName = null, initialSpeechRate = 1.0f)`
- `AzureTtsProvider(speechKey, region, voiceName = "en-US-JennyNeural", initialSpeechRate = 1.0f)`
- `ElevenLabsTtsProvider(apiKey, voiceId, modelId = "eleven_flash_v2_5", audioLatencyMs = 80L, initialSpeechRate = 1.0f)`

## Decision: which provider

| | `AndroidTtsProvider` | `AzureTtsProvider` | `ElevenLabsTtsProvider` |
|---|---|---|---|
| Cost | Free | Per-character (Azure billing) | Per-character (ElevenLabs billing) |
| Offline | Yes (system voices) | No | No |
| Quality | OK (system voice quality) | Very good (Neural voices) | Best (especially `eleven_flash_v2_5` for low latency) |
| Latency | Low | Medium (~300–600 ms TTFB) | Very low (`audioLatencyMs` ≈ 80) |
| Voice variety | Limited to device | Hundreds of Neural voices | Custom / cloned voices |
| Extra runtime dep | None | `com.microsoft.cognitiveservices.speech:client-sdk:1.44.0` | None (uses OkHttp) |
| minSdk bump | No | **Yes → 26** (transitive azure-core) | No |
| Best for | Dev mode, accessibility, offline fallback | Production with stable Azure account | Premium UX, voice cloning, lowest latency |

Most apps wire all three behind a runtime switch — see "Hot-swapping" below.

## Wiring

### Per-provider runtime deps

Only add the runtime dep for providers you actually use. The library declares both Azure SDK and Spine as `compileOnly` so apps that don't use a provider don't pay the size cost.

```kotlin
dependencies {
    implementation("apero-inhouse:ai-speech:0.1.0")

    // Only if using AzureTtsProvider:
    implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.44.0")
}
```

### Azure: minSdk bump + ABI filter

Azure's transitive `azure-core` uses Java 9+ APIs that can't be desugared for minSdk < 26, and its native libs ship for many architectures.

```kotlin
android {
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += listOf("x86_64", "arm64-v8a") }
    }
    packaging {
        resources.excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        jniLibs { useLegacyPackaging = false }   // required for Android 15+ 16KB pages
    }
}
```

### Provider config

```kotlin
val android = AndroidTtsProvider(
    locale = Locale.US,
    initialVoiceName = null,            // set after `prepare`, see availableVoices()
    initialSpeechRate = 1.0f
)

val azure = AzureTtsProvider(
    speechKey = BuildConfig.AZURE_SPEECH_KEY,
    region    = BuildConfig.AZURE_SPEECH_REGION,
    voiceName = BuildConfig.AZURE_SPEECH_VOICE,    // e.g. "en-US-AriaNeural"
)

val eleven = ElevenLabsTtsProvider(
    apiKey  = BuildConfig.ELEVENLABS_API_KEY,
    voiceId = BuildConfig.ELEVENLABS_VOICE_ID,     // e.g. "21m00Tcm4TlvDq8ikWAM"
    modelId = "eleven_flash_v2_5",
)
```

Never hard-code keys — wire them through `local.properties` → `buildConfigField`, or remote config.

## Minimal recipe

```kotlin
@Composable
fun SpeakScreen() {
    val ctx = LocalContext.current

    val tts: TtsProvider = remember { AndroidTtsProvider() }
    DisposableEffect(tts) { onDispose { tts.shutdown() } }

    LaunchedEffect(tts) {
        tts.prepare(ctx)
        tts.events.collect { evt ->
            when (evt) {
                is TtsEvent.Started -> { /* show "speaking" indicator */ }
                is TtsEvent.Range   -> { /* highlight chars start..end for karaoke */ }
                is TtsEvent.Done    -> { /* clear indicator */ }
                is TtsEvent.Error   -> { /* surface evt.message */ }
            }
        }
    }

    var phrase by remember { mutableStateOf("Hello from ai-speech.") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextField(value = phrase, onValueChange = { phrase = it })
        Button(onClick = { tts.speak(phrase, utteranceId = "phrase-${System.currentTimeMillis()}") }) {
            Text("Speak")
        }
        Button(onClick = { tts.stop() }) { Text("Stop") }
    }
}
```

## Streaming long text

`speak(text, utteranceId)` is one utterance. For paragraph-length input where you want speech to start as soon as the first sentence is ready (e.g. LLM token stream):

```kotlin
import com.baseproject.aispeech.character.sse.toSentences

llmTokenStream
    .toSentences()             // splits at `.!?` + uppercase boundary
    .collectIndexed { i, sentence ->
        tts.speak(sentence, utteranceId = "stream-$i")
    }
```

`toSentences()` is an extension on `Flow<String>` exposed for this use — same buffer that powers TutorCharacter's streaming speak. Don't reinvent it.

## Voice selection (Android only)

Android voice list is device-specific and only available after `prepare(context)`:

```kotlin
val android = AndroidTtsProvider()
android.prepare(ctx)
val voices = android.availableVoices()    // List<android.speech.tts.Voice>
android.voiceName = voices.firstOrNull { it.locale == Locale.US && !it.isNetworkConnectionRequired }?.name
```

Azure / ElevenLabs voices are server-side — set `voiceName` / `voiceId` in the constructor.

## Speech rate

`speechRate` is read/write on all providers, applied to the next `speak()`. Typical range `0.5f` (half speed) .. `2.0f` (double). Provider-specific clamps apply silently.

```kotlin
tts.speechRate = 0.8f       // slower — good for L2 learners
tts.speak(target, utteranceId = "slow-1")
```

## Hot-swapping providers

```kotlin
suspend fun swap(ctx: Context, current: TtsProvider, new: TtsProvider): TtsProvider {
    current.stop()
    current.shutdown()
    new.prepare(ctx)
    return new
}
```

If the swap happens mid-screen, also re-collect `new.events` — the old `events` Flow is now defunct.

## Gotchas

- **`prepare(context)` is suspending and required.** Calling `speak` before it completes is a silent no-op (Android), `Error` (Azure / ElevenLabs).
- **`shutdown()` matters.** Android's TTS engine and OkHttp's connection pool both leak across screens if you skip `shutdown()`. Call it from `onDispose` / `onDestroy`.
- **`utteranceId` should be unique per call.** It's how `Started` / `Range` / `Done` / `Error` events identify which utterance they belong to — colliding IDs corrupt range-tracking UI.
- **Azure crashes the app if you forget the runtime dep.** `ClassNotFoundException: com.microsoft.cognitiveservices.speech.SpeechSynthesizer` fires the instant `AzureTtsProvider` is instantiated. Add the dep when wiring the provider, not later.
- **ElevenLabs `audioLatencyMs` is a hint, not a guarantee.** Lowering it past ~50 ms loses you some quality features (compression) — measure on real devices before going too low.
- **Range events fire only for in-character audio.** Punctuation and pauses don't emit `Range`, so karaoke advance must be tolerant of gaps. Don't assume monotonically incrementing ranges across the whole utterance.
- **Speech rate is applied to the NEXT speak call.** Setting it mid-utterance does not pitch-shift the currently playing audio.
- **Stopping mid-utterance.** `stop()` ends current audio but does NOT emit `Done` for the partial utterance. If your UI clears state on `Done`, also clear it on `stop()` directly.

## Pairing with the tutor character

`TutorCharacter` takes a `TtsProvider` in its constructor and consumes the `events` flow to drive lipsync and karaoke subtitles. See `references/integrate-character-speaking.md`.
