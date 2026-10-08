package com.devstrike.henosis.gen.reactnative

/**
 * Versions written into generated React Native packages. They mirror what
 * `create-react-native-library` 0.63 and the React Native 0.87 app template write, verified on
 * 2026-10-08, so the generated package builds exactly like a hand-created one.
 */
object ReactNativeToolchain {
    const val REACT_NATIVE = "0.87.1"
    const val REACT = "19.2.3"
    const val TYPES_REACT = "^19.2.0"
    const val CLI = "20.2.0"
    const val BABEL_CORE = "^7.25.2"
    const val BABEL_PRESET_ENV = "^7.25.3"
    const val BABEL_RUNTIME = "^7.25.0"
    const val BUILDER_BOB = "^0.43.0"
    const val MONOREPO_CONFIG = "^0.4.0"

    /** The template pins TypeScript 6; React Native's tooling is not yet validated on 7. */
    const val TYPESCRIPT = "^6.0.3"
    const val YARN = "4.18.1"
    const val NODE_ENGINE = ">= 22.11.0"

    /** Library build script defaults, overridden by the consuming app's `rootProject.ext`. */
    const val LIBRARY_AGP = "8.7.2"
    const val LIBRARY_KOTLIN = "2.0.21"

    const val APP_KOTLIN = "2.2.0"
    const val BUILD_TOOLS = "37.0.0"
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 36
    const val MIN_SDK = 24
    const val NDK = "27.1.12297006"
    const val GRADLE = "9.4.1"
    const val COROUTINES = "1.11.0"
}
