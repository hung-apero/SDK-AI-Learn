---
phase: 3
title: Migrate :tts sources to aispeech.tts
status: pending
priority: high
effort: 30min
---

# Phase 3 — Migrate `:tts` → `aispeech.tts`

## Context Links
- Plan overview: [plan.md](plan.md)
- Phase 1 (skeleton): [phase-01-skeleton-aispeech-module.md](phase-01-skeleton-aispeech-module.md)

## Overview
- **Priority:** high
- **Status:** pending
- Move 5 `.kt` files from `:tts/src/main/kotlin/com/baseproject/tts/` to `:ai-speech/src/main/kotlin/com/baseproject/aispeech/tts/`. Rename packages. TTS interface unchanged — already the winning interface.

## Key Insights
- `:tts` module already uses `kotlin/` source set + JVM 17 — matches `:ai-speech` config perfectly. No surprises expected.
- Azure SDK already `compileOnly` here — carries over to `:ai-speech` same way.
- This package will be the canonical TTS provider home; charactorspeak's duplicate copies (Phase 5) get deleted, not merged.

## Requirements
- All 5 source files moved with `git mv`
- Package declarations renamed `com.baseproject.tts.*` → `com.baseproject.aispeech.tts.*`
- No tests exist for `:tts` currently — none to move

## Related Code Files

**Move (git mv) — 5 source files:**
```
tts/src/main/kotlin/com/baseproject/tts/TtsEvent.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/tts/TtsEvent.kt
tts/src/main/kotlin/com/baseproject/tts/TtsProvider.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/tts/TtsProvider.kt
tts/src/main/kotlin/com/baseproject/tts/android/AndroidTtsProvider.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/tts/android/AndroidTtsProvider.kt
tts/src/main/kotlin/com/baseproject/tts/azure/AzureTtsProvider.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/tts/azure/AzureTtsProvider.kt
tts/src/main/kotlin/com/baseproject/tts/elevenlabs/ElevenLabsTtsProvider.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/tts/elevenlabs/ElevenLabsTtsProvider.kt
```

## Implementation Steps

1. **Create target directories:**
   ```bash
   mkdir -p ai-speech/src/main/kotlin/com/baseproject/aispeech/tts/{android,azure,elevenlabs}
   ```

2. **`git mv` all 5 files** preserving blame.

3. **Rewrite package declarations:**
   - `package com.baseproject.tts` → `package com.baseproject.aispeech.tts`
   - `package com.baseproject.tts.android` → `package com.baseproject.aispeech.tts.android`
   - `package com.baseproject.tts.azure` → `package com.baseproject.aispeech.tts.azure`
   - `package com.baseproject.tts.elevenlabs` → `package com.baseproject.aispeech.tts.elevenlabs`

4. **Rewrite intra-package imports:**
   ```bash
   find ai-speech/src/main/kotlin/com/baseproject/aispeech/tts -name "*.kt" \
     -exec sed -i '' 's|com\.baseproject\.tts|com.baseproject.aispeech.tts|g' {} \;
   ```

5. **Compile-check:**
   ```bash
   ./gradlew :ai-speech:compileDebugKotlin
   ```
   Must succeed (after Phase 2 also done, since coexistence in same module).

## Todo List
- [ ] Create target tts directory tree
- [ ] `git mv` TtsEvent.kt
- [ ] `git mv` TtsProvider.kt
- [ ] `git mv` android/AndroidTtsProvider.kt
- [ ] `git mv` azure/AzureTtsProvider.kt
- [ ] `git mv` elevenlabs/ElevenLabsTtsProvider.kt
- [ ] Rewrite package declarations
- [ ] sed import rewrite
- [ ] `./gradlew :ai-speech:compileDebugKotlin` passes

## Success Criteria
- 5 source files exist under `aispeech.tts.*`
- 0 files remain under `tts/src/main/kotlin/com/baseproject/tts/`
- `:ai-speech` compiles
- `git log --follow` works on moved files

## Risk Assessment
- **Coroutines version coordination** — old `:tts` declared `api(libs.kotlinx.coroutines.android)`. Confirm `:ai-speech`'s declaration matches. (Already in phase 1 build.gradle snippet.)
- **Azure SDK compileOnly version** — old `:tts` pinned `1.44.0`. Phase 1 build.gradle uses the same pin. If user wants to bump later, separate task.

## Next Steps
- Phase 5 (character) depends on this phase — `TutorCharacter` must import from `aispeech.tts.TtsProvider`
- Phase 4 (pronunciation) is independent — can run in parallel
