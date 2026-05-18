---
phase: 4
title: Migrate :speech pronunciation to aispeech.pronunciation
status: pending
priority: medium
effort: 20min
---

# Phase 4 — Migrate `:speech` pronunciation → `aispeech.pronunciation`

## Context Links
- Plan overview: [plan.md](plan.md)
- Phase 1 (skeleton): [phase-01-skeleton-aispeech-module.md](phase-01-skeleton-aispeech-module.md)

## Overview
- **Priority:** medium (can run after Phase 1; no other phase depends on this)
- **Status:** pending
- Move ONLY the pronunciation subpackage from `:speech`. Drop everything else: `tts/`, `stt/`, `di/`, and `cpp/` (whisper.cpp). The deleted code is orphaned (no callers).

## Key Insights
- `:speech` module is the messy one — it has divergent old interfaces (abstract classes) and Koin DI nobody uses.
- Pronunciation code (`PronunciationAssessor`, `PronunciationResult`, `SimpleWordMatchAssessor`) is self-contained — does not depend on speech's TTS/STT interfaces.
- whisper.cpp source has no Kotlin caller (verified by grep `whisper` across kotlin sources).

## Requirements
- 3 pronunciation files moved with `git mv` to new namespace
- All non-pronunciation `:speech` files left in place (deleted in Phase 6 along with the module)
- No Koin dependency carries into `:ai-speech`

## Related Code Files

**Move (git mv) — 3 source files:**
```
speech/src/main/java/com/baseproject/speech/pronunciation/PronunciationAssessor.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/pronunciation/PronunciationAssessor.kt
speech/src/main/java/com/baseproject/speech/pronunciation/PronunciationResult.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/pronunciation/PronunciationResult.kt
speech/src/main/java/com/baseproject/speech/pronunciation/simple/SimpleWordMatchAssessor.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/pronunciation/simple/SimpleWordMatchAssessor.kt
```

**Leave behind (deleted entirely in Phase 6 with the module):**
- `speech/src/main/java/com/baseproject/speech/di/SpeechModule.kt`
- `speech/src/main/java/com/baseproject/speech/tts/**` (5 files)
- `speech/src/main/java/com/baseproject/speech/stt/**` (4 files)
- `speech/src/main/cpp/whisper.cpp/` (entire native source tree)

## Implementation Steps

1. **Verify pronunciation isolation:**
   ```bash
   grep -rn "import com.baseproject.speech" speech/src/main/java/com/baseproject/speech/pronunciation/
   ```
   Expected: zero results (or only intra-package `pronunciation.*` imports). If anything imports from `speech.tts`/`speech.stt`/`speech.di`, address before migrating.

2. **Verify no external pronunciation consumers:**
   ```bash
   grep -rn "com\.baseproject\.speech\.pronunciation" sample/ app/ stt/ tts/ charactorspeak/
   ```
   Expected: zero (sample doesn't use pronunciation today). If found, note for Phase 6 migration.

3. **Create target dirs:**
   ```bash
   mkdir -p ai-speech/src/main/kotlin/com/baseproject/aispeech/pronunciation/simple
   ```

4. **`git mv` 3 files** preserving blame.

5. **Rewrite package declarations:**
   - `package com.baseproject.speech.pronunciation` → `package com.baseproject.aispeech.pronunciation`
   - `package com.baseproject.speech.pronunciation.simple` → `package com.baseproject.aispeech.pronunciation.simple`

6. **Rewrite imports:**
   ```bash
   find ai-speech/src/main/kotlin/com/baseproject/aispeech/pronunciation -name "*.kt" \
     -exec sed -i '' 's|com\.baseproject\.speech\.pronunciation|com.baseproject.aispeech.pronunciation|g' {} \;
   ```

7. **Compile-check:**
   ```bash
   ./gradlew :ai-speech:compileDebugKotlin
   ```

## Todo List
- [ ] Grep verify pronunciation has no cross-package speech imports
- [ ] Grep verify no external consumers of pronunciation
- [ ] Create target pronunciation dir tree
- [ ] `git mv` PronunciationAssessor.kt
- [ ] `git mv` PronunciationResult.kt
- [ ] `git mv` simple/SimpleWordMatchAssessor.kt
- [ ] Rewrite package declarations
- [ ] sed import rewrite
- [ ] `./gradlew :ai-speech:compileDebugKotlin` passes

## Success Criteria
- 3 source files exist under `aispeech.pronunciation.*`
- `:ai-speech` compiles without Koin dependency
- `speech/src/main/java/com/baseproject/speech/pronunciation/` is empty (deleted in Phase 6)

## Risk Assessment
- **Hidden import from pronunciation to speech.di/speech.tts/speech.stt** → would force keeping more than just pronunciation. Mitigation: grep step 1 catches it.
- **Hidden external consumer in sample/app** → would break compile in Phase 7. Mitigation: grep step 2 catches it; rewrite imports if found.

## Next Steps
- Independent of Phase 5
- Phase 6 deletes the rest of `:speech` (tts/stt/di/cpp)
