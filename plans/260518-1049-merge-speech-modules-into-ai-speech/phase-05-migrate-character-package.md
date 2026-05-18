---
phase: 5
title: Migrate :charactorspeak (no tts subpackage) to aispeech.character
status: pending
priority: high
effort: 45min
---

# Phase 5 — Migrate `:charactorspeak` non-tts → `aispeech.character`

## Context Links
- Plan overview: [plan.md](plan.md)
- Phase 3 (TTS) MUST be complete before this — character depends on `aispeech.tts.TtsProvider`

## Overview
- **Priority:** high
- **Status:** pending
- **Depends on:** Phase 3
- Move 9 non-tts source files from `:charactorspeak` to `aispeech.character.*`. DELETE the 5 tts files (literal duplicates of `:tts`). Rewire `TutorCharacter` to consume `aispeech.tts.TtsProvider` instead of `apero.tutor.sdk.tts.TtsProvider`.

## Key Insights
- charactorspeak's `tts/` subpackage is a byte-for-byte clone of `:tts` (interfaces + 3 providers). Verified in brainstorm.
- `TutorCharacter` is the public entry point — it imports `tts.TtsProvider` and is the only place that needs interface rewiring.
- Spine is `compileOnly` in `:ai-speech` (per Phase 1). Same constraint as before.
- charactorspeak's `maven-publish` JitPack block is DROPPED (user confirmed no external consumers).

## Requirements
- 9 source files moved with `git mv` to `aispeech.character.*`
- 5 tts duplicate files DELETED (not moved)
- `TutorCharacter` and any other character file that referenced `com.apero.tutor.sdk.tts.*` now imports `com.baseproject.aispeech.tts.*`
- `:ai-speech` compiles after this phase

## Related Code Files

**Move (git mv) — 9 source files:**
```
charactorspeak/src/main/kotlin/com/apero/tutor/sdk/TutorCharacter.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/character/TutorCharacter.kt
charactorspeak/src/main/kotlin/com/apero/tutor/sdk/TutorCharacterConfig.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/character/TutorCharacterConfig.kt
charactorspeak/src/main/kotlin/com/apero/tutor/sdk/animation/AnimationContract.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/character/animation/AnimationContract.kt
charactorspeak/src/main/kotlin/com/apero/tutor/sdk/animation/SpineController.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/character/animation/SpineController.kt
charactorspeak/src/main/kotlin/com/apero/tutor/sdk/lipsync/LipSyncEngine.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/character/lipsync/LipSyncEngine.kt
charactorspeak/src/main/kotlin/com/apero/tutor/sdk/lipsync/VisemeEvent.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/character/lipsync/VisemeEvent.kt
charactorspeak/src/main/kotlin/com/apero/tutor/sdk/subtitle/SubtitleRenderer.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/character/subtitle/SubtitleRenderer.kt
charactorspeak/src/main/kotlin/com/apero/tutor/sdk/subtitle/KaraokeSubtitleRenderer.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/character/subtitle/KaraokeSubtitleRenderer.kt
charactorspeak/src/main/kotlin/com/apero/tutor/sdk/sse/SseBuffer.kt
  → ai-speech/src/main/kotlin/com/baseproject/aispeech/character/sse/SseBuffer.kt
```

**Delete (`git rm`) — 5 duplicate tts files:**
```
charactorspeak/src/main/kotlin/com/apero/tutor/sdk/tts/TtsProvider.kt
charactorspeak/src/main/kotlin/com/apero/tutor/sdk/tts/TtsEvent.kt
charactorspeak/src/main/kotlin/com/apero/tutor/sdk/tts/AndroidTtsProvider.kt
charactorspeak/src/main/kotlin/com/apero/tutor/sdk/tts/AzureTtsProvider.kt
charactorspeak/src/main/kotlin/com/apero/tutor/sdk/tts/ElevenLabsTtsProvider.kt
```

## Implementation Steps

1. **Identify all files that import `com.apero.tutor.sdk.tts.*` in charactorspeak character code:**
   ```bash
   grep -rn "import com.apero.tutor.sdk.tts" charactorspeak/src/main/kotlin/com/apero/tutor/sdk/
   ```
   Likely candidates: `TutorCharacter.kt` (uses `TtsProvider`, possibly `TtsEvent`).

2. **Create target dirs:**
   ```bash
   mkdir -p ai-speech/src/main/kotlin/com/baseproject/aispeech/character/{animation,lipsync,subtitle,sse}
   ```

3. **`git mv` all 9 non-tts files** preserving blame.

4. **`git rm` the 5 tts duplicate files** in charactorspeak.

5. **Rewrite package declarations** in each moved file:
   - `package com.apero.tutor.sdk` → `package com.baseproject.aispeech.character`
   - `package com.apero.tutor.sdk.animation` → `package com.baseproject.aispeech.character.animation`
   - `package com.apero.tutor.sdk.lipsync` → `package com.baseproject.aispeech.character.lipsync`
   - `package com.apero.tutor.sdk.subtitle` → `package com.baseproject.aispeech.character.subtitle`
   - `package com.apero.tutor.sdk.sse` → `package com.baseproject.aispeech.character.sse`

6. **Rewrite imports** — two rewrites needed:
   ```bash
   # intra-character imports: apero.tutor.sdk → aispeech.character
   find ai-speech/src/main/kotlin/com/baseproject/aispeech/character -name "*.kt" \
     -exec sed -i '' 's|com\.apero\.tutor\.sdk\.tts|com.baseproject.aispeech.tts|g' {} \;
   find ai-speech/src/main/kotlin/com/baseproject/aispeech/character -name "*.kt" \
     -exec sed -i '' 's|com\.apero\.tutor\.sdk|com.baseproject.aispeech.character|g' {} \;
   ```
   Note: order matters — rewrite `tts` first so it doesn't accidentally become `character.tts`.

7. **Compile-check:**
   ```bash
   ./gradlew :ai-speech:compileDebugKotlin
   ```
   Phase 3 must be complete for this to succeed (depends on `aispeech.tts.TtsProvider`).

## Todo List
- [ ] Grep `com.apero.tutor.sdk.tts` imports in charactorspeak character code
- [ ] Create target character dir tree (animation, lipsync, subtitle, sse)
- [ ] `git mv` TutorCharacter.kt + TutorCharacterConfig.kt
- [ ] `git mv` animation/* (2 files)
- [ ] `git mv` lipsync/* (2 files)
- [ ] `git mv` subtitle/* (2 files)
- [ ] `git mv` sse/SseBuffer.kt
- [ ] `git rm` charactorspeak/src/main/kotlin/com/apero/tutor/sdk/tts/* (5 files)
- [ ] Rewrite package declarations (5 packages)
- [ ] sed import rewrite: `tts` first, then root namespace
- [ ] `./gradlew :ai-speech:compileDebugKotlin` passes

## Success Criteria
- 9 source files exist under `aispeech.character.*`
- 0 files remain under `charactorspeak/src/main/kotlin/com/apero/tutor/sdk/`
- `TutorCharacter` imports `com.baseproject.aispeech.tts.TtsProvider` (not `apero.tutor.sdk.tts`)
- `:ai-speech` compiles
- `git log --follow TutorCharacter.kt` shows full history

## Risk Assessment
- **Sed order bug** — running broader pattern first would mangle `tts` paths into `character.tts`. Mitigation: rewrite `apero.tutor.sdk.tts` → `aispeech.tts` FIRST, then `apero.tutor.sdk` → `aispeech.character`.
- **Imports from charactorspeak.tts in non-tts files** — covered by sed step. Worth double-checking with grep after.
- **Hidden public API on `TutorCharacter`** that exposes `apero.tutor.sdk.tts.TtsEvent` in method signatures — would break sample app callers. Verify by reading public methods of TutorCharacter and matching against sample's usage; Phase 6 handles sample-side rewrites.

## Security Considerations
- None new. Azure/Spine credentials/runtimes still supplied by consumer.

## Next Steps
- Phase 6 updates `:sample` and removes old modules
