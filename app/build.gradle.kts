import java.util.Properties
import java.io.File

plugins {
    id("com.android.application")
}

val localProperties = Properties().apply {
    val candidates = listOf(
        rootProject.file("local.properties"),
        projectDir.parentFile.resolve("local.properties")
    ).distinct()
    candidates.forEach { localPropertiesFile ->
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use(::load)
        }
    }
}
val screenTranslateProjectDir: File = projectDir.parentFile

android {
    namespace = "com.cgangel.screen_translate"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.cgangel.screen_translate"
        minSdk = 23
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        create("localRelease") {
            val releaseStoreFile = localProperties.getProperty("release.storeFile")
            val releaseStorePassword = localProperties.getProperty("release.storePassword")
            val releaseKeyAlias = localProperties.getProperty("release.keyAlias")
            val releaseKeyPassword = localProperties.getProperty("release.keyPassword")
            if (!releaseStoreFile.isNullOrBlank()
                && !releaseStorePassword.isNullOrBlank()
                && !releaseKeyAlias.isNullOrBlank()
                && !releaseKeyPassword.isNullOrBlank()
            ) {
                val releaseStorePath = File(releaseStoreFile)
                storeFile = if (releaseStorePath.isAbsolute) {
                    releaseStorePath
                } else {
                    screenTranslateProjectDir.resolve(releaseStoreFile)
                }
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("localRelease")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("localRelease")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
            res.srcDirs("src/main/res")
            manifest.srcFile("src/main/AndroidManifest.xml")
        }
        getByName("test") {
            java.srcDirs("src/test/java")
        }
    }
}

dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("com.google.mlkit:translate:17.0.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
