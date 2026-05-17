plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.baseproject.stt"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // Networking — used by InHouseRestProvider + InHouseWsProvider
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Azure Speech — used only if you register AzureSpeechProvider.
    // Comment out if you don't ship the Azure provider, to drop ~20MB from the AAR.
    implementation(libs.azure.speech.sdk)
}
