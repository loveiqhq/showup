// Root build file. Versions live here so there is one place to bump them.
//
// These four have to agree with each other:
//   AGP 8.9.x        requires Gradle 8.11.1  (gradle/wrapper/gradle-wrapper.properties)
//   compileSdk 36    requires AGP 8.9.0 or newer
//   Kotlin 2.0.21    must match the Compose compiler plugin version below
//
// If Android Studio offers an "AGP Upgrade Assistant", accepting it is fine — it keeps
// these in step for you.
plugins {
    id("com.android.application")             version "8.9.3"  apply false
    id("org.jetbrains.kotlin.android")        version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose")  version "2.0.21" apply false
}
