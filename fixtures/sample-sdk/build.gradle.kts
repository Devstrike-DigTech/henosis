// Henosis sample SDK: a small Android library that exercises every v0.1 type-mapping row and
// both facade lifecycles (no-arg constructor and Context constructor). Clean-room code.
plugins {
    id("com.android.library") version "9.4.1"
}

android {
    namespace = "com.devstrike.sample"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
