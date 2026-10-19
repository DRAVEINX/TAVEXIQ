plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val tavexiqKeystorePath = System.getenv("TAVEXIQ_KEYSTORE_PATH")
val tavexiqKeystorePassword = System.getenv("TAVEXIQ_KEYSTORE_PASSWORD")
val tavexiqKeyAlias = System.getenv("TAVEXIQ_KEY_ALIAS")
val tavexiqKeyPassword = System.getenv("TAVEXIQ_KEY_PASSWORD")
val tavexiqSigningConfigured = listOf(
    tavexiqKeystorePath,
    tavexiqKeystorePassword,
    tavexiqKeyAlias,
    tavexiqKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.tavexiq.wallet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tavexiq.wallet"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (tavexiqSigningConfigured) {
            create("release") {
                storeFile = file(tavexiqKeystorePath!!)
                storePassword = tavexiqKeystorePassword
                keyAlias = tavexiqKeyAlias
                keyPassword = tavexiqKeyPassword
            }
        }
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = false
            if (tavexiqSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.zxing:core:3.5.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    // Provide a real org.json implementation to local JVM unit tests (Android's stubbed API throws).
    testImplementation("org.json:json:20240303")
}
