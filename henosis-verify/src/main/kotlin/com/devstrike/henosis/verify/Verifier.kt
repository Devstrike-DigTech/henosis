package com.devstrike.henosis.verify

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** How a verification of one target ended. */
enum class VerifyStatus {
    PASSED,
    BUILD_FAILED,
    TOOLCHAIN_MISSING,
    PACKAGE_MISSING,
}

/** The result for one target: its status, the commands that ran and the tail of their output. */
data class VerifyResult(
    val target: String,
    val status: VerifyStatus,
    val detail: String,
) {
    val passed: Boolean get() = status == VerifyStatus.PASSED
}

/** Runs a command and captures its output. Separated so tests can replace it. */
fun interface CommandRunner {
    /** Returns the exit code; [onLine] receives each output line (stdout and stderr merged). */
    fun run(
        command: List<String>,
        directory: Path,
        environment: Map<String, String>,
        onLine: (String) -> Unit,
    ): Int
}

/** The default runner: a real subprocess with inherited environment plus [environment]. */
object ProcessCommandRunner : CommandRunner {
    override fun run(
        command: List<String>,
        directory: Path,
        environment: Map<String, String>,
        onLine: (String) -> Unit,
    ): Int {
        val builder = ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true)
        builder.environment().putAll(environment)
        val process = builder.start()
        process.inputStream.bufferedReader().useLines { lines -> lines.forEach(onLine) }
        if (!process.waitFor(60, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            return -1
        }
        return process.exitValue()
    }
}

/**
 * Builds the generated example apps so broken output is caught before it ships:
 * `flutter build apk --debug` for the Flutter plugin and `./gradlew assembleDebug` for the
 * React Native example (after `yarn install`, which autolinking needs).
 */
class Verifier(
    private val runner: CommandRunner = ProcessCommandRunner,
    private val flutterBinary: String = "flutter",
    private val yarnBinary: String = "yarn",
    private val environment: Map<String, String> = emptyMap(),
    private val log: (String) -> Unit = {},
) {
    /** Verifies the Flutter plugin at [packageDir] (the directory holding `pubspec.yaml`). */
    fun verifyFlutter(packageDir: Path): VerifyResult {
        val example = packageDir.resolve("example")
        if (!Files.isRegularFile(
                example.resolve("pubspec.yaml"),
            )
        ) {
            return VerifyResult("flutter", VerifyStatus.PACKAGE_MISSING, "no example app at $example")
        }
        if (!available(
                flutterBinary,
                example,
            )
        ) {
            return VerifyResult(
                "flutter",
                VerifyStatus.TOOLCHAIN_MISSING,
                "'$flutterBinary' is not on the PATH; install Flutter or pass --flutter",
            )
        }
        return runSteps("flutter", example, listOf(listOf(flutterBinary, "pub", "get"), listOf(flutterBinary, "build", "apk", "--debug")))
    }

    /** Verifies the React Native package at [packageDir] (the directory holding `package.json`). */
    fun verifyReactNative(packageDir: Path): VerifyResult {
        val android = packageDir.resolve("example/android")
        if (!Files.isRegularFile(
                android.resolve("settings.gradle"),
            )
        ) {
            return VerifyResult("rn", VerifyStatus.PACKAGE_MISSING, "no example app at $android")
        }
        if (!available(
                yarnBinary,
                packageDir,
            )
        ) {
            return VerifyResult(
                "rn",
                VerifyStatus.TOOLCHAIN_MISSING,
                "'$yarnBinary' is not on the PATH; install Node 22+ and enable Corepack, or pass --yarn",
            )
        }
        val steps = mutableListOf<List<String>>()
        if (!Files.isDirectory(packageDir.resolve("node_modules"))) steps += listOf(yarnBinary, "install")
        steps += listOf(yarnBinary, "typecheck")
        val install = runSteps("rn", packageDir, steps)
        if (!install.passed) return install
        val gradlew = if (System.getProperty("os.name").startsWith("Windows")) "gradlew.bat" else "./gradlew"
        return runSteps("rn", android, listOf(listOf(gradlew, "assembleDebug", "--console=plain")))
    }

    private fun available(
        binary: String,
        directory: Path,
    ): Boolean {
        if (binary.contains(File.separator)) return Files.isExecutable(Path.of(binary))
        val pathDirs = (environment["PATH"] ?: System.getenv("PATH") ?: "").split(File.pathSeparator)
        val names =
            if (System
                    .getProperty(
                        "os.name",
                    ).startsWith("Windows")
            ) {
                listOf("$binary.cmd", "$binary.bat", "$binary.exe", binary)
            } else {
                listOf(binary)
            }
        return pathDirs.any { dir -> names.any { Files.isExecutable(Path.of(dir, it)) } } ||
            directory.resolve(binary).let { Files.isExecutable(it) }
    }

    private fun runSteps(
        target: String,
        directory: Path,
        steps: List<List<String>>,
    ): VerifyResult {
        val tail = ArrayDeque<String>()
        for (command in steps) {
            log("$target: ${command.joinToString(" ")}  (in $directory)")
            tail.clear()
            val code =
                runner.run(command, directory, environment) { line ->
                    log(line)
                    tail.addLast(line)
                    if (tail.size > 40) tail.removeFirst()
                }
            if (code != 0) {
                return VerifyResult(
                    target,
                    VerifyStatus.BUILD_FAILED,
                    "`${command.joinToString(" ")}` exited with $code\n" + tail.joinToString("\n"),
                )
            }
        }
        return VerifyResult(target, VerifyStatus.PASSED, "${steps.size} step(s) passed")
    }
}
