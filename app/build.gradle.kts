plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.slop"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.slop"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "3.1"
    }

    // Firma fija para las builds de prueba: así cada APK nuevo se instala encima del anterior
    // sin tener que desinstalar (y sin perder los ajustes ni la API key).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Sin dependencias externas: la app usa solo el framework de Android y la
// biblioteca estándar de Kotlin (que agrega el plugin automáticamente).
dependencies {
}
