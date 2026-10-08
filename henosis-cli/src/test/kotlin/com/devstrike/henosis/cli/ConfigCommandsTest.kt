package com.devstrike.henosis.cli

import com.devstrike.henosis.core.config.ConfigLoader
import com.devstrike.henosis.core.ir.IrJson
import com.devstrike.henosis.testing.Golden
import com.github.ajalt.clikt.testing.test
import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** `init`, `doctor` and `generate --emit-ir` against the real sample SDK, with golden output. */
class ConfigCommandsTest {
    private val repoRoot: Path = Path.of(System.getProperty("henosis.repoRoot"))
    private val fixtureConfig: Path = repoRoot.resolve("fixtures/henosis.yaml")
    private val workDir: Path = Path.of("build/tmp/config-commands").also { Files.createDirectories(it) }

    @Test
    fun `doctor passes the sample config with only Long warnings`() {
        val result = heno().test(listOf("doctor", "-c", fixtureConfig.toString()))
        result.statusCode shouldBe 0
        Golden.check("doctor/sample-sdk.txt", result.stdout)
    }

    @Test
    fun `doctor fails a broken config with one line per finding`() {
        val broken =
            """
            name: Sample SDK
            version: 0.1.0
            source:
              aar: ${Golden.sampleAar}
            facades:
              - class: com.devstrike.sample.Greeter
                constructor: none
                methods:
                  - greet
                  - divide
                  - generic
                  - missing
                events:
                  - count
              - class: com.devstrike.sample.Level
            targets:
              flutter:
                package: sample_sdk
                org: com.devstrike
            """.trimIndent()
        val file = workDir.resolve("broken.yaml")
        Files.writeString(file, broken)
        val result = heno().test(listOf("doctor", "-c", file.toString()))
        result.statusCode shouldBe 1
        Golden.check("doctor/broken.txt", result.stdout)
    }

    @Test
    fun `doctor reports config syntax errors with the key path`() {
        val file = workDir.resolve("typo.yaml")
        Files.writeString(file, "name: x\nversion: 0.1.0\nsource:\n  aar: ${Golden.sampleAar}\nfacades:\n  - class: a.B\n    method: [x]\n")
        val result = heno().test(listOf("doctor", "-c", file.toString()))
        result.statusCode shouldBe 1
        result.stderr shouldContain "facades[0].method"
        result.stderr shouldContain "typo.yaml:7:5"
    }

    @Test
    fun `doctor reports a missing aar before touching the SDK`() {
        val file = workDir.resolve("missing.yaml")
        Files.writeString(file, "name: x\nversion: 0.1.0\nsource:\n  aar: nowhere.aar\nfacades:\n  - class: a.B\n")
        val result = heno().test(listOf("doctor", "-c", file.toString()))
        result.statusCode shouldBe 1
        result.stdout shouldContain "error    source.aar: file not found"
        result.stdout shouldContain "doctor: 1 error(s), 0 warning(s)"
    }

    @Test
    fun `generate --emit-ir writes ir json that matches the golden file and the published schema`() {
        val out = workDir.resolve("generate")
        val result = heno().test(listOf("generate", "-c", fixtureConfig.toString(), "--emit-ir", "--out", out.toString()))
        result.statusCode shouldBe 0
        val irJson = Files.readString(out.resolve("ir.json"))
        Golden.check("ir/sample-sdk.json", irJson)

        val schema =
            SchemaRegistry
                .withDefaultDialect(
                    SpecificationVersion.DRAFT_2020_12,
                ).getSchema(Files.readString(repoRoot.resolve("docs/ir.schema.json")), InputFormat.JSON)
        val errors = schema.validate(irJson, InputFormat.JSON)
        withClue(errors.joinToString("\n") { it.message }) { errors.shouldBeEmpty() }

        IrJson.decode(irJson).facades.map { it.name } shouldContainExactly listOf("Greeter", "Counter")
    }

    @Test
    fun `generate writes both packages and copies the aar`() {
        val out = workDir.resolve("generate-all")
        val result = heno().test(listOf("generate", "-c", fixtureConfig.toString(), "--out", out.toString()))
        result.statusCode shouldBe 0
        result.stdout shouldContain "Wrote Flutter plugin to"
        result.stdout shouldContain "Wrote React Native package to"
        val plugin = out.resolve("flutter/sample_sdk")
        Files.isRegularFile(plugin.resolve("pubspec.yaml")) shouldBe true
        Files.isRegularFile(plugin.resolve("lib/src/greeter.dart")) shouldBe true
        Files.size(plugin.resolve("android/libs/maven/henosis/local/sample_sdk/0.1.0/sample_sdk-0.1.0.aar")) shouldBe
            Files.size(Golden.sampleAar)
        val rn = out.resolve("react-native/sample-sdk")
        Files.isRegularFile(rn.resolve("package.json")) shouldBe true
        Files.isRegularFile(rn.resolve("src/NativeSampleSdkGreeter.ts")) shouldBe true
        Files.isRegularFile(rn.resolve("example/android/gradle/wrapper/gradle-wrapper.jar")) shouldBe true
        Files.isExecutable(rn.resolve("example/android/gradlew")) shouldBe true
        Files.size(rn.resolve("android/libs/maven/henosis/local/sample_sdk/0.1.0/sample_sdk-0.1.0.aar")) shouldBe
            Files.size(Golden.sampleAar)
    }

    @Test
    fun `generate --target selects one package`() {
        val out = workDir.resolve("generate-rn")
        val result = heno().test(listOf("generate", "-c", fixtureConfig.toString(), "--target", "rn", "--out", out.toString()))
        result.statusCode shouldBe 0
        Files.exists(out.resolve("flutter")) shouldBe false
        Files.isDirectory(out.resolve("react-native/sample-sdk")) shouldBe true
    }

    @Test
    fun `generate refuses to run with doctor errors`() {
        val file = workDir.resolve("bad-for-generate.yaml")
        Files.writeString(
            file,
            "name: x\nversion: 0.1.0\nsource:\n  aar: ${Golden.sampleAar}\nfacades:\n  - class: a.B\ntargets:\n  flutter:\n    package: x\n    org: a.b\n",
        )
        val out = workDir.resolve("generate-bad")
        val result = heno().test(listOf("generate", "-c", file.toString(), "--emit-ir", "--out", out.toString()))
        result.statusCode shouldBe 1
        Files.exists(out.resolve("ir.json")) shouldBe false
    }

    @Test
    fun `init writes a loadable starter config with every class commented out`() {
        val out = workDir.resolve("init/henosis.yaml")
        Files.deleteIfExists(out)
        val result = heno().test(listOf("init", Golden.sampleAar.toString(), "--out", out.toString()))
        result.statusCode shouldBe 0
        result.stdout shouldContain "5 public classes"
        val text = Files.readString(out)
        Golden.check("init/sample-sdk.yaml", text)

        val config = ConfigLoader.parse(text)
        config.name shouldBe "sample_sdk"
        config.facades.shouldBeEmpty()
        config.source.aar shouldBe "../../../../../fixtures/sample-sdk/build/outputs/aar/sample-sdk-release.aar"
        text shouldNotContain repoRoot.toString()

        heno().test(listOf("init", Golden.sampleAar.toString(), "--out", out.toString())).let {
            it.statusCode shouldBe 1
            it.stderr shouldContain "already exists"
        }
        heno().test(listOf("init", Golden.sampleAar.toString(), "--out", out.toString(), "--force")).statusCode shouldBe 0
    }

    @Test
    fun `init rejects maven coordinates for now`() {
        val result = heno().test(listOf("init", "com.example:sdk:1.0.0", "--out", workDir.resolve("maven.yaml").toString()))
        result.statusCode shouldBe 1
        result.stderr shouldContain "Maven coordinates"
    }
}
