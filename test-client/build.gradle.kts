plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.zapret.testclient"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.zapret.testclient"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
}
