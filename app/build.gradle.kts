plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.zapret.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.zapret.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
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
