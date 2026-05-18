   ---
phase: 2
title: Migrate :stt sources to aispeech.stt
status: pending
priority: high
effort: 45min
---

# Phase 2 — Migrate `:stt` → `aispeech.stt`

## Context Links
- Plan overview: [plan.md](plan.md)
- Phase 1 (skeleton): [phase-01-skeleton-aispeech-module.md](phase-01-skeleton-aispeech-module.md)

## Overview
- **Priority:** high
- **Status:** pending
- Move all 11 `.kt` files from `:stt/src/main/java/com/baseproject/stt/` to `:ai-speech/src/main/kotlin/com/baseproject/aispeech/stt/`. Rename package declarations and internal cross-references. STT interface is unchanged — it's already the winning interface.

## Key Insights
- All sources currently under `java/` source set even though they're `.kt` files. Move them to the new `kotlin/` source set in `:ai-speech` (matches `:tts`'s layout).
- The `:stt` module was the recently-built one (commits in last few days) — no historical baggage.
- Test files exist (`:stt/src/test/`) and must also migrate.

## Requirements
- All 11 source files moved with `git mv` to preserve blame
- Package declarations changed from `com.baseproject.stt.*` → `com.baseproject.aispeech.stt.*`
- Internal imports updated (e.g., `GeminiSpeechToTextProvider` imports `GeminiSttResult`)
- minSdk 24 compatibility verified for Gemini provider (was minSdk 26 in old `:stt`)

## Related Code Files

**Move (git mv) — 11 source files + tests:**
```
stt/src/main/java/com/baseproject/stt/SttEvent.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/stt/SttEvent.kt
stt/src/main/java/com/baseproject/stt/SttResult.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/stt/SttResult.kt
stt/src/main/java/com/baseproject/stt/SpeechToTextProvider.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/stt/SpeechToTextProvider.kt
stt/src/main/java/com/baseproject/stt/android/AndroidSpeechToTextProvider.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/stt/android/AndroidSpeechToTextProvider.kt
stt/src/main/java/com/baseproject/stt/android/AndroidSttResult.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/stt/android/AndroidSttResult.kt
stt/src/main/java/com/baseproject/stt/gemini/GeminiSpeechToTextProvider.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/stt/gemini/GeminiSpeechToTextProvider.kt
stt/src/main/java/com/baseproject/stt/gemini/GeminiSttResult.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/stt/gemini/GeminiSttResult.kt
stt/src/main/java/com/baseproject/stt/gemini/GeminiPrompt.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/stt/gemini/GeminiPrompt.kt
stt/src/main/java/com/baseproject/stt/gemini/internal/GeminiAudioRecorder.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/stt/gemini/internal/GeminiAudioRecorder.kt
stt/src/main/java/com/baseproject/stt/gemini/internal/GeminiDto.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/stt/gemini/internal/GeminiDto.kt
stt/src/main/java/com/baseproject/stt/gemini/internal/GeminiApi.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/stt/gemini/internal/GeminiApi.kt

stt/src/test/java/** → ai-speech/src/test/kotlin/com/baseproject/aispeech/stt/** (mirror tree)
```

## Implementation Steps

1. **Verify minSdk 24 compatibility for Gemini provider:**
   ```bash
   grep -n "AudioRecord.Builder\|@RequiresApi\|VERSION.SDK_INT" stt/src/main/java/com/baseproject/stt/gemini/internal/GeminiAudioRecorder.kt
   ```
   - If `AudioRecord.Builder` (API 23+) used → OK at 24
   - If any `@RequiresApi(26)` found → add the same gate in the new file; document in journal
   - If `AudioRecord` ctor-based recording → OK at any API

2. **Create target directories:**
   ```bash
   mkdir -p ai-speech/src/main/kotlin/com/baseproject/aispeech/stt/{android,gemini/internal}
   mkdir -p ai-speech/src/test/kotlin/com/baseproject/aispeech/stt
   ```

3. **Move files with `git mv`** (one per file to preserve blame):
   ```bash
   git mv stt/src/main/java/com/baseproject/stt/SttEvent.kt \
          ai-speech/src/main/kotlin/com/baseproject/aispeech/stt/SttEvent.kt
   # ... repeat for all 11 sources + all test files
   ```

4. **Rewrite package declarations** in each moved file:
   - `package com.baseproject.stt` → `package com.baseproject.aispeech.stt`
   - `package com.baseproject.stt.android` → `package com.baseproject.aispeech.stt.android`
   - `package com.baseproject.stt.gemini` → `package com.baseproject.aispeech.stt.gemini`
   - `package com.baseproject.stt.gemini.internal` → `package com.baseproject.aispeech.stt.gemini.internal`

5. **Rewrite intra-package imports** in each moved file:
   - `import com.baseproject.stt.*` → `import com.baseproject.aispeech.stt.*`
   - Use `find ai-speech/src -name "*.kt" -exec sed -i '' 's|com\.baseproject\.stt|com.baseproject.aispeech.stt|g' {} \;` (BSD sed syntax for macOS)

6. **Compile-check:**
   ```bash
   ./gradlew :ai-speech:compileDebugKotlin
   ```
   Must succeed. If it fails, fix imports/package decls.

7. **Run unit tests** (existing `:stt` tests now live here):
   ```bash
   ./gradlew :ai-speech:testDebugUnitTest
   ```

## Todo List
- [ ] Verify `GeminiAudioRecorder` API compatibility at minSdk 24
- [ ] Create target directory tree under `ai-speech/src/main/kotlin/com/baseproject/aispeech/stt/`
- [ ] Create target test tree under `ai-speech/src/test/kotlin/com/baseproject/aispeech/stt/`
- [ ] `git mv` all 11 main sources
- [ ] `git mv` all test sources
- [ ] Rewrite package declarations in all moved files
- [ ] Rewrite intra-package imports via sed
- [ ] `./gradlew :ai-speech:compileDebugKotlin` passes
- [ ] `./gradlew :ai-speech:testDebugUnitTest` passes

## Success Criteria
- 11 main source files + N test files exist under `aispeech.stt.*`
- 0 files remain under `stt/src/main/java/com/baseproject/stt/` (the directory becomes empty)
- `:ai-speech` compiles
- All `:stt` unit tests pass under new package
- `git log --follow` works on moved files (blame preserved)

## Risk Assessment
- **Gemini AudioRecorder uses API 26 APIs** → must gate or revert minSdk to 26. Verify in step 1; if blocked, change `:ai-speech` minSdk back to 26 in `phase-01`'s build.gradle (revisit phase 1 todo). Document in journal.
- **`sed -i` syntax mac vs linux** → contributor uses macOS (zsh); use `sed -i ''` (empty backup arg) on macOS.
- **Test resources** in `stt/src/test/resources/` must also move alongside test sources.

## Security Considerations
- Gemini API key handling unchanged (still passed in at provider construction by sample app via BuildConfig). No new attack surface.

## Next Steps
- Phase 3 (TTS) is independent — can run in parallel
- Old `:stt` directory still exists (empty source set); deleted in Phase 6
