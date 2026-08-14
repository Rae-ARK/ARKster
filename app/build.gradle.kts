plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
}

android {
    namespace = "com.arkster.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.arkster.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.0")

    // Icons.Default.* used throughout the UI (ArrowBack, Sort, FilterList, Brightness4/7,
    // KeyboardArrowUp/Down, Star, etc.). Declared explicitly rather than relying on
    // material3 to pull material-icons-core in transitively, since that transitive
    // dependency was removed in newer material3 releases and some of the icons used
    // here (Sort, FilterList, Brightness4/7, KeyboardArrowUp/Down) live in the
    // extended icon set, not the core one.
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // Coil for images
    implementation("io.coil-kt:coil:2.4.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // DocumentFile
    implementation("androidx.documentfile:documentfile:1.0.1")
}

