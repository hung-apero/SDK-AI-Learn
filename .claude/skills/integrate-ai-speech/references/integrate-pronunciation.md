# Integrating pronunciation scoring

For Gradle wiring and manifest basics, see the parent `SKILL.md`. This file focuses on pronunciation scoring. The library is STT-agnostic — pair this with `references/integrate-stt.md` for the full "speak the phrase, get a score" flow.

## Public surface

Package `com.baseproject.aispeech.pronunciation`:

```kotlin
abstract class PronunciationAssessor {
    abstract fun assess(
        referenceText: String,
        spokenText: String,
        locale: Locale = Locale.US
    ): PronunciationResult

    protected fun normalizeText(text: String): List<String>
}

data class PronunciationResult(
    val score: Float,                          // 0.0 .. 100.0
    val level: PronunciationLevel,
    val matchedWords: List<String>,
    val unmatchedWords: List<String>,
    val wordDetails: List<WordDetail>          // one per reference token, original order
)

data class WordDetail(
    val word: String,
    val isMatched: Boolean
)

enum class PronunciationLevel(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    TRY_AGAIN("Try Again");

    companion object {
        fun fromScore(score: Float): PronunciationLevel
    }
}
```

Sub-package `pronunciation.simple`:

```kotlin
class SimpleWordMatchAssessor : PronunciationAssessor() {
    override fun assess(
        referenceText: String,
        spokenText: String,
        locale: Locale
    ): PronunciationResult
}
```

That's the entire surface. No Compose UI ships — the library deliberately leaves rendering to the host so it stays UI-framework-agnostic.

## Decision: which assessor

There's only one production-ready assessor right now (`SimpleWordMatchAssessor`). The `PronunciationAssessor` base class exists so you (or a future provider) can plug in a richer one — e.g. Gemini coaching responses, Azure Pronunciation Assessment, phonetic-distance scoring.

| | `SimpleWordMatchAssessor` | Custom subclass |
|---|---|---|
| What it scores | Word-level matches between reference and spoken text | Whatever you implement |
| Score formula | `matchedCount / referenceTokenCount * 100` | Your call |
| Use when | MVP drills, vocabulary practice, simple repeat-after-me | Phoneme accuracy, prosody, intonation, native-feedback flows |

Subclass `PronunciationAssessor` and reuse the `normalizeText(text)` helper for consistent tokenization across implementations.

## Wiring

No extra deps — assessor is pure Kotlin. The only external piece is whatever feeds the `spokenText`. Typically that's an STT provider (`references/integrate-stt.md`), but it could also be manual text input for debug screens or accessibility flows.

## Minimal Compose recipe (STT-driven)

```kotlin
@Composable
fun PronunciationScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val target = "Practice makes perfect."

    val stt: SpeechToTextProvider = remember { AndroidSpeechToTextProvider(ctx) }
    val assessor = remember { SimpleWordMatchAssessor() }
    DisposableEffect(stt) { onDispose { stt.destroy() } }

    var heard  by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PronunciationResult?>(null) }

    LaunchedEffect(stt) {
        stt.prepare(ctx)
        stt.events.collect { evt ->
            if (evt is SttEvent.Result) {
                heard = evt.result.text
                if (heard.isNotBlank()) result = assessor.assess(target, heard)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Say:", style = MaterialTheme.typography.labelLarge)
        Text(target, style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            if (stt.isListening) stt.stopListening() else stt.startListening()
        }) {
            Text(if (stt.isListening) "Stop" else "Record")
        }

        Text("Heard: $heard", color = MaterialTheme.colorScheme.outline)

        result?.let { r ->
            Spacer(Modifier.height(16.dp))
            ScoreCard(r)
            Spacer(Modifier.height(8.dp))
            ReferenceHighlight(r)
        }
    }
}
```

### Rendering helpers

The library only gives you the data — you render. Two starter components for the result:

```kotlin
@Composable
fun ScoreCard(result: PronunciationResult) {
    val color = when (result.level) {
        PronunciationLevel.EXCELLENT -> Color(0xFF2E7D32)
        PronunciationLevel.GOOD      -> Color(0xFF558B2F)
        PronunciationLevel.FAIR      -> Color(0xFFEF6C00)
        PronunciationLevel.TRY_AGAIN -> Color(0xFFC62828)
    }
    Card(colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.padding(16.dp)) {
            Text("${result.score.toInt()}%", color = Color.White,
                 style = MaterialTheme.typography.displaySmall)
            Text(result.level.label, color = Color.White)
        }
    }
}

@Composable
fun ReferenceHighlight(result: PronunciationResult) {
    Text(buildAnnotatedString {
        result.wordDetails.forEachIndexed { i, d ->
            if (i > 0) append(" ")
            withStyle(SpanStyle(
                color = if (d.isMatched) Color(0xFF2E7D32) else Color(0xFFC62828),
                fontWeight = FontWeight.SemiBold
            )) { append(d.word) }
        }
    })
}
```

`wordDetails` is in reference-text order so the highlight reads naturally. `matchedWords` / `unmatchedWords` are summary lists for stats screens (e.g. "you missed: 'perfect', 'practice'").

## Pairing with Gemini STT (transcript + structured coaching)

`GeminiSpeechToTextProvider`'s `SttResult` is actually a `GeminiSttResult` — it has the transcript plus a structured coaching payload. If you're using Gemini STT, you can either:

1. Feed the transcript into `SimpleWordMatchAssessor` (same flow as above), or
2. Use Gemini's structured response directly and skip the local assessor for richer per-phoneme feedback.

Option 2 belongs in a custom `PronunciationAssessor` subclass — keep the host UI talking to a single `PronunciationAssessor` interface regardless of source.

## Manual text input (no STT)

For debug/accessibility paths:

```kotlin
var typed by remember { mutableStateOf("") }
TextField(value = typed, onValueChange = { typed = it })
Button(onClick = { result = assessor.assess(target, typed) }) { Text("Score") }
```

Useful for testing your render code without granting mic permission or hitting STT quota.

## Score thresholds

`PronunciationLevel.fromScore(score)` partitions the 0–100 range into four buckets. Treat the boundaries as private — read the level instead of the raw thresholds, so the lib can re-tune without breaking your UI.

Want bucket-specific copy ("Nice — try once more to nail 'perfect'!")? Branch off `level`, not `score`.

## Gotchas

- **`spokenText.isBlank()` returns a `score = 0` result, not `null`.** Guard your UI so you don't show a "0% Try Again" card when the user hasn't recorded yet — only call `assess` once you have a non-empty transcript.
- **Tokenization strips punctuation.** `normalizeText` lowercases, removes punctuation, collapses whitespace. `"Hello, world!"` and `"hello world"` tokenize identically. Don't try to score code or symbol-heavy strings.
- **Score is whole-word match, not phoneme accuracy.** "world" vs "word" counts as a miss. If your learners need finer feedback, plug in a phonetic-distance subclass.
- **Order matters in `wordDetails`** — it follows the reference text, not the spoken text. Don't try to render the spoken transcript from `wordDetails`; use the raw `SttEvent.Result.text` for that.
- **`matchedWords` may have duplicates** if the reference repeats a token ("very very fast"). Length counts match-instances, not unique words.
- **Locale parameter is currently unused** by `SimpleWordMatchAssessor` — it's there for future subclasses. Pass it anyway so existing call sites work when a richer assessor lands.
- **No fuzzy matching.** Unlike the retired `:pronunciation` module's `LevenshteinMatcher`, this assessor is strict word equality after normalization. STT misrecognitions ("world" → "whirled") will score 0 on that token. Plan UX accordingly — e.g. allow "try again" on low scores rather than treating them as failures.

## Verifying it works

1. `assess("hello world", "hello world")` → `score == 100`, `level == EXCELLENT`, both words matched.
2. `assess("hello world", "hello")` → `score == 50`, one match + one unmatched, level `FAIR` (or whatever the threshold yields — read from `.level`).
3. `assess("hello world", "")` → `score == 0`, level `TRY_AGAIN`. Guard your UI against rendering this when no recording has happened.
4. Manual text input → STT-free smoke test on every screen.

## Where to go next

- For the "speak the phrase" capture half of the flow: `references/integrate-stt.md`.
- For richer feedback than word-matching: subclass `PronunciationAssessor`, reuse `normalizeText`, return your own `PronunciationResult`.
