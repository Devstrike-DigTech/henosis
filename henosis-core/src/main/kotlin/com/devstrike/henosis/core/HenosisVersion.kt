package com.devstrike.henosis.core

/**
 * The Henosis release version.
 *
 * The value is stamped into the `henosis/version.txt` resource at build time from the
 * `henosisVersion` Gradle property, so every module and every generated header agree on it.
 */
object HenosisVersion {
    /** The current version string, for example `0.1.0`, or `unknown` if the resource is missing. */
    val current: String by lazy {
        HenosisVersion::class.java
            .getResourceAsStream("/henosis/version.txt")
            ?.bufferedReader()
            ?.use { it.readText().trim() }
            ?: "unknown"
    }
}
