plugins {
    id("com.android.application")
}

val ks = rootProject.file("signing/ham.jks")

android {
    namespace = "cat.narezany.grind"
    compileSdk = 34

    defaultConfig {
        applicationId = "cat.narezany.grind"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (ks.exists()) {
            create("ham") {
                storeFile = ks
                storePassword = "hamham"
                keyAlias = "ham"
                keyPassword = "hamham"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (ks.exists()) signingConfig = signingConfigs.getByName("ham")
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
