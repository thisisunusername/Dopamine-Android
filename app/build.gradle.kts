/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * See LICENSE at the repository root. Clauses 1 and 3 of the GPL only need the
 * licence text to travel with a copy, so `copyLicenceIntoApk` below puts it
 * inside the APK's assets: the repository ships the text, but a five-megabyte
 * APK attached to a release would otherwise be the "object code" the licence
 * talks about without its terms next to it.
 */
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
            /*
             * Resolved against the project root, not this module: `file(...)` inside
             * app/build.gradle.kts is relative to app/, and the keystore lives at the repository
             * root so it is not tied to one module's layout.
             */
            storeFile = rootProject.file("signing/dopamine-release.jks")
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
/*
 * Put the licence inside the APK.
 *
 * GPL-2.0 section 1 requires the licence to accompany every copy that is
 * distributed, and an APK downloaded from a release page is such a copy: the
 * person who has the file may never look at the repository. assets/ is readable
 * without unpacking anything clever, and merging into the existing assets
 * directory instead of declaring `assets.srcDirs` keeps the Java-generated
 * sources that default directory already carries.
 *
 * `rootProject.file` and not `file`: this file is evaluated in app/, and the
 * licence lives at the repository root.
 */
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            tasks.register<Copy>("copyLicenceIntoAssets${variant.name.replaceFirstChar { it.uppercase() }}") {
                from(rootProject.file("LICENSE"))
                from(rootProject.file("COPYRIGHT"))
                into(layout.buildDirectory.dir("generated/licenceAssets/"))
            }
        ) { it.outputDir }
    }
}
