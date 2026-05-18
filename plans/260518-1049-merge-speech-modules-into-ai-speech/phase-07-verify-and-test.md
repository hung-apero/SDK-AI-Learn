---
phase: 7
title: Verify build, run sample, run tests
status: pending
priority: critical
effort: 30min
---

# Phase 7 — Verify build, run sample, run tests

## Context Links
- Plan overview: [plan.md](plan.md)
- Depends on: Phase 6 complete

## Overview
- **Priority:** critical (definition of done)
- **Status:** pending
- **Depends on:** Phase 6 complete
- Validate the merge end-to-end. Build, install, exercise STT/TTS/Character flows in the sample app, run unit tests. Anything that fails here either rolls back the merge or schedules follow-up.

## Requirements
- Clean build from scratch
- All unit tests pass (carried over from `:stt`)
- Sample app installs on emulator/device and exercises each feature
- No lint errors that block compile

## Implementation Steps

1. **Clean build:**
   ```bash
   ./gradlew clean
   ./gradlew :ai-speech:assembleDebug :sample:assembleDebug
   ```
   Both must succeed. Note any warnings about unused dependencies.

2. **Unit tests:**
   ```bash
   ./gradlew :ai-speech:testDebugUnitTest
   ```
   All previously-passing `:stt` tests must pass under new package paths.

3. **Lint (informational, not blocking):**
   ```bash
   ./gradlew :ai-speech:lintDebug
   ```
   Review warnings — anything new since the merge?

4. **Install sample on emulator/device:**
   ```bash
   ./gradlew :sample:installDebug
   adb shell am start -n com.baseproject.sample/.MainActivity
   ```

5. **Manual smoke test — exercise each feature:**
   - **STT Android provider:** tap STT button, speak a phrase, verify transcription appears
   - **STT Gemini provider:** switch provider (if sample exposes), speak, verify Gemini result with grammar feedback
   - **TTS Android provider:** tap TTS, hear synthesized speech
   - **TTS Azure provider:** verify Azure voice plays (requires `AZURE_SPEECH_KEY` in `local.properties`)
   - **TTS ElevenLabs provider:** verify ElevenLabs voice plays (requires `ELEVENLABS_API_KEY`)
   - **Character lipsync:** play character TTS, observe Spine animation lip-sync + karaoke subtitles

6. **logcat sanity check during smoke test:**
   ```bash
   adb logcat -e "com.baseproject.aispeech\|TutorCharacter\|TtsProvider\|SpeechToTextProvider" -v time
   ```
   No `ClassNotFoundException`, `NoSuchMethodError`, or `RuntimeException` from the merged code.

7. **Git history sanity check:**
   ```bash
   git log --follow ai-speech/src/main/kotlin/com/baseproject/aispeech/stt/SpeechToTextProvider.kt | head -10
   git log --follow ai-speech/src/main/kotlin/com/baseproject/aispeech/character/TutorCharacter.kt | head -10
   ```
   Both should show pre-merge commits — confirms `git mv` preserved history.

## Todo List
- [ ] `./gradlew clean` succeeds
- [ ] `./gradlew :ai-speech:assembleDebug :sample:assembleDebug` succeeds
- [ ] `./gradlew :ai-speech:testDebugUnitTest` — all tests pass
- [ ] `./gradlew :sample:installDebug` succeeds
- [ ] Manual: STT Android works
- [ ] Manual: STT Gemini works (with API key)
- [ ] Manual: TTS Android works
- [ ] Manual: TTS Azure works (with key)
- [ ] Manual: TTS ElevenLabs works (with key)
- [ ] Manual: Character lipsync + karaoke subtitle works
- [ ] logcat clean (no merge-related runtime errors)
- [ ] `git log --follow` shows history preserved

## Success Criteria
- All checkboxes ticked
- Sample app demonstrates parity with pre-merge behavior across STT/TTS/Character
- Zero runtime errors from `com.baseproject.aispeech.*` packages

## Risk Assessment
- **Gemini provider fails at minSdk 24** (if a 26+ API was hidden) → either gate with `@RequiresApi(26)` or revert `:ai-speech` minSdk to 26 in `build.gradle.kts`. Document in journal.
- **Sample app's Azure runtime mismatch** — Azure SDK 1.44.0 must remain runtime in sample. Already declared, just verify.
- **Spine runtime missing in sample** — `:ai-speech` declares Spine `compileOnly`; sample currently has `:charactorspeak` (which was `api`-exposed Spine). After merge, sample must declare Spine itself. **Action:** add `implementation("com.esotericsoftware.spine:spine-android:4.2.12")` to `sample/build.gradle.kts` — capture this in Phase 6 todo too.
- **Lipsync timing drift** — if `LipSyncEngine` internally used `com.apero.tutor.sdk.tts.TtsEvent` types now renamed, any reflection-based lookup would break. Compile catches it; smoke test confirms.

## Security Considerations
- No new surface — verification only.

## Next Steps
- If all green: commit + push, run `/ck:journal` to log the merge
- If issues found: triage; small fixes go back to the relevant phase; larger issues become follow-up tasks
- If user wants to publish `:ai-speech` to JitPack later, that's a separate task (charactorspeak's `maven-publish` block was intentionally dropped)
