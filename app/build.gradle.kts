plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.opa334.dopamine"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.opa334.dopamine"
        /*
         * API 28 (Android 9) because that is what the UI is stated to support, and API 36
         * (Android 16) as the target. compileSdk stays at 36: the SDK shipped with this
         * toolchain is 36.1, and raising it further would only be needed to call newer APIs,
         * which this app does not do.
         */
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    /*
     * A real signing key, not the debug key.
     *
     * Shipping a release signed with the debug key means the signature is the well-known "android"
     * one, so anyone can rebuild this app and have it install over an existing copy - the update
     * would be accepted as the same publisher. The keystore here is generated once and reused, so
     * updates keep the same signature, which is what Android requires to install over a previous
     * version without uninstalling.
     *
     * The password is read from the environment (local builds) or from a Gradle property seeded by
     * CI secrets, never from this file: the repository is public.
     */
    signingConfigs {
        create("release") {
            storeFile = file("signing/dopamine-release.jks")
            storePassword = System.getenv("DOPAMINE_STORE_PASSWORD")
                ?: (project.findProperty("dopamineStorePassword") as String?)
                ?: ""
            keyAlias = "dopamine"
            keyPassword = System.getenv("DOPAMINE_KEY_PASSWORD")
                ?: (project.findProperty("dopamineKeyPassword") as String?)
                ?: ""
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            /*
             * Only sign when a password is actually available. Without this fallback a plain
             * `./gradlew assembleDebug` on a machine with no keystore password would still try to
             * configure the release config, and an empty password makes the APK unsigned rather
             * than merely unsigned-in-a-way-nobody-notices.
             */
            signingConfig = if (signingConfigs.getByName("release").storePassword.isNullOrEmpty()) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    /*
     * Coroutines explicitly, even though Compose pulls them in transitively.
     *
     * JailbreakEngine and the animations use delay()/launch() directly, and relying on a transitive
     * version means an upgrade of Compose could silently change or drop the API this code compiles
     * against.
     */
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    /*
     * Extended icons, not the core set: RestartAlt, Terminal and ArrowBack are not in the core
     * material-icons-core artifact, so without this the main menu fails to compile.
     */
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}