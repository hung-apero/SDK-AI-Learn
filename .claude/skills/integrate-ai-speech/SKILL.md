---
name: integrate-ai-speech
description: Use when integrating the `:ai-speech` Android library (published as `apero-inhouse:ai-speech`) into an app — speech-to-text, text-to-speech, animated tutor character with lip-sync, or pronunciation scoring. Covers Gradle wiring (published artifact or in-tree module), the `SpeechToTextProvider` / `TtsProvider` / `TutorCharacter` / `PronunciationAssessor` entry points, and the four capability-specific recipes in `references/`. Excludes UI design choices and backend account setup.
---

# Integrating `:ai-speech`

## What this library gives you

Pluggable Android building blocks for speech-driven apps. Four independent capabilities, each behind a small abstraction so you can swap providers without rewriting screens:

| Capability | Entry point | Providers | Reference |
|---|---|---|---|
| Speech-to-text | `SpeechToTextProvider` | Android (offline) / Gemini (cloud) | `references/integrate-stt.md` |
| Text-to-speech | `TtsProvider` | Android (offline) / Azure / ElevenLabs | `references/integrate-tts.md` |
| Animated tutor character | `TutorCharacter` | Spine 2D + lipsync + karaoke subtitles | `references/integrate-character-speaking.md` |
| Pronunciation scoring | `PronunciationAssessor` | `SimpleWordMatchAssessor` (drop-in) | `references/integrate-pronunciation.md` |

Capabilities compose: TTS feeds the character lipsync; STT feeds pronunciation scoring. Both pairings are spelled out in the matching reference.

## Decision: which reference do I load?

- "Recognize what the user said" → `references/integrate-stt.md`
- "Make the app speak" → `references/integrate-tts.md`
- "Show a talking avatar with mouth sync + subtitles" → `references/integrate-character-speaking.md` (and usually `integrate-tts.md`)
- "Score how well a learner pronounced a phrase" → `references/integrate-pronunciation.md` (and usually `integrate-stt.md`)

Multiple capabilities → load multiple references in parallel.

## Gradle wiring (once)

Pick the option that matches how your project gets the library.

### Option A — Published artifact (recommended for downstream apps)

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenLocal()                                              // for local dev / testing
        maven("https://artifactory.apero.vn/artifactory/gradle-release/") {
            credentials {
                username = providers.gradleProperty("artifactoryUser").orNull
                    ?: System.getenv("ARTIFACTORY_USER") ?: ""
                password = providers.gradleProperty("artifactoryPassword").orNull
                    ?: System.getenv("ARTIFACTORY_PASSWORD") ?: ""
            }
        }
        google()
        mavenCentral()
    }
}
```

`app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("apero-inhouse:ai-speech:0.1.0")  // bump as releases ship
}
```

### Option B — In-tree module (when you vendor the source)

`settings.gradle.kts`:

```kotlin
include(":app")
include(":ai-speech")
```

`app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":ai-speech"))
}
```

### Compile target

```kotlin
android {
    compileSdk = 36
    defaultConfig {
        minSdk = 24           // bump to 26 if you use AzureTtsProvider
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
```

### Optional runtime deps (only if you use that provider)

The library declares these as `compileOnly` — the host app adds them at runtime so apps that don't use Azure / Spine don't pay the size cost:

```kotlin
dependencies {
    // Only if using AzureTtsProvider — also forces minSdk = 26 (transitive azure-core).
    implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.44.0")

    // Only if using TutorCharacter (Spine animation).
    implementation("com.esotericsoftware.spine:spine-android:4.2.12")
}
```

When using Azure: trim ABIs and use modern jniLibs packaging — Azure ships native `.so` files for many architectures.

```kotlin
android {
    defaultConfig {
        ndk { abiFilters += listOf("x86_64", "arm64-v8a") }
    }
    packaging {
        resources.excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        jniLibs { useLegacyPackaging = false }   // required for Android 15+ 16KB pages
    }
}
```

## Manifest basics

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />

<!-- Required on Android 11+ for the system SpeechRecognizer service to be visible. -->
<queries>
    <intent>
        <action android:name="android.speech.RecognitionService" />
    </intent>
</queries>
```

`RECORD_AUDIO` is a runtime permission — request before calling `provider.startListening()`. `INTERNET` is needed for any cloud provider (Gemini STT, Azure / ElevenLabs TTS).

## Module-wide gotchas

- **No bundled Compose UI.** The library ships no `@Composable` views. Reference files include minimal Compose recipes, but the data models (`SttResult`, `TtsEvent`, `PronunciationResult`) are the contract — render them however your design system requires.
- **Always call `prepare(context)` first.** Every `SpeechToTextProvider` and `TtsProvider` requires `prepare(context)` (suspending) before any `start*`/`speak`. Call it inside `LaunchedEffect` or a `lifecycleScope.launch`.
- **Lifecycle: `prepare → use → shutdown/destroy`.** Releasing matters for the Android STT (`SpeechRecognizer`) and Android TTS — leaking either keeps mic / engine handles alive across screens.
- **`compileOnly` deps are silent at compile time but crash at runtime.** If you instantiate `AzureTtsProvider` without adding the Azure runtime dep, you'll see `NoClassDefFoundError` only when the screen loads. Add the right runtime dep when you wire each provider.
- **Version is set in root `gradle.properties` of the library repo** (`sdkVersion=0.1.0`). The `0.1.0` examples above will drift — check the latest published version.

## Verifying it works

Smoke test before wiring real UI:

1. Add `implementation("apero-inhouse:ai-speech:0.1.0")` (or `project(":ai-speech")`).
2. In any Activity, request `RECORD_AUDIO`, then:
   ```kotlin
   val tts = AndroidTtsProvider()
   lifecycleScope.launch {
       tts.prepare(this@MyActivity)
       tts.speak("Hello from ai-speech.", utteranceId = "smoke-1")
   }
   ```
3. You should hear the system voice. If silent, check the audio media stream volume and that `prepare` actually returned (collect `tts.events` to see `Started`/`Error`).

If that works, dive into the capability you need.

## Next

Load the matching `references/integrate-{capability}.md` for the integration you're doing — they each follow the same shape: public surface → provider decision → wiring → minimal recipe → gotchas.
