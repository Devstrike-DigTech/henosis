package com.devstrike.henosis.parser.android

import com.devstrike.henosis.core.api.ApiModel
import java.nio.file.Files
import java.nio.file.Path

/** The sample SDK AAR built by `fixtures/sample-sdk`, located via the `henosis.sampleAar` system property. */
object SampleSdk {
    val aar: Path by lazy {
        val path = Path.of(System.getProperty("henosis.sampleAar") ?: error("henosis.sampleAar system property is not set"))
        check(Files.isRegularFile(path)) { "Sample SDK AAR not built: $path (run ./gradlew build with the Android SDK installed)" }
        path
    }

    val model: ApiModel by lazy { AndroidSdkParser.parse(aar) }
}
