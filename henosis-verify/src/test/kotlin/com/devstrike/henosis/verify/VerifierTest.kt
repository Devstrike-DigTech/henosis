package com.devstrike.henosis.verify

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class VerifierTest {
    private val work: Path = Path.of("build/tmp/verifier").also { Files.createDirectories(it) }

    private fun flutterPackage(): Path {
        val dir = work.resolve("flutter/sample_sdk/example")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("pubspec.yaml"), "name: x\n")
        return work.resolve("flutter/sample_sdk")
    }

    private fun rnPackage(): Path {
        val dir = work.resolve("react-native/sample-sdk/example/android")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("settings.gradle"), "")
        Files.createDirectories(work.resolve("react-native/sample-sdk/node_modules"))
        return work.resolve("react-native/sample-sdk")
    }

    private fun fakeTool(name: String): String {
        val tool = work.resolve("bin/$name")
        Files.createDirectories(tool.parent)
        Files.writeString(tool, "#!/bin/sh\nexit 0\n")
        tool.toFile().setExecutable(true)
        return tool.toString()
    }

    @Test
    fun `runs the documented commands in order and passes when every step exits 0`() {
        val commands = mutableListOf<String>()
        val runner =
            CommandRunner { command, directory, _, onLine ->
                commands += command.joinToString(" ") + " @ " + directory.fileName
                onLine("ok")
                0
            }
        val verifier = Verifier(runner, flutterBinary = fakeTool("flutter"), yarnBinary = fakeTool("yarn"))
        verifier.verifyFlutter(flutterPackage()).status shouldBe VerifyStatus.PASSED
        verifier.verifyReactNative(rnPackage()).status shouldBe VerifyStatus.PASSED
        commands.map { it.substringAfter("bin/") } shouldBe
            listOf(
                "flutter pub get @ example",
                "flutter build apk --debug @ example",
                "yarn typecheck @ sample-sdk",
                "./gradlew assembleDebug --console=plain @ android",
            )
    }

    @Test
    fun `a failing step reports the command and the output tail`() {
        val runner =
            CommandRunner { command, _, _, onLine ->
                if (command.contains("build")) {
                    onLine("Gradle task assembleDebug failed")
                    1
                } else {
                    0
                }
            }
        val result = Verifier(runner, flutterBinary = fakeTool("flutter")).verifyFlutter(flutterPackage())
        result.status shouldBe VerifyStatus.BUILD_FAILED
        result.detail shouldContain "flutter build apk --debug"
        result.detail shouldContain "Gradle task assembleDebug failed"
    }

    @Test
    fun `missing toolchain and missing package are distinguished`() {
        val runner = CommandRunner { _, _, _, _ -> 0 }
        Verifier(runner, flutterBinary = "/nonexistent/flutter").verifyFlutter(flutterPackage()).status shouldBe
            VerifyStatus.TOOLCHAIN_MISSING
        Verifier(runner, flutterBinary = fakeTool("flutter")).verifyFlutter(work.resolve("nowhere")).status shouldBe
            VerifyStatus.PACKAGE_MISSING
    }
}
