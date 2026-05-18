# Merge 4 Speech Modules into :ai-speech — Brainstorm + Design Session

**Date:** 2026-05-18 10:49 (Asia/Saigon)
**Severity:** Medium (architectural consolidation, no production impact yet)
**Component:** Gradle modules `:stt`, `:tts`, `:speech`, `:charactorspeak` → `:ai-speech`
**Status:** Design locked, 7-phase implementation plan created

## What Happened

User initiated a brainstorm to merge 4 overlapping Android Gradle modules into a single `:ai-speech` module containing 4 sub-packages (`stt/`, `tts/`, `pronunciation/`, `character/`). The motivation: eliminate 3-way code duplication of STT/TTS provider implementations and conflicting interface families. Session concluded with design consensus and a materialized 7-phase implementation plan with 7 associated task items.

## The Brutal Truth

The codebase has a duplication problem that's harder to spot than a single catastrophic bug — it's scattered across modules. `TtsProvider` exists as a byte-for-byte duplicate in both `:tts` and `:charactorspeak`. `AndroidTtsProvider` and `AndroidSpeechToTextProvider` were reimplemented in 3 places (`:tts`, `:speech`, `:charactorspeak`). Meanwhile, `:speech` contains a graveyard of older abstract-class versions of interfaces that nothing imports, whisper.cpp native code that nothing calls, and a Koin DI module that the sample app never touches. The sample app itself only imports from `:stt` and `:charactorspeak` — `:tts` and `:speech` are already dead weight in practice. This kind of drift is insidious because it looks like organization until you measure what's actually being used.

## Technical Details

**Concrete duplication found:**
- `TtsProvider` interface: identical copy in `:tts` and `:charactorspeak`
- `AndroidTtsProvider`: implemented in `:tts` (minSdk 26), `:speech` (old abstract base), `:charactorspeak` (minSdk 24)
- `SpeechToTextProvider` / `TextToSpeechProvider`: two competing interface families (`:stt`/`:tts` modern style vs `:speech` older abstract-class style)
- `:speech/src/main/cpp/whisper.cpp`: 0 Kotlin references — orphaned native code
- `:speech/src/main/kotlin/com/baseproject/speech/di/SpeechModule`: Koin DI bindings never imported by sample app
- `:charactorspeak/tts/*`: TTS providers duplicated; module instead composes `:tts` post-merge

**Design locked:**
- New module: `:ai-speech`, namespace `com.baseproject.aispeech`
- 4 sub-packages mirror old modules; `character/` package composes `tts/` directly (no internal TTS subpackage)
- JVM 17, minSdk 24 (downgrade from `:stt`'s 26)
- Heavy dependencies (Azure Speech SDK, Spine) marked `compileOnly` — consumers opt in
- Interface winners: `:tts` style for TTS (modern suspend prepare + speak), `:stt` style for STT
- Dropped: `speech/tts/*`, `speech/stt/*`, `speech/di/SpeechModule`, `speech/cpp/whisper.cpp`, `charactorspeak/tts/*`, `charactorspeak` JitPack maven-publish
- Kept: `speech/pronunciation/*` (without Koin)
- Hard break — no backward-compat facades (sample and app are only consumers; they live in the same repo)

## What We Tried

N/A — this was a discovery + planning session, not an implementation. Brainstorm evaluated 3 alternative architectures:
1. **Flat 4 packages (chosen):** Mirrors old module structure. KISS, easy review. No premature shared `core/` package.
2. **4 packages + shared `core/`:** Rejected (no shared code today; YAGNI).
3. **Product flavors (stt-only / tts-only / full):** Rejected (overkill; `compileOnly` already provides opt-in).

## Root Cause Analysis

The duplication emerged over time without active dedup because:
1. Modules were built for different stakeholder purposes (`:stt` for speech-only features, `:charactorspeak` for character animation, `:speech` as an earlier attempt at consolidation that never fully replaced the earlier modules).
2. Android provider pattern (interface + multiple impls) encourages copy-paste — no shared base library established early.
3. `:speech` module existed as a partial consolidation attempt but diverged using abstract classes instead of interfaces, so consumers never migrated to it. It became dead code with a runtime DI layer nobody touched.
4. `:charactorspeak` brought its own copy of TTS providers rather than depending on `:tts` — coupling avoidance that created duplication.

This is a classic "layers of organizational debt" problem: each module made sense in isolation at the time it was created.

## Lessons Learned

1. **Detect dead code early**: `:speech`'s Koin DI module and whisper.cpp sat in the repo because nothing screamed "this code path is unreachable." Regular consumption audits (grepping imports, tracing sample app deps) would have caught this faster.
2. **Interface parity matters**: Two competing TTS interfaces (abstract class in `:speech`, interface in `:tts`) meant consumers had to choose a style. No consolidation pressure. Future multi-impl patterns should establish the interface first, then implementations follow.
3. **Hard breaks are okay for internal modules**: `:tts`, `:stt`, `:speech`, `:charactorspeak` are only consumed by `:sample` and `:app` — both in the same repo. Deprecation facades and typealiases add surface area. A single squashed commit with updated imports in sample/app is cleaner.
4. **Spine + Azure as runtime deps need explicit documentation**: Post-merge, if a consumer imports `character/` but forgets to add Spine as an `implementation`, they get a runtime ClassNotFoundException. This is acceptable (they chose the dependency path), but needs a README.
5. **Previous plan was incomplete**: The prior plan `260518-0844-stt-multi-provider-refactor` shows `status: pending` in its frontmatter, but its work was committed to git (`feat(stt): add Gemini provider`). Stale frontmatter — a reminder to refresh plan status when work completes.

## Next Steps

1. **Execute 7-phase implementation plan** — start with Phase 1 (skeleton `:ai-speech` module + `build.gradle.kts` + `AndroidManifest.xml`)
2. **Verify Gemini minSdk 24 support** — `:stt` was built at minSdk 26; confirm `GeminiAudioRecorder` has no API 26+ hard dependencies or gate with `@RequiresApi(26)`
3. **Confirm Spine runtime in sample** — post-merge, `character/` no longer has Spine as `api` transitive; sample must add Spine `implementation` explicitly or character lipsync crashes
4. **Verify sed substitution order in Phase 5** — rewrite `apero.tutor.sdk.tts` BEFORE `apero.tutor.sdk` to avoid mangling `character.tts` imports
5. **Document via AAR verification** — ensure `compileOnly` deps (Azure, Spine) are NOT bundled in final AAR

---

## Unresolved Questions

1. **Does `GeminiAudioRecorder` require minSdk 26?** Need to inspect for `AudioRecord.Builder` or other API-level gated calls. If yes, gate with `@RequiresApi(26)` in the caller.
2. **Does sample app actually have Azure SDK at runtime?** Sample imports `AzureTtsProvider` and `ElevenLabsTtsProvider`. If Azure is missing, those code paths likely already crash today. Out of scope for merge; defer to pre-existing issue audit.
3. **Should we add `:ai-speech` README post-merge?** Recommend documenting the 4 sub-packages and their `compileOnly` runtime requirements. Not blocking; nice-to-have.

---

**Plan artifacts:** `/Users/nguyendoanhung/Desktop/src/SDK-AI-Learn/plans/260518-1049-merge-speech-modules-into-ai-speech/` (plan.md + 7 phase files + 7 tasks)
