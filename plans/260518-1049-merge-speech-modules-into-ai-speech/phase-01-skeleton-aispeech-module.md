---
phase: 1
title: Skeleton :ai-speech module
status: pending
priority: critical
effort: 30min
---

# Phase 1 — Skeleton `:ai-speech` module

## Context Links
- Plan overview: [plan.md](plan.md)
- Reference: existing `:tts/build.gradle.kts` and `:stt/build.gradle.kts` for combined dependency shape

## Overview
- **Priority:** critical (blocks all other phases)
- **Status:** pending
- Create empty `:ai-speech` module shell with consolidated Gradle config + manifest + source directory layout. No code yet — just the container.

## Requirements
- Module compiles empty (`./gradlew :ai-speech:assembleDebug` succeeds with no sources)
- All consolidated dependencies declared (consumer of all 4 old modules' deps)
- Compile-only declarations for heavy/optional deps (Azure SDK, Spine)
- Single AndroidManifest with permissions union of the 4 old manifests

## Related Code Files

**Create:**
- `ai-speech/build.gradle.kts`
- `ai-speech/consumer-rules.pro`
- `ai-speech/src/main/AndroidManifest.xml`
- `ai-speech/src/main/kotlin/com/baseproject/aispeech/.keep` (empty file to ensure dir created — delete after Phase 2 starts)

**Modify:**
- `settings.gradle.kts` — add `include(":ai-speech")` (do NOT yet remove the 4 old modules; Phase 6 removes them)

## Implementation Steps

1. Read the 4 old manifests; collect declared permissions (likely: `RECORD_AUDIO`, `INTERNET`):
   ```
   stt/src/main/AndroidManifest.xml
   tts/src/main/AndroidManifest.xml
   speech/src/main/AndroidManifest.xml
   charactorspeak/src/main/AndroidManifest.xml
   ```
2. Create `ai-speech/src/main/AndroidManifest.xml` with `<manifest>` tag + union of permissions.
3. Create `ai-speech/build.gradle.kts` with the consolidated config (see snippet below).
4. Copy `stt/consumer-rules.pro` → `ai-speech/consumer-rules.pro` (or merge if other modules also have one).
5. Edit `settings.gradle.kts` — add `include(":ai-speech")` line at the bottom. Do NOT remove old includes yet.
6. Run `./gradlew :ai-speech:assembleDebug` — must succeed with empty source set.

### `ai-speech/build.gradle.kts` reference
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

    // compileOnly — consumer apps must declare these at runtime
    compileOnly("com.microsoft.cognitiveservices.speech:client-sdk:1.44.0")
    compileOnly("com.esotericsoftware.spine:spine-android:4.2.12")

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

## Todo List
- [ ] Read existing AndroidManifest files from 4 old modules and collect permissions
- [ ] Create `ai-speech/src/main/AndroidManifest.xml` with consolidated permissions
- [ ] Create `ai-speech/build.gradle.kts` per snippet above
- [ ] Create `ai-speech/consumer-rules.pro` (copy from `:stt`)
- [ ] Add `include(":ai-speech")` to `settings.gradle.kts`
- [ ] Run `./gradlew :ai-speech:assembleDebug` — must succeed empty

## Success Criteria
- `./gradlew :ai-speech:assembleDebug` returns 0
- `ai-speech/` directory exists with `build.gradle.kts`, manifest, empty kotlin source dir
- `settings.gradle.kts` lists `:ai-speech` alongside (not replacing yet) the 4 old modules

## Risk Assessment
- **Manifest permission union missed** → Sample may fail at runtime (e.g., RECORD_AUDIO not requested). Mitigation: also check sample's manifest — runtime permissions there are the real gate.
- **Library version mismatch** between coroutines `api` declarations across old modules (1.7.3 in charactorspeak, libs.versions.toml elsewhere). Mitigation: pick the `libs.kotlinx.coroutines.android` alias version; verify it ≥ 1.7.3.

## Next Steps
- Phase 2, 3, 4 can run in parallel after this
