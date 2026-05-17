plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.baseproject.tts"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    implementation("androidx.core:core-ktx:1.13.1")
    // Azure SDK compileOnly — consumer (:sample, :app) cấp runtime
    compileOnly("com.microsoft.cognitiveservices.speech:client-sdk:1.44.0")
}
