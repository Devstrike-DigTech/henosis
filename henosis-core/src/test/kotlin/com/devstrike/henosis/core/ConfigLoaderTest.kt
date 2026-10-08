package com.devstrike.henosis.core

import com.devstrike.henosis.core.config.ConfigException
import com.devstrike.henosis.core.config.ConfigLoader
import com.devstrike.henosis.core.config.ConstructorKind
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class ConfigLoaderTest {
    private val valid =
        """
        name: sample_sdk
        version: 0.1.0
        source:
          aar: ./sdk.aar
        facades:
          - class: com.x.Greeter
            as: Greeter
            constructor: context
            methods:
              - greet
              - divide(Int, Int)
            events:
              - ticks
        types:
          include: [com.x.Extra]
        targets:
          flutter:
            package: sample_sdk
            org: com.devstrike
          react_native:
            package: "@devstrike/sample-sdk"
            module: SampleSdk
        """.trimIndent()

    @Test
    fun `parses a complete config`() {
        val config = ConfigLoader.parse(valid)
        config.name shouldBe "sample_sdk"
        config.source.aar shouldBe "./sdk.aar"
        val facade = config.facades.single()
        facade.className shouldBe "com.x.Greeter"
        facade.exposedName shouldBe "Greeter"
        facade.constructor shouldBe ConstructorKind.CONTEXT
        facade.methods shouldContainExactly listOf("greet", "divide(Int, Int)")
        facade.events shouldContainExactly listOf("ticks")
        config.types.include shouldContainExactly listOf("com.x.Extra")
        config.targets.flutter
            .shouldNotBeNull()
            .packageName shouldBe "sample_sdk"
        config.targets.reactNative
            .shouldNotBeNull()
            .module shouldBe "SampleSdk"
    }

    @Test
    fun `defaults apply when optional keys are absent`() {
        val config = ConfigLoader.parse("name: x\nversion: 1.0.0\nsource:\n  aar: a.aar\n")
        config.facades shouldBe emptyList()
        config.types.include shouldBe emptyList()
        config.targets.flutter shouldBe null
    }

    @Test
    fun `an empty facades key is treated as no facades`() {
        val config = ConfigLoader.parse("name: x\nversion: 1.0.0\nsource:\n  aar: a.aar\nfacades:\n  # - class: com.x.Y\n")
        config.facades shouldBe emptyList()
    }

    @Test
    fun `unknown keys are errors with the exact path`() {
        val e = shouldThrow<ConfigException> { ConfigLoader.parse(valid.replace("    events:", "    event:")) }
        e.path shouldBe "facades[0].event"
        e.message shouldContain "event"
        e.line.shouldNotBeNull()
    }

    @Test
    fun `missing required keys name the key`() {
        val e = shouldThrow<ConfigException> { ConfigLoader.parse("version: 1.0.0\nsource:\n  aar: a.aar\n") }
        e.message shouldContain "name"
    }

    @Test
    fun `wrong value types name the path`() {
        val e = shouldThrow<ConfigException> { ConfigLoader.parse(valid.replace("constructor: context", "constructor: sometimes")) }
        e.path shouldBe "facades[0].constructor"
        e.message shouldContain "sometimes"
    }

    @Test
    fun `malformed yaml is reported at the root`() {
        val e = shouldThrow<ConfigException> { ConfigLoader.parse("name: [unclosed\n") }
        e.format() shouldContain e.path
    }
}
