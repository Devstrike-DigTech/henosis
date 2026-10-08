pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "henosis"

include(
    "henosis-cli",
    "henosis-core",
    "henosis-parser-android",
    "henosis-gen-bridge",
    "henosis-gen-flutter",
    "henosis-gen-react-native",
    "henosis-verify",
)

// The sample Android SDK is a separate Gradle build (it needs the Android SDK and AGP's built-in
// Kotlin), composed into this one so tests can depend on its AAR.
includeBuild("fixtures/sample-sdk")
