plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
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
    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // compileOnly — consumer apps must provide these at runtime if using the
    // corresponding providers (Azure TTS / Spine character animation).
    compileOnly("com.microsoft.cognitiveservices.speech:client-sdk:1.44.0")
    compileOnly("com.esotericsoftware.spine:spine-android:4.2.12")

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = project.property("sdkGroupId").toString()
                artifactId = "ai-speech"
                version = project.property("sdkVersion").toString()
                from(components["release"])
            }
        }
        repositories {
            maven {
                url = uri("https://artifactory.apero.vn/artifactory/gradle-release/")
                credentials {
                    username = (project.findProperty("artifactoryUser") as String?)
                        ?: System.getenv("ARTIFACTORY_USER") ?: ""
                    password = (project.findProperty("artifactoryPassword") as String?)
                        ?: System.getenv("ARTIFACTORY_PASSWORD") ?: ""
                }
            }
        }
    }
}
