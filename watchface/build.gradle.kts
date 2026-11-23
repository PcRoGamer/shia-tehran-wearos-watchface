plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.shia.tehranwatchface.wff"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shia.tehranwatchface.wff"
        minSdk = 33
        targetSdk = 35
        versionCode = 7
        versionName = "1.5.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
