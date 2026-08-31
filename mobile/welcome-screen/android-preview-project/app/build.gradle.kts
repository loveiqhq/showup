plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Must match the `import com.showup.R` in WelcomeScreen.kt
    namespace = "com.showup"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.showup"
        minSdk = 24          // deliberately low: the orbs must look right on old devices too
        // API 36 (Android 16). Google Play has required this of new apps and updates since
        // 31 August 2026 -- below it, the first submission is simply rejected.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
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
    buildFeatures {
        compose = true
    }
    testOptions {
        // Robolectric needs the real resources -- fonts and the drawable the welcome card uses.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.core:core-ktx:1.13.1")

    // Google's libphonenumber — Apache 2.0, free, offline. It carries the real numbering rules for
    // every country, which is what makes a full country list possible: hand-writing length and
    // mobile-prefix rules for 250 countries would mean guessing, and a wrong guess rejects a real
    // person's real number.
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.52")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // The phone rules are pure JVM logic, so they are tested off-device.
    testImplementation("junit:junit:4.13.2")

    // Layout verification without a device. Robolectric runs the real Compose runtime on the JVM,
    // so every screen can be measured at every phone size in seconds -- which an emulator on one
    // machine cannot do, and which @Preview cannot assert on.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Needed for the @Preview panel to render inside Android Studio
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
