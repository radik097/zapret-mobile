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

tasks.register<Exec>("buildRustAndroid") {
    workingDir = rustCrateDir.asFile
    environment("ANDROID_NDK_HOME", "D:\\Android\\Sdk\\ndk\\28.2.13676358")
    environment("ANDROID_NDK_ROOT", "D:\\Android\\Sdk\\ndk\\28.2.13676358")
    environment("ANDROID_SDK_ROOT", "D:\\Android\\Sdk")
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
