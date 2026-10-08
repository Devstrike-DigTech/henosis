package com.devstrike.henosis.cli

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

/**
 * Golden-file support. Compares [actual] with `golden/<relative>`; with `-Dhenosis.updateGolden=true`
 * (the `updateGolden` Gradle task) it rewrites the file instead.
 */
object Golden {
    private val repoRoot: Path =
        Path.of(System.getProperty("henosis.repoRoot") ?: error("henosis.repoRoot system property is not set"))
    private val update = System.getProperty("henosis.updateGolden") == "true"

    fun check(
        relative: String,
        actual: String,
    ) {
        val file = repoRoot.resolve("golden").resolve(relative)
        if (update) {
            Files.createDirectories(file.parent)
            Files.writeString(file, actual)
            return
        }
        check(Files.isRegularFile(file)) { "Golden file missing: $file (run ./gradlew updateGolden to create it)" }
        withClue("Output differs from $file (run ./gradlew updateGolden if the change is intended)") {
            actual shouldBe Files.readString(file)
        }
    }

    val sampleAar: Path by lazy {
        val path = Path.of(System.getProperty("henosis.sampleAar") ?: error("henosis.sampleAar system property is not set"))
        check(Files.isRegularFile(path)) { "Sample SDK AAR not built: $path" }
        path
    }
}
