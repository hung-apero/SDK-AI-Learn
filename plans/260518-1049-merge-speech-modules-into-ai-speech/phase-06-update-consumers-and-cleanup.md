---
phase: 6
title: Update :sample consumer + delete old modules
status: pending
priority: critical
effort: 45min
---

# Phase 6 — Update `:sample` consumer + delete old modules

## Context Links
- Plan overview: [plan.md](plan.md)
- Depends on: Phases 2, 3, 4, 5 all complete

## Overview
- **Priority:** critical (the breaking change point)
- **Status:** pending
- **Depends on:** Phases 2-5 complete
- Switch `:sample` from depending on `:stt`+`:charactorspeak` to depending on `:ai-speech`. Rewrite imports. Then remove the 4 old modules from `settings.gradle.kts` and delete their directories.

## Key Insights
- `:sample` is the ONLY consumer — `:app` doesn't touch any of these modules. Confirmed by grep in brainstorm scout.
- Sample imports today: `com.baseproject.stt.*` and `com.apero.tutor.sdk.*` (no `:tts`, no `:speech`). Two prefix rewrites total.
- Sample's `minSdk = 26` already (Azure SDK constraint). No floor change needed.
- Azure SDK runtime is supplied by `:sample` (`implementation "com.microsoft.cognitiveservices.speech:client-sdk:1.44.0"`) — unchanged.

## Requirements
- `:sample/build.gradle.kts` depends on `:ai-speech` only (not `:stt`/`:charactorspeak`)
- All sample imports rewritten to `com.baseproject.aispeech.*` paths
- `settings.gradle.kts` lists only `:ai-speech` (no `:stt`, `:tts`, `:speech`, `:charactorspeak`)
- The 4 old module directories are deleted via `git rm -r`

## Related Code Files

**Modify:**
- `sample/build.gradle.kts` — replace `project(":charactorspeak")` and `project(":stt")` with `project(":ai-speech")`
- `sample/src/main/kotlin/com/baseproject/sample/MainActivity.kt` — rewrite imports
- `sample/src/main/kotlin/com/baseproject/sample/stt/SttResultRenderer.kt` — rewrite imports
- `sample/src/main/kotlin/com/baseproject/sample/stt/SttProviderFactory.kt` — rewrite imports
- (Possibly other sample files — grep before editing)
- `settings.gradle.kts` — remove 4 old `include()` lines

**Delete (`git rm -r`):**
- `stt/`
- `tts/`
- `speech/`
- `charactorspeak/`
- Any `.idea/modules.xml` references (Android Studio auto-regenerates)

## Implementation Steps

1. **Find ALL sample/app files with old imports:**
   ```bash
   grep -rln "com\.baseproject\.\(stt\|tts\|speech\)\|com\.apero\.tutor\.sdk" sample/ app/
   ```

2. **Rewrite imports across all hits — 4 prefix mappings:**
   ```bash
   # Map old → new
   # com.baseproject.stt        → com.baseproject.aispeech.stt
   # com.baseproject.tts        → com.baseproject.aispeech.tts
   # com.baseproject.speech.pronunciation → com.baseproject.aispeech.pronunciation
   # com.apero.tutor.sdk.tts    → com.baseproject.aispeech.tts
   # com.apero.tutor.sdk        → com.baseproject.aispeech.character

   find sample app -name "*.kt" -exec sed -i '' \
     -e 's|com\.baseproject\.stt|com.baseproject.aispeech.stt|g' \
     -e 's|com\.baseproject\.tts|com.baseproject.aispeech.tts|g' \
     -e 's|com\.baseproject\.speech\.pronunciation|com.baseproject.aispeech.pronunciation|g' \
     -e 's|com\.apero\.tutor\.sdk\.tts|com.baseproject.aispeech.tts|g' \
     -e 's|com\.apero\.tutor\.sdk|com.baseproject.aispeech.character|g' \
     {} \;
   ```
   Sed runs left-to-right; `apero.tutor.sdk.tts` substitution happens before `apero.tutor.sdk` — correct order.

3. **Edit `sample/build.gradle.kts`:**
   - Remove `implementation(project(":charactorspeak"))`
   - Remove `implementation(project(":stt"))`
   - Add `implementation(project(":ai-speech"))`
   - Keep the Azure runtime line (comment update: `:ai-speech` declares Azure compileOnly)

4. **Compile sample (still with old modules present):**
   ```bash
   ./gradlew :sample:assembleDebug
   ```
   Must succeed. If imports were missed, fix them.

5. **Remove old modules from `settings.gradle.kts`:**
   - Delete: `include(":speech")`, `include(":charactorspeak")`, `include(":stt")`, `include(":tts")`
   - Keep: `include(":ai-speech")`, `include(":sample")`, `include(":app")`

6. **Delete old module directories via git:**
   ```bash
   git rm -r stt tts speech charactorspeak
   ```

7. **Sync Gradle and rebuild:**
   ```bash
   ./gradlew :sample:assembleDebug
   ./gradlew :ai-speech:assembleDebug
   ```

8. **Cleanup Phase 1's `.keep` file** if it exists (`ai-speech/src/main/kotlin/com/baseproject/aispeech/.keep`).

## Todo List
- [ ] Grep all sample/app files with old imports — establish baseline
- [ ] Run multi-pattern sed import rewrite across sample + app
- [ ] Visually inspect MainActivity.kt — confirm rewrite is correct
- [ ] Edit sample/build.gradle.kts: swap deps to :ai-speech
- [ ] Run `./gradlew :sample:assembleDebug` — must succeed pre-cleanup
- [ ] Remove 4 old `include()` lines from settings.gradle.kts
- [ ] `git rm -r stt tts speech charactorspeak`
- [ ] Delete `.keep` placeholder if present
- [ ] Final compile: both `:ai-speech` and `:sample`

## Success Criteria
- `./gradlew :sample:assembleDebug` succeeds with `:ai-speech` only
- 0 files contain old import paths (`com.baseproject.{stt,tts,speech}` or `com.apero.tutor.sdk`)
- `settings.gradle.kts` lists 3 modules: `:app`, `:sample`, `:ai-speech`
- 4 old module directories no longer exist on disk

## Risk Assessment
- **Sed substitution overreach** — e.g., a string literal containing `com.apero.tutor.sdk` could be incorrectly rewritten. Likelihood low (no such strings expected in sample), but grep verify after.
- **`:speech` had references in sample** that grep missed (e.g., reflection-based class loading). Mitigation: full clean build verifies — any orphan reference fails compile.
- **AndroidManifest in app/sample** referencing old module manifest entries — none expected (libraries don't typically register components in manifests), but check.
- **`local.properties` / IDE caches** — Android Studio may need `File → Invalidate Caches`. Document for next session.

## Security Considerations
- No new attack surface. Sample's BuildConfig still injects API keys at compile time.

## Next Steps
- Phase 7 runs verification: sample app on device/emulator + unit tests
