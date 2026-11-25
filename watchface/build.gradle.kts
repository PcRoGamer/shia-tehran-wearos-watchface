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
        versionCode = 27
        versionName = "1.6.16"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
