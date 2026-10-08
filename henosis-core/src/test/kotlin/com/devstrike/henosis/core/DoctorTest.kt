package com.devstrike.henosis.core

import com.devstrike.henosis.core.config.ConfigLoader
import com.devstrike.henosis.core.config.HenosisConfig
import com.devstrike.henosis.core.doctor.Doctor
import com.devstrike.henosis.core.doctor.DoctorFinding
import com.devstrike.henosis.core.doctor.DoctorLevel
import com.devstrike.henosis.core.doctor.DoctorResult
import com.devstrike.henosis.core.ir.IrConstructor
import com.devstrike.henosis.core.ir.IrEventSource
import com.devstrike.henosis.core.ir.IrTypeKind
import com.devstrike.henosis.core.ir.IrTypeRef
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

/** One test per doctor rule, each asserting the level, the key path and the message. */
class DoctorTest {
    private val base =
        """
        name: sample_sdk
        version: 0.1.0
        source:
          aar: ./sdk.aar
        facades:
          - class: com.x.Greeter
            constructor: context
            methods: [greet, greetMany, fetchProfile, "divide(Int, Int)", "divide(Double, Double) as divideDouble"]
            events: [ticks]
          - class: com.x.Counter
            methods: [increment, reset]
        targets:
          flutter:
            package: sample_sdk
            org: com.devstrike
          react_native:
            package: "@devstrike/sample-sdk"
            module: SampleSdk
        """.trimIndent()

    private fun run(yaml: String = base): DoctorResult = Doctor.run(ConfigLoader.parse(yaml), TestSdk.model, "0.1.0")

    private fun run(edit: (HenosisConfig) -> HenosisConfig): DoctorResult =
        Doctor.run(edit(ConfigLoader.parse(base)), TestSdk.model, "0.1.0")

    private fun DoctorResult.only(
        level: DoctorLevel,
        path: String,
        vararg messageParts: String,
    ): DoctorFinding {
        val matching = findings.filter { it.level == level && it.path == path }
        withClue("expected one $level at $path, got:\n${findings.joinToString("\n") { it.format() }}") { matching shouldHaveSize 1 }
        messageParts.forEach { matching.single().message shouldContain it }
        return matching.single()
    }

    private fun DoctorResult.errorPaths() = errors.map { it.path }

    // ---- the happy path ----------------------------------------------------------------------------

    @Test
    fun `a valid config builds the IR and only warns about Long`() {
        val result = run()
        result.hasErrors.shouldBeFalse()
        result.warnings.map { it.path }.toSet() shouldBe setOf("facades[0].methods[2]", "types[com.x.Profile].id")
        val ir = result.ir.shouldNotBeNull()
        ir.name shouldBe "sample_sdk"
        ir.henosisVersion shouldBe "0.1.0"
        ir.facades.map { it.name } shouldContainExactly listOf("Greeter", "Counter")
        val greeter = ir.facades[0]
        greeter.nativeClass shouldBe TestSdk.GREETER
        greeter.constructor shouldBe IrConstructor.CONTEXT
        greeter.methods.map { it.name } shouldContainExactly listOf("greet", "greetMany", "fetchProfile", "divide", "divideDouble")
        greeter.methods.single { it.name == "divideDouble" }.nativeName shouldBe "divide"
        val fetch = greeter.methods.single { it.name == "fetchProfile" }
        fetch.isSuspend.shouldBeTrue()
        fetch.returnType shouldBe IrTypeRef(IrTypeKind.DATA_CLASS, nullable = true, name = "Profile")
        greeter.methods.single { it.name == "greetMany" }.returnType shouldBe
            IrTypeRef(IrTypeKind.MAP, element = IrTypeRef(IrTypeKind.STRING))
        val ticks = greeter.events.single()
        ticks.source shouldBe IrEventSource.PROPERTY
        ticks.jvmName shouldBe "getTicks"
        ticks.elementType shouldBe IrTypeRef(IrTypeKind.INT)
        ir.facades[1].constructor shouldBe IrConstructor.NONE
        ir.types.map { it.name } shouldContainExactly listOf("Level", "Profile")
        val profile = ir.types.single { it.name == "Profile" }
        profile.fields.map { it.name } shouldContainExactly listOf("id", "name", "tags", "level")
        profile.fields.single { it.name == "level" }.type shouldBe IrTypeRef(IrTypeKind.ENUM, name = "Level")
        profile.jvmConstructorDescriptor shouldBe "(4)V"
        ir.types.single { it.name == "Level" }.entries shouldContainExactly listOf("BRONZE", "SILVER", "GOLD")
        ir.targets.flutter
            .shouldNotBeNull()
            .packageName shouldBe "sample_sdk"
    }

    @Test
    fun `an event may also be a zero-argument function returning Flow`() {
        val result = run(base.replace("events: [ticks]", "events: [ticksFn]"))
        result.hasErrors.shouldBeFalse()
        val event =
            result.ir
                .shouldNotBeNull()
                .facades[0]
                .events
                .single()
        event.source shouldBe IrEventSource.FUNCTION
        event.jvmName shouldBe "ticksFn"
    }

    @Test
    fun `findings are sorted by path in natural order with errors first`() {
        val result =
            run(
                base.replace(
                    "methods: [greet, greetMany, fetchProfile",
                    "methods: [nope, greet, greetMany, fetchProfile, nope2, nope3, nope4, nope5, nope6, nope7, nope8, nope9",
                ),
            )
        val paths = result.findings.map { it.path }
        paths shouldBe paths.sortedWith(compareBy { it.replace(Regex("\\d+")) { m -> m.value.padStart(8, '0') } })
        result.findings.first().path shouldBe "facades[0].methods[0]"
    }

    // ---- top-level rules ---------------------------------------------------------------------------

    @Test
    fun `name must be a package-like identifier`() {
        run(base.replace("name: sample_sdk", "name: Sample-SDK")).only(DoctorLevel.ERROR, "name", "'Sample-SDK'").hint shouldContain
            "sample_sdk"
    }

    @Test
    fun `non-semver version is a warning`() {
        run(base.replace("version: 0.1.0", "version: v1")).only(DoctorLevel.WARNING, "version", "'v1'")
    }

    @Test
    fun `at least one target is required`() {
        run {
            it.copy(
                targets =
                    com.devstrike.henosis.core.config
                        .TargetsConfig(),
            )
        }.only(DoctorLevel.ERROR, "targets", "no target")
    }

    @Test
    fun `target names are validated`() {
        val result =
            run(
                base
                    .replace("package: sample_sdk", "package: Sample-Sdk")
                    .replace("org: com.devstrike", "org: devstrike")
                    .replace("package: \"@devstrike/sample-sdk\"", "package: \"Devstrike SDK\"")
                    .replace("module: SampleSdk", "module: sampleSdk"),
            )
        result.errorPaths() shouldContainExactly
            listOf("targets.flutter.org", "targets.flutter.package", "targets.react_native.module", "targets.react_native.package")
    }

    @Test
    fun `source checks need the file system`() {
        val loaded =
            com.devstrike.henosis.core.config.LoadedConfig(
                ConfigLoader.parse(base),
                java.nio.file.Path
                    .of("/tmp/henosis-test/henosis.yaml"),
            )
        Doctor.checkSource(loaded).single().let {
            it.level shouldBe DoctorLevel.ERROR
            it.path shouldBe "source.aar"
            it.message shouldContain "file not found"
        }
        Doctor
            .checkSource(loaded.copy(config = loaded.config.copy(source = loaded.config.source.copy(aar = null, maven = "g:a:1"))))
            .single()
            .path shouldBe "source.maven"
        Doctor
            .checkSource(loaded.copy(config = loaded.config.copy(source = loaded.config.source.copy(maven = "g:a:1"))))
            .single()
            .message shouldContain "both"
        Doctor
            .checkSource(loaded.copy(config = loaded.config.copy(source = loaded.config.source.copy(aar = null))))
            .single()
            .message shouldContain "no SDK source"
    }

    // ---- facade rules ------------------------------------------------------------------------------

    @Test
    fun `no facades is an error`() {
        run(base.substringBefore("facades:") + "facades: []\n" + base.substringAfter("targets:").let { "targets:$it" })
            .only(DoctorLevel.ERROR, "facades", "no facades")
    }

    @Test
    fun `unknown class`() {
        run(
            base.replace("class: com.x.Counter", "class: com.x.Missing"),
        ).only(DoctorLevel.ERROR, "facades[1].class", "'com.x.Missing' not found")
    }

    @Test
    fun `facade must be a plain class`() {
        run(base.replace("class: com.x.Counter", "class: com.x.Util")).only(DoctorLevel.ERROR, "facades[1].class", "is an object")
        run(base.replace("class: com.x.Counter", "class: com.x.Level")).only(DoctorLevel.ERROR, "facades[1].class", "is an enum class")
        run(base.replace("class: com.x.Counter", "class: com.x.Box")).only(DoctorLevel.ERROR, "facades[1].class", "generic")
    }

    @Test
    fun `constructor kind must match an existing constructor`() {
        run(base.replace("constructor: context", "constructor: none"))
            .only(DoctorLevel.ERROR, "facades[0].constructor", "no public no-argument constructor", "constructor(context: Context)")
        run(base.replace("methods: [increment, reset]", "constructor: context\n    methods: [increment, reset]"))
            .only(DoctorLevel.ERROR, "facades[1].constructor", "android.content.Context")
    }

    @Test
    fun `exposed facade names must be valid and unique`() {
        run(base.replace("class: com.x.Counter", "class: com.x.Counter\n    as: Greeter"))
            .only(DoctorLevel.ERROR, "facades[1].as", "'Greeter' is already used by facades[0].class")
        run(
            base.replace("class: com.x.Counter", "class: com.x.Counter\n    as: 2fast"),
        ).only(DoctorLevel.ERROR, "facades[1].as", "not a valid")
    }

    @Test
    fun `a facade that exposes nothing is a warning`() {
        run(base.replace("methods: [increment, reset]", "methods: []")).only(DoctorLevel.WARNING, "facades[1]", "exposes nothing")
    }

    // ---- method rules ------------------------------------------------------------------------------

    @Test
    fun `selector syntax errors`() {
        run(base.replace("reset]", "\"reset(Int,\"]")).only(DoctorLevel.ERROR, "facades[1].methods[1]", "not a valid method selector")
    }

    @Test
    fun `unknown method`() {
        run(base.replace("reset]", "restart]")).only(DoctorLevel.ERROR, "facades[1].methods[1]", "'restart' not found on com.x.Counter")
    }

    @Test
    fun `ambiguous overload`() {
        run(base.replace("\"divide(Int, Int)\"", "divide"))
            .only(DoctorLevel.ERROR, "facades[0].methods[3]", "2 overloads", "divide(Double, Double) | divide(Int, Int)")
    }

    @Test
    fun `no overload matches the selector`() {
        run(base.replace("\"divide(Int, Int)\"", "\"divide(Long, Long)\""))
            .only(DoctorLevel.ERROR, "facades[0].methods[3]", "no overload of 'divide' matches divide(Long, Long)")
    }

    @Test
    fun `duplicate exposed method names need an alias`() {
        run(
            base.replace(" as divideDouble", ""),
        ).only(DoctorLevel.ERROR, "facades[0].methods[4]", "'divide' is already used by facades[0].methods[3]")
    }

    @Test
    fun `generic functions are errors`() {
        val result =
            run(base.replace("reset]", "reset]\n  - class: com.x.Greeter\n    as: G2\n    constructor: context\n    methods: [generic]"))
        result.errors
            .filter { it.path == "facades[2].methods[0]" }
            .map { it.message }
            .joinToString() shouldContain "generic"
    }

    @Test
    fun `unsupported parameter and return types are errors with the member named`() {
        run(
            base.replace(
                "reset]",
                "reset]\n  - class: com.x.Greeter\n    as: G2\n    constructor: context\n    methods: [useFile, badMap, badFlowParam]",
            ),
        ).let { r ->
            r.only(DoctorLevel.ERROR, "facades[2].methods[0]", "com.x.Greeter.useFile parameter 'f'", "java.io.File")
            r.only(DoctorLevel.ERROR, "facades[2].methods[1]", "com.x.Greeter.badMap return type", "Map keys must be non-null String")
            r.only(DoctorLevel.ERROR, "facades[2].methods[2]", "parameter 'events'", "Flow is only supported as an event")
        }
    }

    @Test
    fun `Long is a warning not an error`() {
        run().only(DoctorLevel.WARNING, "facades[0].methods[2]", "com.x.Greeter.fetchProfile parameter 'id'", "2^53")
    }

    @Test
    fun `Java platform types are warnings`() {
        val result = run(base.replace("reset]", "reset]\n  - class: com.x.LegacyApi\n    methods: [ping]"))
        result.hasErrors.shouldBeFalse()
        result.warnings.filter { it.path == "facades[2].methods[0]" } shouldHaveSize 2
        result.ir
            .shouldNotBeNull()
            .facades[2]
            .methods
            .single()
            .returnType shouldBe IrTypeRef(IrTypeKind.STRING, nullable = true)
    }

    // ---- event rules -------------------------------------------------------------------------------

    @Test
    fun `unknown event`() {
        run(base.replace("events: [ticks]", "events: [beats]")).only(DoctorLevel.ERROR, "facades[0].events[0]", "'beats' not found")
    }

    @Test
    fun `event must be a Flow`() {
        run(
            base.replace("events: [ticks]", "events: [count]"),
        ).only(DoctorLevel.ERROR, "facades[0].events[0]", "is 'Int', but events must be Flow<T>")
    }

    @Test
    fun `events share the method namespace`() {
        run(
            base
                .replace(
                    "events: [ticks]",
                    "events: [ticks]\n    as: Greeter",
                ).replace("methods: [greet,", "methods: [\"greet as ticks\","),
        ).only(DoctorLevel.ERROR, "facades[0].events[0]", "'ticks' is already used by facades[0].methods[0]")
    }

    // ---- type rules --------------------------------------------------------------------------------

    @Test
    fun `types include must name existing data classes or enums`() {
        run(base.replace("targets:", "types:\n  include: [com.x.Nope, com.x.Counter]\ntargets:")).let { r ->
            r.only(DoctorLevel.ERROR, "types.include[0]", "'com.x.Nope' not found")
            r.only(DoctorLevel.ERROR, "types.include[1]", "'com.x.Counter' is a class;")
        }
    }

    @Test
    fun `types include adds otherwise unreachable types to the IR`() {
        val result = run(base.replace("targets:", "types:\n  include: [com.x.Level]\ntargets:").replace("fetchProfile, ", ""))
        result.hasErrors.shouldBeFalse()
        result.ir
            .shouldNotBeNull()
            .types
            .map { it.name } shouldContainExactly listOf("Level")
    }

    @Test
    fun `reachable data classes with unsupported fields are errors`() {
        run(base.replace("methods: [greet,", "methods: [broken, greet,"))
            .only(DoctorLevel.ERROR, "types[com.x.Broken].path", "type:", "java.io.File")
    }

    @Test
    fun `nothing reachable means no types`() {
        val result =
            run(
                base.replace(
                    "methods: [greet, greetMany, fetchProfile, \"divide(Int, Int)\", \"divide(Double, Double) as divideDouble\"]",
                    "methods: [greet]",
                ),
            )
        result.hasErrors.shouldBeFalse()
        result.ir
            .shouldNotBeNull()
            .types
            .shouldBeEmpty()
    }
}
