# Integrating STT (Speech-to-Text)

For Gradle wiring and manifest basics, see the parent `SKILL.md`. This file focuses on the STT capability only.

## Public surface

Package `com.baseproject.aispeech.stt`:

```kotlin
interface SpeechToTextProvider {
    val events: Flow<SttEvent>
    val isListening: Boolean
    suspend fun prepare(context: Context)
    fun startListening()
    fun stopListening()
    fun cancel()
    fun destroy()
    fun isAvailable(): Boolean
}

sealed interface SttEvent {
    data class Result(val result: SttResult) : SttEvent
    data class PartialResult(val result: SttResult) : SttEvent
    data object ReadyForSpeech : SttEvent
    data object BeginningOfSpeech : SttEvent
    data object EndOfSpeech : SttEvent
    data class Error(val code: SttErrorCode, val message: String) : SttEvent
}

enum class SttErrorCode {
    NETWORK, AUDIO, SERVER, NO_MATCH,
    RECOGNIZER_BUSY, INSUFFICIENT_PERMISSIONS, UNKNOWN
}

open class SttResult(
    val text: String,
    val isFinal: Boolean = true,
    val languageCode: String? = null
)
```

Providers:

- `AndroidSpeechToTextProvider(context, locale = Locale.US, partialResults = true)` — wraps `android.speech.SpeechRecognizer`.
- `GeminiSpeechToTextProvider(apiKey, model = "models/gemini-2.5-flash-lite", locale = Locale.US, nativeLocale = Locale("vi"), systemPrompt = DEFAULT_COACH_PROMPT)` — records audio, ships to Gemini, returns a `GeminiSttResult` (subclass of `SttResult`) with extra coaching fields.

## Decision: which provider

| | `AndroidSpeechToTextProvider` | `GeminiSpeechToTextProvider` |
|---|---|---|
| Cost | Free | Per-request Gemini billing |
| Offline | Yes (on most devices) | No (HTTPS required) |
| Latency | Low (~200–500 ms partial) | Higher (single shot after `stopListening`) |
| Partial results | Yes (live transcript) | No (only final) |
| Locales | System-supported list | Any Gemini-supported language |
| Output | Plain transcript | Transcript + structured coaching JSON (when paired with pronunciation) |
| Use when | Phrase capture, dictation, drills | Pronunciation coaching, multilingual users, when you want both transcript + scoring in one call |

For most "capture what the user said" UX, default to `AndroidSpeechToTextProvider`. Pick Gemini when you want the coach-style structured response or when the device's speech recognizer is unreliable for your locale.

## Wiring

### Manifest (required for either provider)

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />

<queries>
    <intent>
        <action android:name="android.speech.RecognitionService" />
    </intent>
</queries>
```

The `<queries>` block is mandatory on Android 11+ so `AndroidSpeechToTextProvider` can see the system recognizer service.

### Runtime permission

Request `RECORD_AUDIO` before the first `startListening()` — both providers will emit `SttEvent.Error(INSUFFICIENT_PERMISSIONS, …)` otherwise.

```kotlin
val launcher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
    if (granted) startStt() else showMicDenied()
}
LaunchedEffect(Unit) {
    if (ContextCompat.checkSelfPermission(ctx, RECORD_AUDIO) != PERMISSION_GRANTED) {
        launcher.launch(RECORD_AUDIO)
    }
}
```

### Provider config (Gemini)

API key + locales come from your build's `local.properties` or remote config — never hard-code:

```kotlin
val gemini = GeminiSpeechToTextProvider(
    apiKey       = BuildConfig.GEMINI_API_KEY,
    model        = "models/gemini-2.5-flash-lite",
    locale       = Locale.US,                 // target language being practiced
    nativeLocale = Locale("vi"),              // learner's L1 — used by coach prompt
)
```

## Minimal Compose recipe

```kotlin
@Composable
fun DictationScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val provider: SpeechToTextProvider = remember {
        AndroidSpeechToTextProvider(ctx, locale = Locale.US)
    }
    DisposableEffect(provider) { onDispose { provider.destroy() } }

    var partial by remember { mutableStateOf("") }
    var final   by remember { mutableStateOf("") }
    var error   by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(provider) {
        provider.prepare(ctx)
        provider.events.collect { evt ->
            when (evt) {
                is SttEvent.PartialResult -> partial = evt.result.text
                is SttEvent.Result        -> { final = evt.result.text; partial = "" }
                is SttEvent.Error         -> error = "${evt.code}: ${evt.message}"
                SttEvent.ReadyForSpeech,
                SttEvent.BeginningOfSpeech,
                SttEvent.EndOfSpeech      -> { /* drive UI states (idle / listening / processing) */ }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Heard: $final", style = MaterialTheme.typography.headlineSmall)
        Text(partial, color = MaterialTheme.colorScheme.outline)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(onClick = {
            if (provider.isListening) provider.stopListening()
            else provider.startListening()
        }) {
            Text(if (provider.isListening) "Stop" else "Record")
        }
    }
}
```

Notes on the recipe:

- **Always `destroy()` on dispose.** The Android `SpeechRecognizer` is a system service; leaks keep the mic indicator on between screens.
- **Cancel vs stop.** `stopListening()` asks the engine to produce a final result for the audio captured so far. `cancel()` aborts without emitting a `Result`. Use `cancel()` when the user backs out of a screen mid-recording.
- **`isListening` is a snapshot.** Don't drive UI state straight off it (the field updates after the event flow). Track listening state from `BeginningOfSpeech` / `EndOfSpeech` / `Result` / `Error` instead.

## Hot-swapping providers

Both providers implement the same interface — sample apps usually keep a factory:

```kotlin
enum class SttProviderKind { ANDROID, GEMINI }

object SttProviderFactory {
    fun create(context: Context, kind: SttProviderKind): SpeechToTextProvider = when (kind) {
        SttProviderKind.ANDROID -> AndroidSpeechToTextProvider(context)
        SttProviderKind.GEMINI  -> GeminiSpeechToTextProvider(apiKey = BuildConfig.GEMINI_API_KEY)
    }
}
```

Swap at runtime: `destroy()` the old, `prepare()` the new, re-collect `events`.

## Gotchas

- **`prepare(context)` is required and suspending.** Calling `startListening()` before `prepare` returns is a silent no-op for Android, and an immediate `Error(UNKNOWN, …)` for Gemini.
- **`Result.text` may be empty.** Android emits `Error(NO_MATCH, …)` for "I heard nothing"; Gemini returns an empty string with `isFinal = true`. Guard your downstream (e.g. pronunciation scorer) against the empty case.
- **Locale must match the device's installed STT pack** for `AndroidSpeechToTextProvider`. Unsupported locales fall back to system default and silently produce wrong-language transcripts.
- **Gemini provider buffers full audio.** It's not streaming — there are no `PartialResult` events. If you need live partials, use the Android provider.
- **`RECOGNIZER_BUSY` means another app holds the mic** (often your own app's previous instance leaked). Call `destroy()` on screen leave; if it still recurs, double-check you're not building a new provider on every recomposition.
- **`INSUFFICIENT_PERMISSIONS` is post-runtime-request.** The error fires when you call `startListening()` after the user denied or revoked `RECORD_AUDIO`. Check the permission state before starting, not just at app launch.
- **Stop the engine before the screen exits.** Don't rely on garbage collection — call `destroy()` from `onDispose` / `onDestroy`.

## Pairing with pronunciation scoring

The final transcript (`SttEvent.Result.result.text`) is exactly what `PronunciationAssessor.assess(reference, spoken)` wants. See `references/integrate-pronunciation.md` for the wired-up screen.
