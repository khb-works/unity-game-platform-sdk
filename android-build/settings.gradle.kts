pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.android.library") version "9.3.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
}

rootProject.name = "game-platform-android"

// Unity builds the same folder as part of the exported Gradle project, so this build only adds plugin versions for local builds and unit tests.
include(":gameplatform")
project(":gameplatform").projectDir =
    file("../Packages/io.github.khbworks.gameplatform/Plugins/Android/GamePlatform.androidlib")
