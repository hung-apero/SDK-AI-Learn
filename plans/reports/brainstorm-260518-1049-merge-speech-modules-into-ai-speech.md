# Brainstorm: Merge `:stt` + `:tts` + `:speech` + `:charactorspeak` into `:ai-speech`

**Date:** 2026-05-18 10:49 (Asia/Saigon)
**Branch:** `feat/sample-module`
**Status:** Design agreed, awaiting plan creation

---

## Problem Statement

4 Gradle modules with overlapping responsibilities & **3-way code duplication**:

| Module | Namespace | Reality |
|---|---|---|
| `:stt` | `com.baseproject.stt` | Modern STT interface + Android/Gemini impls |
| `:tts` | `com.baseproject.tts` | Modern TTS interface + Android/Azure/ElevenLabs impls |
| `:speech` | `com.baseproject.speech` | Older divergent TTS+STT interfaces (orphan duplicates) + pronunciation + whisper.cpp (unused) + Koin DI |
| `:charactorspeak` | `com.apero.tutor.sdk` | Character + lipsync + subtitle + **literal copy of `:tts`'s providers** |

**Concrete duplication:**
- `TtsProvider` interface duplicated verbatim between `:tts` and `:charactorspeak`
- `AndroidTtsProvider` / `AndroidSpeechToTextProvider` implemented 3 times (`:tts`, `:speech`, `:charactorspeak`)
- Two divergent TTS interfaces (`:tts` modern vs `:speech` old abstract class)
- Two divergent STT interfaces (`:stt` modern vs `:speech` old abstract class)

---

## Decisions (user-confirmed)

| Decision | Choice |
|---|---|
| Motivation | Eliminate code duplication |
| Module name | `:ai-speech` |
| Namespace | `com.baseproject.aispeech.*` |
| Heavy deps (Spine, Azure SDK) | `compileOnly` — consumer opts in |
| JVM target | 17 |
| minSdk | 24 (downgrade from `:stt`'s 26 — verify Gemini support) |
| TTS interface winner | `:tts` style (modern interface) |
| STT interface winner | `:stt` style (modern interface) |
| `:speech` extras | Keep pronunciation; drop whisper.cpp + Koin DI |
| Backward compat | None — hard break (only `:app`/`:sample` consume internally) |

---

## Evaluated Approaches

### A. Flat 4 packages (CHOSEN)
Mirrors user request literally. 4 packages = 4 old modules. `character/` composes `tts/` via direct interface usage. No shared `core/` package until needed.

**Pros:** KISS. Matches mental model. Easy review.
**Cons:** No home for shared utilities if they emerge. Acceptable (YAGNI — add `common/` later if needed).

### B. 4 packages + shared `core/`
Add `common/` for cross-cutting concerns (audio helpers, http builders).

**Rejected:** No demonstrable shared code today. Premature abstraction.

### C. Product flavors (stt-only / tts-only / full)
Multiple AAR variants.

**Rejected:** Overkill. User confirmed dedup is the driver, not lean variants. `compileOnly` on heavy deps already gives consumers the opt-in they need.

---

## Final Design

### Module Layout

```
ai-speech/
├── build.gradle.kts           (JVM 17, minSdk 24, namespace com.baseproject.aispeech)
├── consumer-rules.pro
└── src/main/kotlin/com/baseproject/aispeech/
    ├── stt/
    │   ├── SpeechToTextProvider.kt         (interface, suspend prepare)
    │   ├── SttEvent.kt
    │   ├── SttResult.kt
    │   ├── android/   (AndroidSpeechToTextProvider, AndroidSttResult)
    │   └── gemini/    (GeminiSpeechToTextProvider, GeminiSttResult, GeminiPrompt, internal/*)
    │
    ├── tts/
    │   ├── TtsProvider.kt                  (interface, suspend prepare, speak(text, utteranceId))
    │   ├── TtsEvent.kt
    │   ├── android/   (AndroidTtsProvider)
    │   ├── azure/     (AzureTtsProvider — compileOnly Azure SDK)
    │   └── elevenlabs/(ElevenLabsTtsProvider)
    │
    ├── pronunciation/
    │   ├── PronunciationAssessor.kt
    │   ├── PronunciationResult.kt
    │   └── simple/    (SimpleWordMatchAssessor)
    │
    └── character/
        ├── TutorCharacter.kt
        ├── TutorCharacterConfig.kt
        ├── animation/ (AnimationContract, SpineController — compileOnly Spine)
        ├── lipsync/   (LipSyncEngine, VisemeEvent)
        ├── subtitle/  (SubtitleRenderer, KaraokeSubtitleRenderer)
        └── sse/       (SseBuffer)
```

### Dependencies (consolidated `build.gradle.kts`)

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.baseproject.aispeech"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // compileOnly — consumer must add at runtime if using these providers
    compileOnly("com.microsoft.cognitiveservices.speech:client-sdk:1.44.0")  // azure tts
    compileOnly("com.esotericsoftware.spine:spine-android:4.2.12")           // character animation

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

### Migration Mapping

| Old import | New import |
|---|---|
| `com.baseproject.stt.SpeechToTextProvider` | `com.baseproject.aispeech.stt.SpeechToTextProvider` |
| `com.baseproject.stt.android.*` | `com.baseproject.aispeech.stt.android.*` |
| `com.baseproject.stt.gemini.*` | `com.baseproject.aispeech.stt.gemini.*` |
| `com.baseproject.tts.TtsProvider` | `com.baseproject.aispeech.tts.TtsProvider` |
| `com.baseproject.tts.{android,azure,elevenlabs}.*` | `com.baseproject.aispeech.tts.{…}.*` |
| `com.baseproject.speech.pronunciation.*` | `com.baseproject.aispeech.pronunciation.*` |
| `com.apero.tutor.sdk.TutorCharacter` | `com.baseproject.aispeech.character.TutorCharacter` |
| `com.apero.tutor.sdk.{lipsync,animation,subtitle,sse}.*` | `com.baseproject.aispeech.character.{…}.*` |
| `com.apero.tutor.sdk.tts.*` | **DELETE** — use `com.baseproject.aispeech.tts.*` |
| `com.baseproject.speech.tts.*` | **DELETE** — orphaned old interface |
| `com.baseproject.speech.stt.*` | **DELETE** — orphaned old interface |
| `com.baseproject.speech.di.SpeechModule` | **DELETE** — Koin DI dropped |

### `settings.gradle.kts` delta

```kotlin
// remove:
// include(":speech")
// include(":charactorspeak")
// include(":stt")
// include(":tts")

// add:
include(":ai-speech")
```

### Implicit losses (acknowledged)

1. **`SttConfig` / `TtsConfig`** — gone. Config moves to impl constructors.
2. **`QueueMode` enum + `speakSequence(texts)`** — gone. Reimplement inside Android impl if needed.
3. **Koin bindings** — `speechModule { … }` deleted. Consumers DI themselves.
4. **JitPack publishing** (charactorspeak `maven-publish`) — dropped.

---

## Implementation Considerations

### Order of operations
1. Create `:ai-speech` module skeleton + new `build.gradle.kts` + `AndroidManifest.xml`
2. Copy modern interfaces from `:stt` and `:tts` first (these are the canonical winners)
3. Migrate `:stt` sources → `aispeech.stt.*` (rename package only)
4. Migrate `:tts` sources → `aispeech.tts.*` (rename package only)
5. Migrate `:speech` **pronunciation only** → `aispeech.pronunciation.*` (drop tts/stt/di subpackages, drop cpp/)
6. Migrate `:charactorspeak` **non-tts** code → `aispeech.character.*`; rewrite `TutorCharacter` to depend on `aispeech.tts.TtsProvider` instead of `apero.tutor.sdk.tts.TtsProvider`
7. Update `settings.gradle.kts` — add `:ai-speech`, remove 4 old modules
8. Update `:sample` and `:app` `build.gradle.kts` — replace 4 dependencies with `:ai-speech`
9. Find/replace imports in `:sample`/`:app` (8 prefix mappings)
10. Delete old 4 module directories
11. Compile + run sample app — verify STT, TTS, character all work
12. Run existing `:stt` unit tests (now under `:ai-speech`) — ensure all pass

### File-preservation strategy
Use `git mv` per file when relocating to preserve `git blame` history. Bulk-move with a script if needed.

### Verification of `minSdk 24` downgrade
Gemini SDK code in `:stt` was built at `minSdk 26`. Need to confirm:
- `GeminiAudioRecorder` doesn't use `AudioRecord.Builder` APIs that require 26
- If it does, gate with `@RequiresApi(26)` and runtime check in `GeminiSpeechToTextProvider`

---

## Success Metrics

- [ ] `./gradlew :sample:assembleDebug` builds clean
- [ ] `./gradlew :ai-speech:testDebugUnitTest` passes (existing stt tests carry over)
- [ ] Sample app: STT (Android + Gemini) records and transcribes
- [ ] Sample app: TTS (Android + Azure + ElevenLabs) speaks audibly
- [ ] Sample app: Character lipsync renders with subtitle
- [ ] `settings.gradle.kts` lists `:ai-speech`, no `:stt`/`:tts`/`:speech`/`:charactorspeak`
- [ ] No file under `com.baseproject.stt.*`, `com.baseproject.tts.*`, `com.baseproject.speech.*`, `com.apero.tutor.sdk.*`
- [ ] Single `AndroidManifest.xml` under `:ai-speech` (no manifest merger conflicts)
- [ ] AAR size — informational; check that compileOnly deps are NOT bundled

---

## Risks & Mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| Gemini SDK code uses API 26+ features | Medium | Verify in impl phase; gate with `@RequiresApi(26)` if needed |
| `git mv` not done file-by-file → blame loss | Low | One script-driven `git mv` per file; squash commit |
| Sample app silently uses speech module's old TTS API | Low | Scan complete — sample only uses `:stt` + `:charactorspeak`. Confirmed. |
| Manifest merge issues (4 manifests → 1) | Low | All 4 manifests trivially empty; just declare permissions in the new one |
| Spine `compileOnly` breaks character if consumer forgets | Medium | Document in module README; runtime ClassNotFoundException is acceptable |
| Whisper.cpp deletion breaks something | Low | Grep confirmed no Kotlin code references `whisper` — safe to delete |

---

## Open Questions

1. Does `GeminiAudioRecorder` actually require minSdk 26? Verify during implementation; gate or accept the floor.
2. Sample app's `MainActivity` imports `AzureTtsProvider`/`ElevenLabsTtsProvider` — does the runtime have Azure SDK? If not, those code paths likely already crash today. Out of scope for merge; document as pre-existing.
3. Should we add a thin `:ai-speech` README documenting the 4 sub-packages and their `compileOnly` runtime requirements? Recommend yes, but defer to plan phase.

---

## Next Steps

Decide whether to invoke `/ck:plan` to materialize this design into a phased implementation plan, or end the session here.
