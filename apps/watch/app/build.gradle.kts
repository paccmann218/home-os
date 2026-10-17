import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.homeos.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.homeos.wear"
        minSdk = 30
        targetSdk = 34 // wear-compose 1.4 reads a settings key that targetSdk 35 blocks
        versionCode = 1
        versionName = "1.0"
        // Backend WebSocket + the person wearing this watch (set in local.properties).
        buildConfigField("String", "HOMEOS_WS", "\"${localProps.getProperty("homeos.ws", "ws://10.0.2.2:3001/ws/device")}\"")
        buildConfigField("String", "HOMEOS_ALIAS", "\"${localProps.getProperty("homeos.alias", "harsh")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")
    implementation("androidx.health:health-services-client:1.0.0")
    // Optional Samsung Health Sensor SDK for IBI (see app/libs/README.md); loaded by reflection.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    debugImplementation("androidx.compose.ui:ui-tooling")
}
