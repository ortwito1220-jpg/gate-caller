plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gatecaller"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gatecaller"
        minSdk = 33
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.$versionCode"
    }

    // Fixed keystore: the workflow decodes it from repo secrets into keystore.p12.
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore.p12")
            storeType = "pkcs12"
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "gate"
            keyPassword = System.getenv("KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
