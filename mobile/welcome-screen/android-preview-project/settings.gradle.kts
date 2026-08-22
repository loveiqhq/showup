pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// No repositoriesMode here on purpose. FAIL_ON_PROJECT_REPOS is the usual line, but it is marked
// @Incubating in Gradle, so it raises three warnings that read like errors — and it only earns its
// keep in a multi-module build, by stopping a module declaring its own repositories. This project
// has one module.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ShowUp Welcome Preview"
include(":app")
