plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.shia.tehranwatchface.wff"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shia.tehranwatchface.wff"
        minSdk = 36
        targetSdk = 36
        versionCode = 61
        versionName = "1.6.50"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
