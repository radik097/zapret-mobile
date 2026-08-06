import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "dev.zapret.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.zapret.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.1.10"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // armeabi (ARMv5/v6) is intentionally excluded: the NDK removed
            // its toolchain years ago (pre-r17), so it cannot be built with
            // any currently supported NDK version.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        getByName("debug") {
            // Checked into version control on purpose: this is the standard,
            // non-secret Android debug certificate (password "android"). Pinning
            // it here (instead of each machine's own ~/.android/debug.keystore)
            // keeps local builds, CI builds, and GitHub Releases signed with the
            // same key, so updates install cleanly over a previous debug build
            // instead of failing with a signature mismatch.
            storeFile = rootProject.layout.projectDirectory.file("keystore/debug.keystore").asFile
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        // Only created when a local keystore.properties + release.keystore exist
        // (both are git-ignored -- this is a real secret, unlike the debug key
        // above, and must never be committed). Without it, `release` builds
        // fall back to unsigned output rather than failing the build, so CI and
        // machines without the release key can still build/test everything else.
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

val rustCrateDir = rootProject.layout.projectDirectory.dir("native-engine/rust/zapret_engine")
val rustOutputDir = project.layout.projectDirectory.dir("src/main/jniLibs")
val hevBuildScript = rootProject.layout.projectDirectory.file("scripts/build-hev-socks5.ps1")

val ndkPackageVersion = "28.2.13676358"
val resolvedAndroidSdkRoot = System.getenv("ANDROID_SDK_ROOT")
    ?: System.getenv("ANDROID_HOME")
    ?: "D:\\Android\\Sdk"
val resolvedAndroidNdkHome = System.getenv("ANDROID_NDK_HOME")
    ?: System.getenv("ANDROID_NDK_ROOT")
    ?: "$resolvedAndroidSdkRoot\\ndk\\$ndkPackageVersion"

tasks.register<Exec>("buildRustAndroid") {
    workingDir = rustCrateDir.asFile
    environment("ANDROID_NDK_HOME", resolvedAndroidNdkHome)
    environment("ANDROID_NDK_ROOT", resolvedAndroidNdkHome)
    environment("ANDROID_SDK_ROOT", resolvedAndroidSdkRoot)
    commandLine(
        "cargo",
        "ndk",
        "-t",
        "arm64-v8a",
        "-t",
        "armeabi-v7a",
        "-t",
        "x86",
        "-t",
        "x86_64",
        "-o",
        rustOutputDir.asFile.absolutePath,
        "build",
        "--release"
    )
}

tasks.register<Exec>("buildHevSocks5Tunnel") {
    workingDir = rootProject.layout.projectDirectory.asFile
    environment("ANDROID_NDK_HOME", resolvedAndroidNdkHome)
    environment("ANDROID_NDK_ROOT", resolvedAndroidNdkHome)
    environment("ANDROID_SDK_ROOT", resolvedAndroidSdkRoot)
    environment("ANDROID_HOME", resolvedAndroidSdkRoot)
    commandLine(
        "powershell",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        hevBuildScript.asFile.absolutePath
    )
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("buildRustAndroid")
    dependsOn("buildHevSocks5Tunnel")
}
