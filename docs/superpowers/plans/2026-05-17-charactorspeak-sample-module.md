# `:sample` Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `:sample` Android application module that demonstrates the `:charactorspeak` SDK by porting the demo app from `C:\Users\hung0\learna-clone-starter\tutor-sdk\app`.

**Architecture:** A single new module sibling to `:app`, `:speech`, `:stt`, `:charactorspeak`. Standalone APK with a single launcher activity (`MainActivity`) using View-based UI. Depends on `:charactorspeak` and brings the Azure Speech SDK runtime that the SDK declares as `compileOnly`. Java 17 / minSdk 26.

**Tech Stack:** Android Gradle Plugin 8.13.2 (from `gradle/libs.versions.toml`), Kotlin 2.1.21, Java 17, AppCompat + Material + ConstraintLayout + CardView (View system, no Compose), ViewBinding, Spine Android runtime (transitively via `:charactorspeak`), Microsoft Cognitive Services Speech SDK 1.44.0 runtime.

**Spec:** `docs/superpowers/specs/2026-05-17-charactorspeak-sample-module-design.md`

**Working directory for all commands:** `D:\src\SDK-AI-Learn`

**Note on commit prefix:** The repo currently uses Conventional-Commit-ish prefixes (`docs:`, `feat:`, `fix:`); this plan follows that pattern.

**Note on TDD:** The spec explicitly excludes automated tests for this module — it's a manual-verification demo of an SDK. Each task's verification step is therefore "Gradle sync / build succeeds", not "test passes". The final task is a manual smoke-test on emulator.

---

## File map

Files this plan creates or modifies:

| Path | Action | Responsibility |
|---|---|---|
| `settings.gradle.kts` | modify (append one line) | register `:sample` in the build |
| `sample/build.gradle.kts` | create | module config: plugins, namespace, BuildConfig fields, deps |
| `sample/src/main/AndroidManifest.xml` | create | declare launcher activity + permissions + theme |
| `sample/src/main/res/layout/activity_main.xml` | create | demo UI: SpineView + subtitle + control panel |
| `sample/src/main/res/values/themes.xml` | create | `Theme.Sample` style |
| `sample/src/main/res/values/strings.xml` | create | app name |
| `sample/src/main/assets/spine/tutor_character.atlas` | copy (binary) | Spine region map |
| `sample/src/main/assets/spine/tutor_character.json` | copy (binary) | Spine skeleton |
| `sample/src/main/assets/spine/tutor_character.png` | copy (binary) | Spine texture |
| `sample/src/main/kotlin/com/baseproject/sample/MainActivity.kt` | create (ported) | demo activity driving `TutorCharacter` |

No existing files outside `settings.gradle.kts` are touched.

---

## Task 1: Scaffold module + register in settings

Goal: Create the module directory with a build script and a stub manifest, register it with Gradle, and confirm sync passes. After this task the module exists but has no UI yet.

**Files:**
- Create: `sample/build.gradle.kts`
- Create: `sample/src/main/AndroidManifest.xml` (stub — no activity yet)
- Modify: `settings.gradle.kts:26` (append `include(":sample")`)

- [ ] **Step 1: Create `sample/build.gradle.kts`**

Create file `D:\src\SDK-AI-Learn\sample\build.gradle.kts` with this exact content:

```kotlin
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Read TTS credentials from root local.properties (gitignored).
// Missing keys fall back to defaults; the demo runs without any cloud credentials.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val ttsProvider:  String = localProps.getProperty("TTS_PROVIDER",         "auto")
val androidVoice: String = localProps.getProperty("ANDROID_TTS_VOICE",    "")
val azureKey:     String = localProps.getProperty("AZURE_SPEECH_KEY",     "")
val azureRegion:  String = localProps.getProperty("AZURE_SPEECH_REGION",  "")
val azureVoice:   String = localProps.getProperty("AZURE_SPEECH_VOICE",   "en-US-AriaNeural")
val elevenKey:    String = localProps.getProperty("ELEVENLABS_API_KEY",   "")
val elevenVoice:  String = localProps.getProperty("ELEVENLABS_VOICE_ID",  "21m00Tcm4TlvDq8ikWAM")
val elevenModel:  String = localProps.getProperty("ELEVENLABS_MODEL_ID",  "eleven_monolingual_v1")

android {
    namespace  = "com.baseproject.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.baseproject.sample"
        // 26: required by Azure Speech SDK 1.44.0 transitive (azure-core 1.50)
        // which uses Java 9+ classes that can't be dexed for minSdk < 26.
        minSdk        = 26
        targetSdk     = 36
        versionCode   = 1
        versionName   = "1.0"

        buildConfigField("String", "TTS_PROVIDER",        "\"$ttsProvider\"")
        buildConfigField("String", "ANDROID_TTS_VOICE",   "\"$androidVoice\"")
        buildConfigField("String", "AZURE_SPEECH_KEY",    "\"$azureKey\"")
        buildConfigField("String", "AZURE_SPEECH_REGION", "\"$azureRegion\"")
        buildConfigField("String", "AZURE_SPEECH_VOICE",  "\"$azureVoice\"")
        buildConfigField("String", "ELEVENLABS_API_KEY",  "\"$elevenKey\"")
        buildConfigField("String", "ELEVENLABS_VOICE_ID", "\"$elevenVoice\"")
        buildConfigField("String", "ELEVENLABS_MODEL_ID", "\"$elevenModel\"")

        // Azure Speech SDK ships native .so libs — limit ABI to what the emulator
        // actually uses to keep build time / APK size reasonable while testing.
        ndk { abiFilters += listOf("x86_64", "arm64-v8a") }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")

    packaging {
        // Multiple Azure modules bring duplicate META-INF licence files
        resources.excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        // Required for Android 15+ 16 KB page-aligned native libs.
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(project(":charactorspeak"))
    // Azure Speech SDK (charactorspeak declares this compileOnly — the sample brings the runtime).
    // 1.44.0 matches what :charactorspeak was compiled against.
    implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.44.0")

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
```

- [ ] **Step 2: Create stub `sample/src/main/AndroidManifest.xml`**

Create file `D:\src\SDK-AI-Learn\sample\src\main\AndroidManifest.xml` with this exact content (no activity yet — added in Task 4):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="false"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="Charactorspeak Sample" />
</manifest>
```

- [ ] **Step 3: Register module in `settings.gradle.kts`**

Edit `D:\src\SDK-AI-Learn\settings.gradle.kts`. Append `include(":sample")` after the existing `include(":charactorspeak")` line.

The file should end with:

```kotlin
rootProject.name = "AIP396 AI Learn Language"
include(":app")
include(":speech")
include(":stt")
include(":charactorspeak")
include(":sample")
```

- [ ] **Step 4: Verify Gradle sync**

Run from `D:\src\SDK-AI-Learn`:

```powershell
.\gradlew.bat :sample:tasks --no-daemon
```

Expected: command exits 0 and prints a task list containing `assembleDebug`, `clean`, etc. No "Could not resolve plugin", "Plugin already on classpath with different version", or "Project 'sample' not found" errors.

If `:charactorspeak` was previously not depended on by anything, this is also the first time the SDK gets built as a Gradle dep target. That's expected — sync will compile it.

- [ ] **Step 5: Commit**

```powershell
git add settings.gradle.kts sample/build.gradle.kts sample/src/main/AndroidManifest.xml
git commit -m "feat(sample): scaffold :sample module and register in settings"
```

---

## Task 2: Port resource files (layout, themes, strings)

Goal: Bring over the demo UI XML so the activity has something to inflate.

**Files:**
- Create: `sample/src/main/res/layout/activity_main.xml`
- Create: `sample/src/main/res/values/themes.xml`
- Create: `sample/src/main/res/values/strings.xml`

- [ ] **Step 1: Create `sample/src/main/res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Charactorspeak Sample</string>
</resources>
```

- [ ] **Step 2: Create `sample/src/main/res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.Sample" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="colorPrimary">#4AB8D8</item>
        <item name="colorPrimaryDark">#1E5070</item>
        <item name="colorAccent">#FF91A8</item>
        <item name="android:statusBarColor" tools:targetApi="l">@android:color/black</item>
    </style>
</resources>
```

- [ ] **Step 3: Create `sample/src/main/res/layout/activity_main.xml`**

This is copied verbatim from `C:\Users\hung0\learna-clone-starter\tutor-sdk\app\src\main\res\layout\activity_main.xml`. No edits required:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#F5F2EE">

    <com.esotericsoftware.spine.android.SpineView
        android:id="@+id/spineView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@id/subtitleCard"
        app:layout_constraintVertical_weight="2"/>

    <androidx.cardview.widget.CardView
        android:id="@+id/subtitleCard"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="16dp"
        android:layout_marginBottom="4dp"
        app:cardBackgroundColor="#CC1A1A2E"
        app:cardCornerRadius="12dp"
        app:cardElevation="0dp"
        app:layout_constraintBottom_toTopOf="@id/controls">

        <TextView
            android:id="@+id/subtitleText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:padding="12dp"
            android:textSize="16sp"
            android:gravity="center"
            android:lineSpacingMultiplier="1.3"
            android:text=""
            android:visibility="gone"/>
    </androidx.cardview.widget.CardView>

    <ScrollView
        android:id="@+id/controls"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:background="#FFFFFF"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintVertical_weight="1">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="12dp">

            <TextView
                android:id="@+id/status"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:padding="6dp"
                android:textSize="12sp"
                android:textColor="#555"
                android:fontFamily="monospace"
                android:text="Loading model..."/>

            <!-- TTS provider picker -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="TTS provider"
                android:textSize="11sp"
                android:textStyle="bold"
                android:textColor="#777"
                android:textAllCaps="true"
                android:layout_marginTop="6dp"
                android:layout_marginBottom="2dp"/>
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal">
                <Button android:id="@+id/btn_provider_android"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_margin="2dp"
                    android:textSize="11sp"
                    android:text="Android"/>
                <Button android:id="@+id/btn_provider_azure"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_margin="2dp"
                    android:textSize="11sp"
                    android:text="Azure"/>
                <Button android:id="@+id/btn_provider_elevenlabs"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_margin="2dp"
                    android:textSize="11sp"
                    android:text="ElevenLabs"/>
            </LinearLayout>

            <!-- Speech rate picker -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Speech rate"
                android:textSize="11sp"
                android:textStyle="bold"
                android:textColor="#777"
                android:textAllCaps="true"
                android:layout_marginTop="6dp"
                android:layout_marginBottom="2dp"/>
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal">
                <Button android:id="@+id/btn_rate_slow"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_margin="2dp"
                    android:textSize="11sp"
                    android:text="0.75x"/>
                <Button android:id="@+id/btn_rate_normal"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_margin="2dp"
                    android:textSize="11sp"
                    android:text="1.0x"/>
                <Button android:id="@+id/btn_rate_fast"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_margin="2dp"
                    android:textSize="11sp"
                    android:text="1.5x"/>
            </LinearLayout>

            <EditText
                android:id="@+id/edit_tts_text"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="Type something to say..."
                android:text="Nice to meet you! Let us learn English together."
                android:inputType="text"
                android:layout_marginTop="8dp"
                android:layout_marginBottom="4dp"/>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal">
                <Button android:id="@+id/btn_speak"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_margin="2dp"
                    android:text="Speak"
                    android:backgroundTint="#4CAF50"
                    android:textColor="#FFFFFF"/>
                <Button android:id="@+id/btn_demo_sse"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_margin="2dp"
                    android:text="Simulate SSE"
                    android:backgroundTint="#FF9800"
                    android:textColor="#FFFFFF"/>
            </LinearLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:layout_marginTop="4dp">
                <Button android:id="@+id/btn_happy_on"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_margin="2dp"
                    android:text="Happy"/>
                <Button android:id="@+id/btn_happy_off"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_margin="2dp"
                    android:text="Neutral"/>
                <Button android:id="@+id/btn_blink"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_margin="2dp"
                    android:text="Blink"/>
                <Button android:id="@+id/btn_stop"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_margin="2dp"
                    android:text="Stop"
                    android:backgroundTint="#F44336"
                    android:textColor="#FFFFFF"/>
            </LinearLayout>
        </LinearLayout>
    </ScrollView>
</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 4: Verify resources compile**

Run from `D:\src\SDK-AI-Learn`:

```powershell
.\gradlew.bat :sample:processDebugResources --no-daemon
```

Expected: BUILD SUCCESSFUL. Should NOT see "AAPT: error" or "resource not found" output. (The XML references `com.esotericsoftware.spine.android.SpineView`, which AAPT links lazily at compile time — that comes from `:charactorspeak`'s `api(...)` declaration for `spine-android`.)

- [ ] **Step 5: Commit**

```powershell
git add sample/src/main/res
git commit -m "feat(sample): port layout, theme, and strings from original demo"
```

---

## Task 3: Copy Spine character assets

Goal: Place the three Spine model files in the module's assets directory so `MainActivity` can copy them into `filesDir` on first launch.

**Files:**
- Create (binary copy): `sample/src/main/assets/spine/tutor_character.atlas`
- Create (binary copy): `sample/src/main/assets/spine/tutor_character.json`
- Create (binary copy): `sample/src/main/assets/spine/tutor_character.png`

These are binary assets and MUST be copied with a file-copy command, not pasted through an editor.

- [ ] **Step 1: Create the destination directory**

```powershell
New-Item -ItemType Directory -Force -Path "D:\src\SDK-AI-Learn\sample\src\main\assets\spine" | Out-Null
```

- [ ] **Step 2: Copy the three asset files**

```powershell
Copy-Item "C:\Users\hung0\learna-clone-starter\tutor-sdk\app\src\main\assets\spine\tutor_character.atlas" "D:\src\SDK-AI-Learn\sample\src\main\assets\spine\tutor_character.atlas"
Copy-Item "C:\Users\hung0\learna-clone-starter\tutor-sdk\app\src\main\assets\spine\tutor_character.json"  "D:\src\SDK-AI-Learn\sample\src\main\assets\spine\tutor_character.json"
Copy-Item "C:\Users\hung0\learna-clone-starter\tutor-sdk\app\src\main\assets\spine\tutor_character.png"   "D:\src\SDK-AI-Learn\sample\src\main\assets\spine\tutor_character.png"
```

- [ ] **Step 3: Verify the three files exist and are non-empty**

```powershell
Get-ChildItem "D:\src\SDK-AI-Learn\sample\src\main\assets\spine\" | Select-Object Name, Length
```

Expected output: three rows, all `Length > 0`:
```
Name                    Length
----                    ------
tutor_character.atlas    ... (a few KB)
tutor_character.json     ... (tens of KB)
tutor_character.png      ... (hundreds of KB)
```

- [ ] **Step 4: Commit**

```powershell
git add sample/src/main/assets
git commit -m "feat(sample): add Spine character assets for demo"
```

---

## Task 4: Port `MainActivity.kt` and wire it into the manifest

Goal: Bring over the activity that drives `TutorCharacter`, with the only edit being the `package` declaration. Then register it in the manifest as the launcher activity.

**Files:**
- Create: `sample/src/main/kotlin/com/baseproject/sample/MainActivity.kt`
- Modify: `sample/src/main/AndroidManifest.xml` (add `<activity>` element + theme reference inside `<application>`)

- [ ] **Step 1: Create the Kotlin source directory**

```powershell
New-Item -ItemType Directory -Force -Path "D:\src\SDK-AI-Learn\sample\src\main\kotlin\com\baseproject\sample" | Out-Null
```

- [ ] **Step 2: Create `MainActivity.kt`**

Create file `D:\src\SDK-AI-Learn\sample\src\main\kotlin\com\baseproject\sample\MainActivity.kt` with this exact content. This is the original `learna-clone-starter` activity verbatim except for `package com.baseproject.sample` on line 1:

```kotlin
package com.baseproject.sample

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.apero.tutor.sdk.TutorCharacter
import com.apero.tutor.sdk.TutorCharacterConfig
import com.apero.tutor.sdk.subtitle.KaraokeSubtitleRenderer
import com.apero.tutor.sdk.tts.AndroidTtsProvider
import com.apero.tutor.sdk.tts.AzureTtsProvider
import com.apero.tutor.sdk.tts.ElevenLabsTtsProvider
import com.apero.tutor.sdk.tts.TtsEvent
import com.apero.tutor.sdk.tts.TtsProvider
import com.esotericsoftware.spine.android.SpineView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

/**
 * Demo activity that proves the SDK works end-to-end.
 *
 * Provides a 3-button picker (Android / Azure / ElevenLabs) so the provider
 * can be hot-swapped at runtime via [TutorCharacter.setTtsProvider].
 *
 * Model files are copied from /assets to filesDir on first launch to mimic
 * the "downloaded from server" workflow expected in production.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var sdk: TutorCharacter
    private lateinit var status: TextView
    private lateinit var btnAndroid: Button
    private lateinit var btnAzure: Button
    private lateinit var btnEleven: Button
    private lateinit var btnRateSlow: Button
    private lateinit var btnRateNormal: Button
    private lateinit var btnRateFast: Button

    private var providerLabel: String = "?"
    private var currentKind: ProviderKind = ProviderKind.ANDROID
    private var currentRate: Float = 1.0f
    private var eventsSub: Job? = null

    enum class ProviderKind { ANDROID, AZURE, ELEVENLABS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val spineView    = findViewById<SpineView>(R.id.spineView)
        val subtitleText = findViewById<TextView>(R.id.subtitleText)
        status           = findViewById(R.id.status)
        btnAndroid       = findViewById(R.id.btn_provider_android)
        btnAzure         = findViewById(R.id.btn_provider_azure)
        btnEleven        = findViewById(R.id.btn_provider_elevenlabs)
        btnRateSlow      = findViewById(R.id.btn_rate_slow)
        btnRateNormal    = findViewById(R.id.btn_rate_normal)
        btnRateFast      = findViewById(R.id.btn_rate_fast)

        // 1. Materialise model files into filesDir (mimics a downloader).
        val modelDir = File(filesDir, "models/aria").apply { mkdirs() }
        val atlas = copyAssetIfMissing("spine/tutor_character.atlas", File(modelDir, "tutor_character.atlas"))
        val json  = copyAssetIfMissing("spine/tutor_character.json",  File(modelDir, "tutor_character.json"))
        val png   = copyAssetIfMissing("spine/tutor_character.png",   File(modelDir, "tutor_character.png"))

        // 2. Resolve initial provider from BuildConfig.TTS_PROVIDER.
        val initialKind = resolveInitialKind()
        currentKind = initialKind
        val (initialProvider, initialLabel) = buildProvider(initialKind)
        providerLabel = initialLabel

        // 3. Construct SDK + subscribe to its events.
        sdk = TutorCharacter(
            spineView          = spineView,
            config             = TutorCharacterConfig(atlas, json, png),
            initialTtsProvider = initialProvider,
            subtitleRenderer   = KaraokeSubtitleRenderer(subtitleText),
            scope              = lifecycleScope
        )
        subscribeToEvents(initialProvider)
        updatePickerVisuals()
        updateRateVisuals()

        // 4. Load + wire buttons.
        lifecycleScope.launch {
            status.text = "Loading… [$providerLabel]"
            try {
                sdk.load(this@MainActivity)
                status.text = "Ready · $providerLabel"
            } catch (t: Throwable) {
                status.text = "Load failed: ${t.message}"
            }
        }
        wireButtons()
    }

    private fun wireButtons() {
        val editTts = findViewById<EditText>(R.id.edit_tts_text)
        findViewById<Button>(R.id.btn_speak).setOnClickListener {
            val text = editTts.text.toString().trim()
            if (text.isNotEmpty()) lifecycleScope.launch { sdk.speak(text) }
        }
        findViewById<Button>(R.id.btn_demo_sse).setOnClickListener { simulateSseStream() }
        findViewById<Button>(R.id.btn_happy_on).setOnClickListener  { sdk.setEmotion("happy") }
        findViewById<Button>(R.id.btn_happy_off).setOnClickListener { sdk.setEmotion(null) }
        findViewById<Button>(R.id.btn_blink).setOnClickListener     { sdk.triggerBlink() }
        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            sdk.stop()
            status.text = "Stopped · $providerLabel"
        }

        // Provider picker
        btnAndroid.setOnClickListener { switchTo(ProviderKind.ANDROID) }
        btnAzure  .setOnClickListener { switchTo(ProviderKind.AZURE) }
        btnEleven .setOnClickListener { switchTo(ProviderKind.ELEVENLABS) }

        // Speech rate picker
        btnRateSlow  .setOnClickListener { setRate(0.75f) }
        btnRateNormal.setOnClickListener { setRate(1.0f) }
        btnRateFast  .setOnClickListener { setRate(1.5f) }
    }

    private fun setRate(rate: Float) {
        currentRate = rate
        sdk.speechRate = rate
        updateRateVisuals()
    }

    private fun updateRateVisuals() {
        val onColor  = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
        val offColor = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
        for ((btn, value) in listOf(
            btnRateSlow   to 0.75f,
            btnRateNormal to 1.0f,
            btnRateFast   to 1.5f
        )) {
            val selected = value == currentRate
            btn.backgroundTintList = if (selected) onColor else offColor
            btn.setTextColor(if (selected) Color.WHITE else Color.parseColor("#333333"))
        }
    }

    /** Tear down current TTS, swap to new provider, re-subscribe to events. */
    private fun switchTo(kind: ProviderKind) {
        if (kind == currentKind) return
        val (provider, label) = buildProvider(kind)
        currentKind   = kind
        providerLabel = label
        updatePickerVisuals()
        status.text = "Switching to $label…"

        lifecycleScope.launch {
            try {
                sdk.setTtsProvider(this@MainActivity, provider)
                subscribeToEvents(provider)
                status.text = "Ready · $label"
            } catch (t: Throwable) {
                status.text = "Switch failed: ${t.message}"
            }
        }
    }

    private fun subscribeToEvents(provider: TtsProvider) {
        eventsSub?.cancel()
        eventsSub = lifecycleScope.launch {
            provider.events.collect { ev ->
                when (ev) {
                    is TtsEvent.Started -> status.text = "Speaking · $providerLabel"
                    is TtsEvent.Done    -> status.text = "Ready · $providerLabel"
                    is TtsEvent.Error   -> status.text = "ERROR: ${ev.message}"
                    else -> Unit
                }
            }
        }
    }

    private fun updatePickerVisuals() {
        val onColor  = ColorStateList.valueOf(Color.parseColor("#4CAF50"))   // green
        val offColor = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))   // gray
        for ((btn, kind) in listOf(
            btnAndroid to ProviderKind.ANDROID,
            btnAzure   to ProviderKind.AZURE,
            btnEleven  to ProviderKind.ELEVENLABS
        )) {
            val selected = kind == currentKind
            btn.backgroundTintList = if (selected) onColor else offColor
            btn.setTextColor(if (selected) Color.WHITE else Color.parseColor("#333333"))
        }
    }

    private fun buildProvider(kind: ProviderKind): Pair<TtsProvider, String> = when (kind) {
        ProviderKind.ANDROID -> {
            val voice = BuildConfig.ANDROID_TTS_VOICE.takeIf { it.isNotBlank() }
            val label = if (voice != null) "Android TTS ($voice)" else "Android TTS"
            AndroidTtsProvider(initialVoiceName = voice) to label
        }
        ProviderKind.AZURE -> AzureTtsProvider(
            speechKey = BuildConfig.AZURE_SPEECH_KEY,
            region    = BuildConfig.AZURE_SPEECH_REGION,
            voiceName = BuildConfig.AZURE_SPEECH_VOICE
        ) to "Azure (${BuildConfig.AZURE_SPEECH_VOICE})"
        ProviderKind.ELEVENLABS -> ElevenLabsTtsProvider(
            apiKey  = BuildConfig.ELEVENLABS_API_KEY,
            voiceId = BuildConfig.ELEVENLABS_VOICE_ID,
            modelId = BuildConfig.ELEVENLABS_MODEL_ID
        ) to "ElevenLabs (${BuildConfig.ELEVENLABS_VOICE_ID})"
    }

    /**
     * Resolve initial provider kind from `TTS_PROVIDER` build config:
     *   "elevenlabs" | "azure" | "android" → forced choice
     *   "auto" (default)                   → ElevenLabs > Azure > Android,
     *                                        based on which keys are filled
     */
    private fun resolveInitialKind(): ProviderKind {
        val pick = BuildConfig.TTS_PROVIDER.lowercase()
        val elevenAvail = BuildConfig.ELEVENLABS_API_KEY.isNotBlank()
        val azureAvail  = BuildConfig.AZURE_SPEECH_KEY.isNotBlank() &&
                          BuildConfig.AZURE_SPEECH_REGION.isNotBlank()
        return when (pick) {
            "elevenlabs", "eleven" -> ProviderKind.ELEVENLABS
            "azure"                -> ProviderKind.AZURE
            "android", "system"    -> ProviderKind.ANDROID
            else -> when {
                elevenAvail -> ProviderKind.ELEVENLABS
                azureAvail  -> ProviderKind.AZURE
                else        -> ProviderKind.ANDROID
            }
        }
    }

    /** Emit a passage as if streamed token-by-token from an LLM endpoint. */
    private fun simulateSseStream() {
        val passage = """
            Hello! Welcome to your English lesson today.
            My name is Aria, and I will be your tutor.
            Let us start with pronunciation practice.
            I hope you are ready to learn!
        """.trimIndent().replace("\n", " ").replace(Regex(" +"), " ").trim()

        val tokens = passage.split(Regex("(?<=\\s)|(?=\\s)")).filter { it.isNotEmpty() }
        val chunkFlow = flow {
            for (t in tokens) {
                emit(t)
                delay(55L + Random.nextLong(0, 30))
            }
        }
        lifecycleScope.launch { sdk.speakFromFlow(chunkFlow) }
    }

    override fun onDestroy() {
        sdk.release()
        super.onDestroy()
    }

    private fun copyAssetIfMissing(assetPath: String, destFile: File): File {
        if (destFile.exists() && destFile.length() > 0) return destFile
        assets.open(assetPath).use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        return destFile
    }
}
```

- [ ] **Step 3: Update `sample/src/main/AndroidManifest.xml`**

Replace the file's contents entirely with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="false"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="Charactorspeak Sample"
        android:theme="@style/Theme.Sample">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 4: Compile `:sample`**

Run from `D:\src\SDK-AI-Learn`:

```powershell
.\gradlew.bat :sample:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL. No "unresolved reference" errors. Specifically watch out for:

- `Unresolved reference: TutorCharacter` → `:charactorspeak` is not on the classpath. Check `implementation(project(":charactorspeak"))` in `sample/build.gradle.kts`.
- `Unresolved reference: BuildConfig` → `buildFeatures.buildConfig = true` missing or `buildConfigField` lines wrong in `sample/build.gradle.kts`.
- `Unresolved reference: SpineView` → spine-android isn't being exported; check that `:charactorspeak`'s `build.gradle.kts` still has `api("com.esotericsoftware.spine:spine-android:4.2.12")`.

- [ ] **Step 5: Commit**

```powershell
git add sample/src/main/kotlin sample/src/main/AndroidManifest.xml
git commit -m "feat(sample): port MainActivity from learna-clone-starter demo"
```

---

## Task 5: Build APK and smoke-verify

Goal: Confirm the module assembles a debug APK and that the basic Android-TTS path works on an emulator. The Azure / ElevenLabs paths only run with valid credentials and are out of scope.

**Files:** none modified.

- [ ] **Step 1: Assemble debug APK**

```powershell
.\gradlew.bat :sample:assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL. Produces `sample/build/outputs/apk/debug/sample-debug.apk`.

If this fails with `Could not resolve com.microsoft.cognitiveservices.speech:client-sdk:1.44.0`:
verify `dependencyResolutionManagement.repositories` in the root `settings.gradle.kts` includes `mavenCentral()` (it does already).

If this fails with `Duplicate class` errors involving `azure-core` or `reactor-core`:
the packaging `resources.excludes` likely needs another `META-INF/...` entry. Add to `sample/build.gradle.kts:packaging.resources.excludes`.

- [ ] **Step 2: Verify the APK contains the Spine assets**

```powershell
$apk = "D:\src\SDK-AI-Learn\sample\build\outputs\apk\debug\sample-debug.apk"
$env:JAVA_TOOL_OPTIONS = ""
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::OpenRead($apk).Entries | Where-Object { $_.FullName -like "assets/spine/*" } | Select-Object FullName, Length
```

Expected: three entries listed for `assets/spine/tutor_character.{atlas,json,png}`, each `Length > 0`.

- [ ] **Step 3: Install on a connected device / running emulator and launch**

Pre-req: an Android emulator running API 26+ is connected (`adb devices` shows one device). If you don't have an emulator running, start one from Android Studio (Tools → Device Manager) before continuing.

```powershell
.\gradlew.bat :sample:installDebug --no-daemon
adb shell am start -n com.baseproject.sample/.MainActivity
```

Expected: app launches. The status line at the top of the control panel transitions from `Loading…` to `Ready · Android TTS`. The Spine character appears in the upper half of the screen.

- [ ] **Step 4: Smoke-test the Speak button**

In the running app:
1. Leave the default text ("Nice to meet you! Let us learn English together.") in the EditText.
2. Tap **Speak**.
3. Observe: audio plays through the emulator (host speakers must be unmuted), the subtitle CardView at the bottom shows the sentence with the current word highlighted in a tint, the character's mouth animates, the status line shows `Speaking · Android TTS` then returns to `Ready · Android TTS`.

If no audio: confirm the emulator has Google text-to-speech installed (Settings → Accessibility → Text-to-speech output → preferred engine). The status will still cycle to "Speaking" / "Ready" even without audio output — the TTS callbacks fire regardless of speaker state.

- [ ] **Step 5: (Optional) Smoke-test SSE simulator and emotion buttons**

In the running app:
1. Tap **Simulate SSE** — should speak the four-sentence Aria passage with subtitle + lip-sync.
2. Tap **Happy** then **Speak** again — character expression should change. Tap **Neutral** to revert.
3. Tap **Blink** — character blinks once.
4. Tap **Stop** while audio is playing — speech halts, status shows `Stopped · Android TTS`.

- [ ] **Step 6: Commit (no code changes — this is a verification-only task)**

This task has no commit; it only verifies the previous tasks' output. If any smoke test fails, debug at the corresponding earlier task rather than adding fixup commits here.

---

## After completion

Wrap-up checks:

1. Run `git log --oneline -5` from `D:\src\SDK-AI-Learn` and confirm four new commits exist:
   - `feat(sample): scaffold :sample module and register in settings`
   - `feat(sample): port layout, theme, and strings from original demo`
   - `feat(sample): add Spine character assets for demo`
   - `feat(sample): port MainActivity from learna-clone-starter demo`

2. Run `.\gradlew.bat assemble --no-daemon` to confirm the whole project (`:app`, `:speech`, `:stt`, `:charactorspeak`, `:sample`) still builds. If any pre-existing module breaks, that's a regression caused by this work — investigate before considering the plan complete.

3. The original demo project at `C:\Users\hung0\learna-clone-starter\tutor-sdk\` can stay where it is as a reference; this plan does not delete or modify it.
