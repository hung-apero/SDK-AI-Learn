import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

// Read Artifactory credentials from root local.properties (gitignored).
// Falls back to Gradle properties or env vars so CI can override without editing the file.
val publishProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val artifactoryUser: String = publishProps.getProperty("artifactoryUser")
    ?: (project.findProperty("artifactoryUser") as String?)
    ?: System.getenv("ARTIFACTORY_USER") ?: ""
val artifactoryPassword: String = publishProps.getProperty("artifactoryPassword")
    ?: (project.findProperty("artifactoryPassword") as String?)
    ?: System.getenv("ARTIFACTORY_PASSWORD") ?: ""

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
                name = "release"
                url = uri("https://artifactory.apero.vn/artifactory/gradle-release/")
                credentials {
                    username = artifactoryUser
                    password = artifactoryPassword
                }
            }
        }
    }
}

// Print final artifact coordinates + URL after a successful publish, so the
// developer knows exactly where the AAR landed (mavenLocal or remote repo).
tasks.withType<PublishToMavenLocal>().configureEach {
    doLast {
        val v = project.property("sdkVersion")
        val groupPath = project.property("sdkGroupId").toString().replace('.', '/')
        val coords = "${project.property("sdkGroupId")}:ai-speech:$v"
        logger.lifecycle("")
        logger.lifecycle("==> Published $coords to mavenLocal")
        logger.lifecycle("    ${System.getProperty("user.home")}/.m2/repository/$groupPath/ai-speech/$v/ai-speech-$v.aar")
    }
}
tasks.withType<PublishToMavenRepository>().configureEach {
    doLast {
        val v = project.property("sdkVersion")
        val groupPath = project.property("sdkGroupId").toString().replace('.', '/')
        val coords = "${project.property("sdkGroupId")}:ai-speech:$v"
        val repoUrl = repository.url.toString().trimEnd('/')
        logger.lifecycle("")
        logger.lifecycle("==> Published $coords to '${repository.name}'")
        logger.lifecycle("    $repoUrl/$groupPath/ai-speech/$v/")
    }
}
