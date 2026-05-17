# `:sample` module for `:charactorspeak` — design

**Date:** 2026-05-17
**Status:** Approved (pending implementation plan)

## Goal

Create a new standalone Android application module named `:sample` that
demonstrates the `:charactorspeak` library. The sample is a verbatim port of
the demo app in the original source project at
`C:\Users\hung0\learna-clone-starter\tutor-sdk\app`, retargeted onto this
repo's `:charactorspeak` module (which is the same SDK code relocated here).

The sample must be runnable as its own APK, independent of the existing
`:app` module (which is a different application — "scarychats" — built with
Compose + Koin + Firebase and unrelated to the tutor SDK).

## Non-goals

- Not refactoring `:charactorspeak` itself. Its pre-existing inconsistencies
  (hardcoded non-catalog dependencies, Azure SDK version skew vs the version
  catalog) are out of scope for this task.
- Not migrating the demo UI to Jetpack Compose. The port stays View-based to
  match the original 1:1.
- Not auto-provisioning Azure / ElevenLabs credentials. The sample builds and
  runs with empty keys; it falls back to Android system TTS when no cloud
  keys are configured.

## Architecture

A single new module sitting alongside the existing modules:

```
D:\src\SDK-AI-Learn\
├─ settings.gradle.kts         (+ include(":sample"))
├─ app\                        existing — Compose "scarychats" app, unrelated
├─ charactorspeak\             existing — the tutor SDK (was tutor-sdk/sdk)
├─ speech\
├─ stt\
└─ sample\                     NEW
   ├─ build.gradle.kts
   └─ src\main\
      ├─ AndroidManifest.xml
      ├─ assets\spine\
      │     tutor_character.atlas
      │     tutor_character.json
      │     tutor_character.png
      ├─ kotlin\com\baseproject\sample\
      │     MainActivity.kt
      └─ res\
         ├─ layout\activity_main.xml
         └─ values\
            ├─ strings.xml
            └─ themes.xml
```

`:sample` depends on `:charactorspeak`. The reverse is not true — the SDK
remains unaware of any consumer.

## Module configuration

`sample/build.gradle.kts`:

- **Plugins**: `com.android.application`, `org.jetbrains.kotlin.android`.
  Declared with raw `id(...)` (not catalog aliases) so the port stays close
  to the original. The catalog also doesn't currently expose appcompat,
  material, constraintlayout, or cardview which this UI needs.
- **`namespace` / `applicationId`**: `com.baseproject.sample`. Activity
  package is the same.
- **SDKs**: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`. The 26 floor
  is dictated by the Azure Speech SDK transitive `azure-core 1.50` which
  uses Java 9+ classes that can't be dexed for lower minSdks.
- **Java**: source/target compatibility 17, Kotlin `jvmTarget = "17"`. This
  matches `:charactorspeak` (consumers of an SDK compiled at JVM 17 must be
  ≥ 17). It's different from `:app`'s JVM 11 setting, which is acceptable
  because modules compile independently and no kotlin inline calls cross
  between `:app` and `:sample`.
- **`buildFeatures`**: `viewBinding = true`, `buildConfig = true`.
- **`defaultConfig.ndk.abiFilters`**: `["x86_64", "arm64-v8a"]` — limits the
  Azure native `.so` libraries shipped in the APK.
- **`packaging`**:
  - `resources.excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1")` to
    de-duplicate transitive Azure license files.
  - `jniLibs.useLegacyPackaging = false` for Android 15+ 16 KB
    page-aligned native libs.
- **BuildConfig fields** read from root `local.properties` (gitignored). All
  default to safe empty/auto values so a developer without cloud credentials
  can still build & run:
  - `TTS_PROVIDER` (default `"auto"`)
  - `ANDROID_TTS_VOICE` (default `""`)
  - `AZURE_SPEECH_KEY`, `AZURE_SPEECH_REGION` (default `""`)
  - `AZURE_SPEECH_VOICE` (default `"en-US-AriaNeural"`)
  - `ELEVENLABS_API_KEY` (default `""`)
  - `ELEVENLABS_VOICE_ID` (default `"21m00Tcm4TlvDq8ikWAM"`)
  - `ELEVENLABS_MODEL_ID` (default `"eleven_monolingual_v1"`)

### Dependencies

```kotlin
implementation(project(":charactorspeak"))
implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.44.0")
implementation("androidx.appcompat:appcompat:1.7.0")
implementation("com.google.android.material:material:1.12.0")
implementation("androidx.constraintlayout:constraintlayout:2.1.4")
implementation("androidx.cardview:cardview:1.0.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
```

The Azure Speech SDK is declared `compileOnly` inside `:charactorspeak`, so
any app exercising the Azure path must supply the runtime — that's the
`client-sdk:1.44.0` line above. **Version**: 1.44.0 matches what
`:charactorspeak` was compiled against. The project's version catalog
(`gradle/libs.versions.toml`) currently lists `azureSpeechSdk = "1.40.0"`,
which is inconsistent; aligning the catalog is a separate cleanup.

## Code & asset port

### `MainActivity.kt`

Ported verbatim from
`C:\Users\hung0\learna-clone-starter\tutor-sdk\app\src\main\kotlin\com\apero\tutor\demo\MainActivity.kt`.
The **only** change is the file's `package` declaration:

```diff
- package com.apero.tutor.demo
+ package com.baseproject.sample
```

All SDK imports (`com.apero.tutor.sdk.*`) remain unchanged because
`:charactorspeak` keeps the original SDK namespace `com.apero.tutor.sdk`.
The implicit `BuildConfig` reference resolves to
`com.baseproject.sample.BuildConfig` and supplies the same field names.

The activity demonstrates:

- 3-button TTS provider picker (Android / Azure / ElevenLabs) using
  `TutorCharacter.setTtsProvider`
- 3-button speech rate picker (0.75× / 1.0× / 1.5×) using
  `TutorCharacter.speechRate`
- Free-text "Speak" via `TutorCharacter.speak(text)`
- "Simulate SSE" button that drives `TutorCharacter.speakFromFlow(...)` from
  a token-by-token flow
- Happy / Neutral emotion via `setEmotion(...)`, Blink via `triggerBlink()`,
  Stop via `stop()`
- Status line wired to the active `TtsProvider.events` flow
- First-launch copy of the bundled Spine assets from `/assets/spine` into
  `filesDir/models/aria` (mimics the production "download model" workflow)

### Layout, theme, strings

- `res/layout/activity_main.xml`: copied verbatim. Uses `SpineView`,
  `CardView`, `ConstraintLayout`, plus standard `Button` / `EditText` /
  `TextView`.
- `res/values/themes.xml`: copied; theme renamed `Theme.TutorDemo` →
  `Theme.Sample`.
- `res/values/strings.xml`: copied; app name updated.

### Manifest

```xml
<manifest>
  <uses-permission android:name="android.permission.INTERNET" />
  <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
  <application
      android:label="Charactorspeak Sample"
      android:theme="@style/Theme.Sample">
    <activity android:name=".MainActivity" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
      </intent-filter>
    </activity>
  </application>
</manifest>
```

### Spine assets

Three binary files copied as-is from
`C:\Users\hung0\learna-clone-starter\tutor-sdk\app\src\main\assets\spine\`:

- `tutor_character.atlas`
- `tutor_character.json`
- `tutor_character.png`

These are not editable text and must be copied with a file-copy operation
(PowerShell `Copy-Item` / `Bash cp`), not written with the `Write` tool.

## Settings file change

Append to `D:\src\SDK-AI-Learn\settings.gradle.kts`:

```kotlin
include(":sample")
```

After this change the build will include five Android modules: `:app`,
`:speech`, `:stt`, `:charactorspeak`, `:sample`.

## Data flow (recap from the SDK design)

```
MainActivity
  └─ TutorCharacter (from :charactorspeak)
       ├─ SpineView (com.esotericsoftware.spine:spine-android)
       ├─ TtsProvider (Android | Azure | ElevenLabs)  ← swappable at runtime
       ├─ LipSyncEngine (drives Spine mouth shapes)
       └─ KaraokeSubtitleRenderer (highlights spoken word in TextView)

TtsProvider.events ──► TutorCharacter ──► Spine / lip-sync / subtitle
                  ──► MainActivity (status line updates)
```

## Error handling

The activity inherits the SDK's error contract:

- `TutorCharacter.load(context)` may throw if assets are missing → caught in
  MainActivity, surfaced as `status.text = "Load failed: …"`.
- `TtsEvent.Error` events update the status line with the error message.
- Switching providers wraps `setTtsProvider` in try/catch; on failure the
  status line shows `"Switch failed: …"` and the previous provider remains
  in effect.
- Empty credentials: `resolveInitialKind()` already falls back to Android
  TTS when Azure/ElevenLabs keys are blank, so the app never crashes at
  launch from missing config.

## Testing strategy

No new automated tests are added by this module — the sample's purpose is
manual verification that `:charactorspeak` integrates correctly. The SDK
itself is what would carry tests, and adding tests there is out of scope.

Manual verification after implementation:

1. Gradle sync succeeds without classpath / version conflicts.
2. `./gradlew :sample:assembleDebug` builds clean.
3. App installs and launches on a min-API-26 emulator; SpineView shows the
   character.
4. "Speak" button with the default text produces audio + visible lip-sync +
   karaoke subtitle highlight under the Android TTS provider (no keys
   needed).
5. Status line transitions through `Loading…` → `Ready` → `Speaking` →
   `Ready`.

If the developer has Azure or ElevenLabs credentials in `local.properties`,
the corresponding picker button switches the provider live.

## Known caveats

- **Azure SDK version skew**: `:sample` uses runtime 1.44.0 to match
  `:charactorspeak`'s `compileOnly` 1.44.0. The catalog still lists 1.40.0
  unused. Document but don't fix in this task.
- **JVM target divergence**: `:app` is 11, `:sample` and `:charactorspeak`
  are 17. Cross-module inline calls between `:app` and `:sample` would be a
  problem — but there are none, so it's fine.
- **Two launchable apps**: building the project produces two APKs (`:app`
  and `:sample`). Document this in the module's location next to the others.
