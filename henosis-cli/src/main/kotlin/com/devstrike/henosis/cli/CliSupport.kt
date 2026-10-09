package com.devstrike.henosis.cli

import com.devstrike.henosis.core.HenosisVersion
import com.devstrike.henosis.core.api.ApiModel
import com.devstrike.henosis.core.config.ConfigException
import com.devstrike.henosis.core.config.ConfigLoader
import com.devstrike.henosis.core.config.LoadedConfig
import com.devstrike.henosis.core.doctor.Doctor
import com.devstrike.henosis.core.doctor.DoctorResult
import com.devstrike.henosis.parser.android.AndroidSdkParser
import com.devstrike.henosis.parser.android.MavenCoordinates
import com.devstrike.henosis.parser.android.MavenResolutionException
import com.devstrike.henosis.parser.android.MavenResolver
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path

/** Exit codes shared by every command (CLAUDE.md section 8). */
object ExitCode {
    const val OK = 0
    const val VALIDATION = 1
    const val TOOLCHAIN_MISSING = 2
    const val BUILD_FAILED = 3
}

/** Prints [message] to stderr and exits with [code]. */
fun CliktCommand.fail(
    message: String,
    code: Int = ExitCode.VALIDATION,
): Nothing {
    echo(message, err = true)
    throw ProgramResult(code)
}

/** The `--config` option used by every command that reads `henosis.yaml`. */
fun CliktCommand.configOption() =
    option("-c", "--config", help = "Path to henosis.yaml")
        .path(canBeDir = false)
        .default(Path.of("henosis.yaml"))

/** Loads the config or exits with a path-precise error. */
fun CliktCommand.loadConfig(file: Path): LoadedConfig =
    try {
        ConfigLoader.load(file)
    } catch (e: ConfigException) {
        fail("error    ${e.format(file)}")
    }

/** The SDK archive the config points at: a local `.aar`, or Maven coordinates downloaded into the cache. */
fun CliktCommand.resolveSdkArchive(loaded: LoadedConfig): Path {
    val source = loaded.config.source
    source.aar?.let { return loaded.resolve(it) }
    val coordinates =
        source.maven?.let { MavenCoordinates.parse(it) } ?: fail("error    source: no SDK source; set source.aar or source.maven")
    return resolveMaven(coordinates, source.repositories)
}

/** Downloads [coordinates] (or reuses the cached copy) and returns the archive. */
fun CliktCommand.resolveMaven(
    coordinates: MavenCoordinates,
    repositories: List<String>,
): Path =
    try {
        echo("Resolving $coordinates ...", err = true)
        MavenResolver(extraRepositories = repositories).resolve(coordinates)
    } catch (e: MavenResolutionException) {
        fail("error    source.maven: ${e.message}  fix: check the coordinates and add the repository under source.repositories")
    }

/**
 * Resolves and parses the SDK named by the config, after the source checks have passed.
 *
 * Kotlin Multiplatform umbrella artifacts (`okio`) hold no classes; their JVM code lives in a
 * sibling (`okio-jvm`, `-android`) that Gradle picks through module metadata. Henosis parses the
 * sibling but keeps the umbrella coordinates in the IR, so generated build files align with other
 * consumers of the same library.
 */
fun CliktCommand.loadSdk(loaded: LoadedConfig): ApiModel {
    val archive = resolveSdkArchive(loaded)
    val model = AndroidSdkParser.parse(archive)
    if (model.classes.isNotEmpty()) return model
    val source = loaded.config.source
    val coordinates = source.maven?.let { MavenCoordinates.parse(it) }
    if (coordinates != null) {
        for (suffix in listOf("-jvm", "-android")) {
            val sibling = coordinates.copy(artifact = coordinates.artifact + suffix)
            val siblingArchive =
                try {
                    MavenResolver(extraRepositories = source.repositories).resolve(sibling)
                } catch (e: MavenResolutionException) {
                    continue
                }
            val siblingModel = AndroidSdkParser.parse(siblingArchive)
            if (siblingModel.classes.isNotEmpty()) {
                echo(
                    "note     $coordinates has no classes of its own; parsing its JVM artifact $sibling (the generated packages keep $coordinates)",
                    err = true,
                )
                return siblingModel
            }
        }
    }
    fail(
        "error    source: $archive contains no public classes  fix: point source at the artifact that holds the SDK's classes (for Kotlin Multiplatform libraries, the -jvm artifact)",
    )
}

/**
 * Runs the full doctor pipeline for [file]: config load, source checks, SDK parse, rules.
 * Prints one line per finding plus a summary, and returns the result (never exits on findings;
 * callers decide).
 */
fun CliktCommand.runDoctor(file: Path): DoctorResult {
    val loaded = loadConfig(file)
    val sourceFindings = Doctor.checkSource(loaded)
    val result =
        if (sourceFindings.isNotEmpty()) {
            DoctorResult(sourceFindings, null)
        } else {
            Doctor.run(loaded.config, loadSdk(loaded), HenosisVersion.current)
        }
    result.findings.forEach { echo(it.format()) }
    echo(result.summary())
    return result
}
