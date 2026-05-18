---
title: Merge :stt + :tts + :speech + :charactorspeak into :ai-speech
slug: merge-speech-modules-into-ai-speech
created: 2026-05-18
status: pending
scope: ":stt + :tts + :speech + :charactorspeak modules + :sample consumer wiring"
brainstorm: ../reports/brainstorm-260518-1049-merge-speech-modules-into-ai-speech.md
supersedes: ../260518-0844-stt-multi-provider-refactor/
blockedBy: []
blocks: []
---

# Merge :stt + :tts + :speech + :charactorspeak into :ai-speech

Consolidate 4 Gradle modules with overlapping/duplicated responsibilities into a single `:ai-speech` module containing 4 sub-packages. Eliminate 3-way duplication of Android STT/TTS impls and 2 divergent TTS/STT interface families.

## Context

- **Brainstorm:** [brainstorm-260518-1049-merge-speech-modules-into-ai-speech.md](../reports/brainstorm-260518-1049-merge-speech-modules-into-ai-speech.md)
- **Supersedes:** `260518-0844-stt-multi-provider-refactor` (work committed; this merge moves the resulting `:stt` sources into `:ai-speech/stt/`)
- **Sample app dependencies today:** `:charactorspeak` + `:stt` only (sample doesn't import `:tts` or `:speech` directly)
- **App module:** no dependency on any of the 4 modules

## Decisions (locked from brainstorm)

- Module name: `:ai-speech`, namespace `com.baseproject.aispeech`
- 4 sub-packages: `stt/`, `tts/`, `pronunciation/`, `character/` (character has NO internal tts subpackage)
- JVM 17, minSdk 24 (downgrade `:stt`'s 26 — verify Gemini works at 24)
- Heavy deps `compileOnly`: Azure Speech SDK, Spine
- TTS interface: `:tts` style wins (modern, suspend prepare, speak(text, utteranceId))
- STT interface: `:stt` style wins (modern, suspend prepare, startListening())
- Drop: `speech/tts/*`, `speech/stt/*`, `speech/di/SpeechModule`, `speech/cpp/whisper.cpp`, `charactorspeak/tts/*`, charactorspeak `maven-publish` block
- Keep: `speech/pronunciation/*` (no Koin)
- Hard break — no facades, no @Deprecated typealiases
- File moves via `git mv` to preserve blame

## Phases

| # | Phase | File | Status |
|---|-------|------|--------|
| 1 | Skeleton `:ai-speech` module | [phase-01-skeleton-aispeech-module.md](phase-01-skeleton-aispeech-module.md) | pending |
| 2 | Migrate `:stt` → `aispeech.stt` | [phase-02-migrate-stt-package.md](phase-02-migrate-stt-package.md) | pending |
| 3 | Migrate `:tts` → `aispeech.tts` | [phase-03-migrate-tts-package.md](phase-03-migrate-tts-package.md) | pending |
| 4 | Migrate `:speech` pronunciation → `aispeech.pronunciation` | [phase-04-migrate-pronunciation-package.md](phase-04-migrate-pronunciation-package.md) | pending |
| 5 | Migrate `:charactorspeak` → `aispeech.character` | [phase-05-migrate-character-package.md](phase-05-migrate-character-package.md) | pending |
| 6 | Update `:sample` consumer + delete old modules | [phase-06-update-consumers-and-cleanup.md](phase-06-update-consumers-and-cleanup.md) | pending |
| 7 | Verify build, run sample, run tests | [phase-07-verify-and-test.md](phase-07-verify-and-test.md) | pending |

## Key dependencies between phases

- Phase 1 must precede 2–5 (module shell needed)
- Phases 2, 3, 4 are **independent** (no cross-package imports) — could run in parallel
- Phase 5 depends on Phase 3 (character → tts interface)
- Phase 6 depends on Phases 2 + 5 (sample imports stt + character)
- Phase 7 depends on Phase 6

## Success criteria (overall)

- `./gradlew :sample:assembleDebug` builds clean
- `./gradlew :ai-speech:testDebugUnitTest` passes
- Sample app: STT (Android + Gemini), TTS (Android + Azure + ElevenLabs), Character lipsync all work
- `settings.gradle.kts` lists `:ai-speech` (no `:stt`/`:tts`/`:speech`/`:charactorspeak`)
- No remaining files under `com.baseproject.{stt,tts,speech}.*` or `com.apero.tutor.sdk.*`
