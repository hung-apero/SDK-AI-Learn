---
name: integrate-ai-speech-skill
status: planning
created: 2026-05-19
mode: fast
---

# Plan: `integrate-ai-speech` Claude skill

Teach Claude how to integrate the published `apero-inhouse:ai-speech` Android library into consumer apps. Mirrors the existing `integrate-pronunciation` / `integrate-speaking-avatar` skill pattern but splits content across 4 capability-specific reference files (progressive disclosure).

## Where it lives (recommended)

**Project-level:** `.claude/skills/integrate-ai-speech/` in the SDK-AI-Learn repo.

Why project over user-level:
- Skill teaches consumers of *this* library — should ship with it, not the developer's machine
- Versioned alongside library changes (refactors → skill commit in same PR)
- Available to any teammate cloning the repo, no manual install
- Existing `integrate-pronunciation` / `integrate-speaking-avatar` at `~/.claude/skills/` describe the *old* `:pronunciation` / `:speaking-avatar` modules, which no longer exist (merged into `:ai-speech` per commit `6978f6b`) — they should be retired or rewritten too, but that's a separate decision

User-level alternative: `~/.claude/skills/integrate-ai-speech/` — only if the user wants it cross-project on their machine and not in the repo.

## Directory layout

```
.claude/skills/integrate-ai-speech/
├── SKILL.md                                  # entry: overview, decision tree, common Gradle wiring
└── references/
    ├── integrate-stt.md                      # Speech-to-Text (Android + Gemini providers)
    ├── integrate-tts.md                      # Text-to-Speech (Android + Azure + ElevenLabs)
    ├── integrate-character-speaking.md       # TutorCharacter + Spine animation + lipsync + subtitle
    └── integrate-pronunciation.md            # Pronunciation scoring (assessor + simple matcher)
```

Progressive disclosure: SKILL.md stays small (frontmatter triggers + ~100-line overview + decision table + Gradle setup). Each reference file is loaded by Claude only when that capability is being integrated.

## SKILL.md content scope

| Section | Purpose |
|---|---|
| **Frontmatter** (`name`, `description`) | One-sentence trigger covering all 4 capabilities |
| **What this library provides** | 4-row capability table: STT / TTS / Character speaking / Pronunciation |
| **Decision: which capability do you need?** | Maps intent → reference file path to load next |
| **Gradle wiring (once)** | `apero-inhouse:ai-speech:0.1.0` from mavenLocal/Artifactory; minSdk 24 (26 if using Azure TTS); JVM 17; compileOnly note for Azure SDK + Spine that consumers must add at runtime if they use those providers |
| **Manifest basics** | `RECORD_AUDIO`, `INTERNET`, Android 11 SpeechRecognizer queries entry |
| **Module-wide gotchas** | minSdk bump for Azure, ABI filter recommendation, no UI components ship in the lib — consumers render their own Compose |
| **Reference loader** | "Load `references/integrate-{capability}.md` when integrating that capability" |

Target size: ~120 lines. Keep examples in references, not here.

## Reference file scopes

### `references/integrate-stt.md`

Public surface: `com.baseproject.aispeech.stt`
- `SpeechToTextProvider` interface, `SttEvent` sealed hierarchy, `SttResult`
- Providers: `AndroidSpeechToTextProvider` (offline, free), `GeminiSpeechToTextProvider` (cloud, key required) + `GeminiPrompt` / `GeminiSttResult`
- Decision table: offline vs cloud, locale support, partial-vs-final, cost
- Permissions + manifest queries entry for `RECOGNIZE_SPEECH`
- Flow-based collection pattern (collect `SttEvent` in a coroutine)
- Gemini setup: API key from `local.properties`, model + native locale config
- Minimum Compose recipe (record button → live transcript)
- Gotchas: `RECORD_AUDIO` runtime grant, locale fallback, partial recomposition cost

Target size: ~200 lines.

### `references/integrate-tts.md`

Public surface: `com.baseproject.aispeech.tts`
- `TtsProvider` interface, `TtsEvent` sealed hierarchy
- Providers: `AndroidTtsProvider` (offline), `AzureTtsProvider` (cloud, requires consumer to add `com.microsoft.cognitiveservices.speech:client-sdk:1.44.0`), `ElevenLabsTtsProvider` (HTTP only, no extra runtime dep)
- Decision table: cost, quality, offline support, voice selection
- Provider configs: Azure key/region/voice, ElevenLabs key/voiceId/modelId
- `speak` overload variants and lib-generated request IDs (per recent commit `260518-1607`)
- Streaming buffer behavior (per commit `260518-1408 tts-phrase-buffer-streaming`)
- Minimum recipe per provider
- Gotchas: minSdk 26 if using Azure (azure-core transitive), ABI filters (`x86_64`, `arm64-v8a`), 16KB-page jniLibs packaging

Target size: ~220 lines.

### `references/integrate-character-speaking.md`

Public surface: `com.baseproject.aispeech.character` + sub-packages
- `TutorCharacter` + `TutorCharacterConfig` entry points
- Sub-systems: `animation/SpineController` + `AnimationContract`, `lipsync/LipSyncEngine` + `VisemeEvent`, `subtitle/SubtitleRenderer` + `KaraokeSubtitleRenderer`, `sse/SseBuffer`
- Wiring data flow: TTS stream → `LipSyncEngine` → viseme events → `SpineController` mouth shapes; subtitle stream → karaoke renderer
- Host requirement: add `com.esotericsoftware.spine:spine-android:4.2.12` at runtime (`compileOnly` in lib)
- Spine asset layout consumer must ship (atlas / json / png triples)
- Minimum Compose screen recipe (avatar surface + TTS-driven speak)
- Pairing note: usually paired with `integrate-tts.md` (TTS feeds the lipsync stream)
- Gotchas: viseme timing buffer for SSE TTS, subtitle/lipsync clock drift, AndroidView interop for Spine surface

Target size: ~250 lines (heaviest of the four — most cross-component wiring).

### `references/integrate-pronunciation.md`

Public surface: `com.baseproject.aispeech.pronunciation`
- `PronunciationAssessor` + `PronunciationResult` (advanced path)
- `simple/SimpleWordMatchAssessor` (drop-in for word-level drills)
- **Important:** no Compose UI ships in `:ai-speech` for pronunciation — old `:pronunciation` module had `PronunciationResultView`, that was dropped in the merge. Consumers render their own from `PronunciationResult`.
- Decision: assessor vs simple matcher
- STT pairing — assessor is host-STT-agnostic; reference back to `integrate-stt.md`
- Levenshtein tuning (threshold, minFuzzyLen) — carry forward from old skill
- Recipe: STT final transcript → `assessor.score(target, said)` → render
- Gotchas: Unicode tokenization, empty target, LCS alignment (not naive zip)

Target size: ~180 lines.

## Out of scope (this skill won't cover)

- Building / publishing the `:ai-speech` library itself (that's `scripts/publish.sh` and module gradle config — separate concern)
- UI design choices (color, layout) — references show data binding only, no aesthetic guidance
- Backend setup for Gemini/Azure/ElevenLabs accounts (link to official provider docs)

## Implementation steps (when user approves)

1. Create `.claude/skills/integrate-ai-speech/` and `references/` subdir
2. Write `SKILL.md` (frontmatter + overview + decision table + Gradle once)
3. Write `references/integrate-stt.md`
4. Write `references/integrate-tts.md`
5. Write `references/integrate-character-speaking.md`
6. Write `references/integrate-pronunciation.md`
7. Verify by loading SKILL.md description in a fresh Claude session — does the trigger sentence fire for relevant prompts? (manual smoke test)
8. Commit as single `docs: add integrate-ai-speech skill` commit

No code changes, no tests, no `./gradlew` runs needed.

## Unresolved questions

1. **Skill location** — project-level (`.claude/skills/`, my recommendation) or user-level (`~/.claude/skills/`)?
2. **Gradle wiring example in SKILL.md** — recommend `implementation("apero-inhouse:ai-speech:0.1.0")` (published artifact path, requires consumer to add `mavenLocal()` or Apero Artifactory), or `implementation(project(":ai-speech"))` (in-tree, only if consumer vendors the module)? Or document both with a decision note?
3. **Retire the old `integrate-pronunciation` and `integrate-speaking-avatar` skills?** They reference `:pronunciation` / `:speaking-avatar` modules that no longer exist post-merge. Out of scope for this plan but worth deciding before users hit the stale skills.
4. **Version bump policy** — should SKILL.md hardcode `0.1.0` or read it from `gradle.properties`? Hardcoding rots; reading is fragile. Recommend hardcode + note "version may have moved — check root `gradle.properties`".
