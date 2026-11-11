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
        // Single source of truth for the app's version, anywhere it needs to be shown.
        // Everything in-app (Settings' About section, both crash screens, the
        // persisted last-crash trace) reads this back via the generated
        // BuildConfig.VERSION_NAME/VERSION_CODE rather than a hardcoded string, so
        // there's exactly one place to bump per release. Static docs (README.md,
        // app/README.md) can't read a Gradle value at doc-render time and are kept in
        // sync by hand - bump those alongside this when you bump here.
        versionCode = 3
        versionName = "0.3"
    }

    buildFeatures {
        compose = true
        // Needed to generate BuildConfig.VERSION_NAME/VERSION_CODE from the values
        // above - AGP 8+ no longer turns this on implicitly just because compose is.
        buildConfig = true
    }

    // Release signing is sourced from env vars so the keystore itself never has to
    // live in the repo. CI (see .github/workflows/release.yml) decodes a base64
    // secret to a keystore file and exports these four vars before invoking
    // assembleRelease. Locally, export the same four vars yourself if you want a
    // signed release build; if they're absent, `release` silently falls back to
    // AGP's default (unsigned) release build type instead of failing the build.
    val releaseStoreFile = System.getenv("ARKSTER_RELEASE_STORE_FILE")
    val releaseStorePassword = System.getenv("ARKSTER_RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = System.getenv("ARKSTER_RELEASE_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("ARKSTER_RELEASE_KEY_PASSWORD")
    val hasReleaseSigning = listOf(
        releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
    ).all { !it.isNullOrBlank() }

    signingConfigs {
        // Committed debug.keystore at the repo root, standard well-known debug
        // credentials - storePassword/keyPassword "android" and alias
        // "androiddebugkey" are the same ones AGP's own auto-generated debug
        // keystore uses, they're not a secret. Without an explicit debug
        // signingConfig, AGP falls back to ~/.android/debug.keystore on whatever
        // machine or CI runner happens to run the build - auto-generated with a
        // random key the first time it's missing, which it always is on a fresh
        // GitHub Actions runner. That means every local dev machine, and every
        // single CI run, was signing debug APKs with its own different key:
        // installing a CI-built APK over a locally-built one (or one CI run's
        // artifact over another's) failed with "App not installed - package
        // conflicts with an existing package" unless you uninstalled first.
        // Pointing every build at this one checked-in file instead - same
        // pattern the sibling ARKarium reader already uses for itself - makes
        // every debug build's signature the same, everywhere it's built.
        getByName("debug") {
            storeFile = file("$rootDir/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Explicit rather than relying on AGP's default, since this APK is
            // handed directly to sideloaders rather than going through Play's
            // App Bundle pipeline - keep the build predictable and skip R8/
            // resource-shrinking risk (stripped Room/Compose reflection, etc.)
            // until proguard rules are actually written and tested.
            isMinifyEnabled = false
            isShrinkResources = false
        }
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

    // Sideloaders download a single APK straight from the GitHub Actions
    // artifact (see .github/workflows/release.yml) rather than through a Play
    // Store listing, so give it a name that identifies itself instead of the
    // generic "app-release.apk" - handy once more than one release is floating
    // around in someone's Downloads folder. `com.android.build.gradle.api.
    // ApkVariantOutput` is the officially documented public API for this on
    // AGP 8.x (see Android Gradle recipes); the newer androidComponents/
    // Variant API's outputFileName doesn't resolve reliably until AGP 9.
    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.api.ApkVariantOutput
            output.outputFileName = "ARKster-${variant.versionName}-${variant.name}.apk"
        }
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
    // STAGE 3 MOVE -- 2026-11-11: navigation-compose:2.7.5 below pulls in enough of
    // these transitively to *compile* against in some versions, but not reliably
    // enough to depend on - viewModel() (AppNavHost.kt) needs
    // lifecycle-viewmodel-compose and collectAsStateWithLifecycle() (used at every
    // destination in AppNavHost.kt) needs lifecycle-runtime-compose. Declared
    // explicitly, pinned to the same 2.6.2 already used above, rather than relying on
    // whatever navigation-compose happens to resolve.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")

    // Coil for images. coil-compose (not the bare coil artifact) is needed for
    // AsyncImage, used to render remote cover thumbnails in the metadata match picker.
    implementation("io.coil-kt:coil-compose:2.4.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // DocumentFile
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Plain JVM unit tests for the pure logic (see ScanReconcileTest). No Robolectric /
    // instrumented setup on purpose - only code with no Android types is covered.
    testImplementation("junit:junit:4.13.2")
}

