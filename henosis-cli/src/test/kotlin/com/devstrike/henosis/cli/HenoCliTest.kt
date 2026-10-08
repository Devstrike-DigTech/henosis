package com.devstrike.henosis.cli

import com.devstrike.henosis.core.HenosisVersion
import com.devstrike.henosis.testing.Golden
import com.github.ajalt.clikt.testing.test
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class HenoCliTest {
    @Test
    fun `--version prints the Henosis version`() {
        val result = heno().test("--version")
        result.statusCode shouldBe 0
        result.stdout.trim() shouldBe "heno version ${HenosisVersion.current}"
        HenosisVersion.current shouldBe "0.1.0"
    }

    @Test
    fun `no subcommand prints usage`() {
        val result = heno().test("")
        result.statusCode shouldBe 0
        result.output shouldContain "Usage: heno"
        result.output shouldContain "inspect"
    }

    @Test
    fun `inspect rejects a missing archive`() {
        val result = heno().test("inspect does-not-exist.aar")
        result.statusCode shouldBe 1
        result.output shouldContain "does-not-exist.aar"
    }

    @Test
    fun `inspect --json output matches the golden file`() {
        val result = heno().test(listOf("inspect", "--json", Golden.sampleAar.toString()))
        result.statusCode shouldBe 0
        Golden.check("inspect/sample-sdk.json", result.stdout)
    }

    @Test
    fun `inspect text output matches the golden file`() {
        val result = heno().test(listOf("inspect", Golden.sampleAar.toString()))
        result.statusCode shouldBe 0
        Golden.check("inspect/sample-sdk.txt", result.stdout)
    }

    @Test
    fun `inspect runs are deterministic`() {
        val first = heno().test(listOf("inspect", "--json", Golden.sampleAar.toString())).stdout
        val second = heno().test(listOf("inspect", "--json", Golden.sampleAar.toString())).stdout
        second shouldBe first
    }
}
