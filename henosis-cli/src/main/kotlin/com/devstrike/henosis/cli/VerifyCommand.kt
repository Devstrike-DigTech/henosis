package com.devstrike.henosis.cli

import com.devstrike.henosis.gen.flutter.FlutterGenerator
import com.devstrike.henosis.gen.reactnative.ReactNativeGenerator
import com.devstrike.henosis.verify.Verifier
import com.devstrike.henosis.verify.VerifyResult
import com.devstrike.henosis.verify.VerifyStatus
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path

/** `heno verify`: builds every generated example app and reports pass/fail per target. */
class VerifyCommand : CliktCommand(name = "verify") {
    private val config by configOption()

    private val targets by option("-t", "--target", help = "Comma-separated targets: flutter, rn (default: every target in the config)")
        .split(",")
        .default(emptyList())

    private val out by option(
        "-o",
        "--out",
        help = "Directory heno generate wrote to",
    ).path(canBeFile = false).default(Path.of("henosis-out"))

    private val flutter by option("--flutter", help = "Flutter executable (default: `flutter` on the PATH)").default("flutter")

    private val yarn by option("--yarn", help = "Yarn executable (default: `yarn` on the PATH)").default("yarn")

    private val verbose by option("-v", "--verbose", help = "Stream build output").flag()

    override fun help(context: Context): String =
        "Build the generated example apps (flutter build apk --debug, ./gradlew assembleDebug) and report pass/fail per target."

    override fun run() {
        val loaded = loadConfig(config)
        val cfg = loaded.config.targets
        val wanted =
            targets.ifEmpty {
                listOfNotNull(
                    cfg.flutter?.let { FlutterGenerator.TARGET },
                    cfg.reactNative?.let { ReactNativeGenerator.TARGET },
                )
            }
        if (wanted.isEmpty()) fail("error    targets: nothing to verify; add targets.flutter or targets.react_native")
        val verifier = Verifier(flutterBinary = flutter, yarnBinary = yarn, log = { if (verbose) echo(it, err = true) })
        val results =
            wanted.map { target ->
                when (target) {
                    FlutterGenerator.TARGET -> {
                        val pkg = cfg.flutter ?: fail("error    --target flutter: the config has no targets.flutter section")
                        echo("verify flutter: building ${out.resolve("flutter/${pkg.packageName}/example")} ...")
                        verifier.verifyFlutter(out.resolve("flutter/${pkg.packageName}"))
                    }

                    ReactNativeGenerator.TARGET -> {
                        val pkg = cfg.reactNative ?: fail("error    --target rn: the config has no targets.react_native section")
                        val dir = out.resolve("react-native/${pkg.packageName.substringAfterLast('/')}")
                        echo("verify rn: building ${dir.resolve("example/android")} ...")
                        verifier.verifyReactNative(dir)
                    }

                    else -> {
                        fail("error    --target: unknown target '$target'; use flutter and/or rn")
                    }
                }
            }
        results.forEach { report(it) }
        val worst = results.map { it.status }.maxByOrNull { severity(it) } ?: VerifyStatus.PASSED
        echo("verify: ${results.count { it.passed }}/${results.size} target(s) passed")
        when (worst) {
            VerifyStatus.PASSED -> Unit
            VerifyStatus.TOOLCHAIN_MISSING -> throw ProgramResult(ExitCode.TOOLCHAIN_MISSING)
            VerifyStatus.PACKAGE_MISSING -> throw ProgramResult(ExitCode.VALIDATION)
            VerifyStatus.BUILD_FAILED -> throw ProgramResult(ExitCode.BUILD_FAILED)
        }
    }

    private fun report(result: VerifyResult) {
        val label =
            when (result.status) {
                VerifyStatus.PASSED -> "PASS"
                VerifyStatus.BUILD_FAILED -> "FAIL"
                VerifyStatus.TOOLCHAIN_MISSING -> "SKIP (toolchain missing)"
                VerifyStatus.PACKAGE_MISSING -> "SKIP (not generated)"
            }
        echo("${result.target.padEnd(8)} $label")
        if (!result.passed) result.detail.lineSequence().forEach { echo("    $it") }
    }

    private fun severity(status: VerifyStatus) =
        when (status) {
            VerifyStatus.PASSED -> 0
            VerifyStatus.PACKAGE_MISSING -> 1
            VerifyStatus.TOOLCHAIN_MISSING -> 2
            VerifyStatus.BUILD_FAILED -> 3
        }
}
