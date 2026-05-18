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
val sttProvider:  String = localProps.getProperty("STT_PROVIDER",         "android")
val sttLocale:    String = localProps.getProperty("STT_LOCALE",           "en-US")
val geminiKey:    String = localProps.getProperty("GEMINI_API_KEY",       "")
val geminiModel:  String = localProps.getProperty("GEMINI_STT_MODEL",     "models/gemini-2.5-flash-lite")
val geminiNative: String = localProps.getProperty("GEMINI_NATIVE_LOCALE", "vi")

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
        buildConfigField("String", "STT_PROVIDER",         "\"$sttProvider\"")
        buildConfigField("String", "STT_LOCALE",           "\"$sttLocale\"")
        buildConfigField("String", "GEMINI_API_KEY",       "\"$geminiKey\"")
        buildConfigField("String", "GEMINI_STT_MODEL",     "\"$geminiModel\"")
        buildConfigField("String", "GEMINI_NATIVE_LOCALE", "\"$geminiNative\"")

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
    implementation("apero-inhouse:ai-speech:0.1.0")
    // Azure Speech SDK + Spine are compileOnly in :ai-speech — sample brings runtime.
    implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.44.0")
    implementation("com.esotericsoftware.spine:spine-android:4.2.12")

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
