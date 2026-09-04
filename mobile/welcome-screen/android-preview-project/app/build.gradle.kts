plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.openapi.generator")
}

// The OpenAPI contract, emitted from the NestJS backend by `npm run openapi:emit` at the repo root.
//
// The path reaches out of the Android project because backend and both apps share one repository.
// That is a real coupling to the repo layout, and it is the deliberate trade: the alternative is
// publishing the spec as an artifact and downloading it, which makes an app build need the network
// and a live pipeline. If the apps ever move to their own repository, this is the line that has to
// change.
val openApiSpec = file("$rootDir/../../../openapi.json")

// WHY GENERATED CODE IS NOT COMMITTED
//
// Committing it means every backend field rename produces a large diff nobody reads, and -- worse --
// it makes the generated files *editable*. The moment somebody fixes a bug by hand in a generated
// file, the generator stops being a source of truth and becomes a suggestion. Writing into build/
// removes the temptation structurally: the next build erases it.
val generatedApiDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set(openApiSpec.absolutePath)
    outputDir.set(generatedApiDir.map { it.asFile.absolutePath })

    apiPackage.set("com.showup.api.generated.api")
    modelPackage.set("com.showup.api.generated.model")
    packageName.set("com.showup.api.generated")

    // jvm-retrofit2 rather than the multiplatform or plain-jvm libraries: this is a single-platform
    // Android app, Retrofit is the interface style Android developers already read, and it composes
    // with OkHttp -- which is where the auth interceptor lives, keeping tokens out of every
    // generated endpoint method.
    library.set("jvm-retrofit2")

    configOptions.set(
        mapOf(
            // kotlinx.serialization rather than Moshi or Gson: it is compile-time generated, so it
            // needs no reflection and therefore almost no R8 keep rules. Moshi's reflective adapter
            // would need a keep rule per model, and a missing one fails only in release.
            "serializationLibrary" to "kotlinx_serialization",
            "dateLibrary" to "java8",
            // Coroutines instead of Call<T>. Every caller is already in a coroutine.
            "useCoroutines" to "true",
        )
    )

    // No globalProperties filter here, deliberately. Setting `supportingFiles` on its own reads as
    // "generate supporting files" but acts as "generate ONLY supporting files" -- it produced the
    // infrastructure package and zero APIs or models. The generator also writes a build.gradle,
    // settings.gradle and README alongside; those are harmless, because only the src/main/kotlin
    // subtree is added to the source set and Gradle never looks for a nested project inside build/.
}

// Generation must happen before anything tries to compile against it. Depending on preBuild rather
// than on the Kotlin compile task is deliberate: preBuild runs for every variant, so debug, release
// and unit tests all get the same generated sources without three separate wirings.
tasks.named("preBuild") { dependsOn("openApiGenerate") }

android {
    // Must match the `import com.showup.R` in WelcomeScreen.kt
    namespace = "com.showup"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.showup"
        // API 30 (Android 11), confirmed by the product side on 2026-09-01. It was 24, which is
        // 2016 and well under 1% of active devices -- the cost of that floor was desugaring, more
        // OEM quirks, and testing on hardware essentially nobody runs.
        minSdk = 30
        // API 36 (Android 16). Google Play has required this of new apps and updates since
        // 31 August 2026 -- below it, the first submission is simply rejected.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    // One base URL per build type, read from BuildConfig rather than chosen at runtime. A runtime
    // condition can be wrong -- a debug flag left on, an environment variable unset -- and then a
    // release build talks to staging. This way the URL is decided by which build you made.
    //
    // These mirror the three-branch workflow in CONTRIBUTING.md: development, staging, main.
    buildTypes {
        debug {
            // 10.0.2.2 is the host machine as seen from the Android emulator; localhost inside the
            // emulator is the emulator itself.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3000/\"")
        }
        release {
            // On now rather than later, deliberately. R8 breaks the things it cannot see -- data
            // loaded by a name built at runtime, anything reached by reflection -- and it breaks
            // them ONLY in release, so a debug build keeps working and the failure waits until the
            // build that goes to the store. Switching it on while the app is five screens means
            // any such breakage is found today, against code we still remember.
            isMinifyEnabled = true
            // Unused drawables, layouts and strings as well as unused code. Our 245 flag PNGs are
            // in assets/, which is never shrunk, so they are unaffected.
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("String", "API_BASE_URL", "\"https://api.showup.example/\"")
        }
    }

    // The generated client is compiled as part of the app rather than as a separate module: one
    // module, one build, and nothing to publish. It is still isolated in package terms
    // (com.showup.api.generated), which is what the "never edit generated code" rule relies on.
    sourceSets {
        getByName("main") {
            java.srcDir(generatedApiDir.map { it.dir("src/main/kotlin") })
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
        buildConfig = true
    }
    testOptions {
        // Robolectric needs the real resources -- fonts and the drawable the welcome card uses.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // ── the generated API client's runtime dependencies ─────────────────────────
    // The generator emits interfaces and data classes; these are what make them work. Versions are
    // pinned rather than ranged, because a client that regenerates itself is quite enough moving
    // parts without the HTTP stack moving too.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // These three are not optional extras -- the generated infrastructure/ApiClient.kt imports all
    // of them directly, so leaving any one out is a compile error in generated code rather than a
    // missing feature. Retrofit's own kotlinx converter is used rather than Jake Wharton's, so the
    // converter and Retrofit are versioned together.
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

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

    // Serves canned HTTP responses in-process, so the client and the auth interceptor can be tested
    // without a backend, a network or an emulator.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

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
