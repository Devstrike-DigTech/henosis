package com.devstrike.henosis.testing

import com.devstrike.henosis.core.codegen.GeneratedFile
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

/**
 * Golden-file support shared by every module's tests.
 *
 * Compares actual output with files under `golden/`; with `-Dhenosis.updateGolden=true`
 * (the `updateGolden` Gradle task) it rewrites them instead.
 */
object Golden {
    val repoRoot: Path =
        Path.of(System.getProperty("henosis.repoRoot") ?: error("henosis.repoRoot system property is not set"))

    private val update = System.getProperty("henosis.updateGolden") == "true"

    val goldenRoot: Path = repoRoot.resolve("golden")

    /** The sample SDK's AAR, built by the included `fixtures/sample-sdk` build. */
    val sampleAar: Path by lazy {
        val path = Path.of(System.getProperty("henosis.sampleAar") ?: error("henosis.sampleAar system property is not set"))
        check(Files.isRegularFile(path)) { "Sample SDK AAR not built: $path (run ./gradlew build with the Android SDK installed)" }
        path
    }

    /** The golden IR for the sample SDK, the input every generator test starts from. */
    val sampleIrJson: String get() = Files.readString(goldenRoot.resolve("ir/sample-sdk.json"))

    /** Compares one text output with `golden/<relative>`. */
    fun check(
        relative: String,
        actual: String,
    ) {
        val file = goldenRoot.resolve(relative)
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

    /**
     * Compares a whole generated tree with `golden/<relativeDir>`: every file's content, plus
     * no missing and no unexpected files. In update mode the directory is replaced.
     */
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun checkTree(
        relativeDir: String,
        files: List<GeneratedFile>,
    ) {
        val dir = goldenRoot.resolve(relativeDir)
        if (update) {
            if (Files.exists(dir)) dir.toFile().deleteRecursively()
            files.forEach { f ->
                val target = dir.resolve(f.path)
                Files.createDirectories(target.parent)
                Files.writeString(target, f.content)
            }
            return
        }
        check(Files.isDirectory(dir)) { "Golden directory missing: $dir (run ./gradlew updateGolden to create it)" }
        val expectedPaths =
            dir
                .walk()
                .filter { it.isRegularFile() }
                .map { it.relativeTo(dir).toString().replace('\\', '/') }
                .toSet()
        val actualPaths = files.map { it.path }.toSet()
        withClue(
            "Files generated but missing from $dir (run ./gradlew updateGolden if intended)",
        ) { (actualPaths - expectedPaths).shouldBeEmpty() }
        withClue(
            "Golden files no longer generated (run ./gradlew updateGolden if intended)",
        ) { (expectedPaths - actualPaths).shouldBeEmpty() }
        files.sortedBy { it.path }.forEach { f ->
            withClue("${f.path} differs from its golden copy under $dir (run ./gradlew updateGolden if the change is intended)") {
                f.content shouldBe Files.readString(dir.resolve(f.path))
            }
        }
    }
}
