plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.santhosh.x200tcamera"
    compileSdk = 35

    // Keep Java and Kotlin bytecode targets aligned.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "com.santhosh.x200tcamera"
        minSdk = 23
        targetSdk = 35
        versionCode = 22
        versionName = "1.4.3"
    }
}
