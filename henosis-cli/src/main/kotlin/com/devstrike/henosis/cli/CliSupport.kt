package com.devstrike.henosis.cli

import com.devstrike.henosis.core.HenosisVersion
import com.devstrike.henosis.core.api.ApiModel
import com.devstrike.henosis.core.config.ConfigException
import com.devstrike.henosis.core.config.ConfigLoader
import com.devstrike.henosis.core.config.LoadedConfig
import com.devstrike.henosis.core.doctor.Doctor
import com.devstrike.henosis.core.doctor.DoctorResult
import com.devstrike.henosis.parser.android.AndroidSdkParser
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

/** Resolves and parses the SDK named by the config, after the source checks have passed. */
fun CliktCommand.loadSdk(loaded: LoadedConfig): ApiModel {
    val aar = loaded.config.source.aar ?: fail("error    source: no SDK source; set source.aar")
    return AndroidSdkParser.parse(loaded.resolve(aar))
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
