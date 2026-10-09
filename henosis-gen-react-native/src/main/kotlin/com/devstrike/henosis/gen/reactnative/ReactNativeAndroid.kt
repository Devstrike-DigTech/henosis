package com.devstrike.henosis.gen.reactnative

import com.devstrike.henosis.core.codegen.CodeWriter
import com.devstrike.henosis.core.codegen.CopiedFile
import com.devstrike.henosis.core.codegen.GeneratedFile
import com.devstrike.henosis.core.codegen.GeneratedHeader
import com.devstrike.henosis.core.codegen.GeneratorContext
import com.devstrike.henosis.core.ir.Ir
import com.devstrike.henosis.core.ir.IrConstructor
import com.devstrike.henosis.core.ir.IrFacade
import com.devstrike.henosis.core.ir.IrTypeKind
import com.devstrike.henosis.core.ir.IrTypeRef
import com.devstrike.henosis.gen.bridge.BridgeGenerator

/** Generates the package's `android/` directory: Gradle build, TurboModules over the shared bridge, and the ReactPackage. */
internal class ReactNativeAndroid(
    private val ir: Ir,
    private val moduleName: String,
    private val androidPackage: String,
    private val context: GeneratorContext,
    private val ts: TsLibrary,
) {
    private val version = ir.henosisVersion
    private val packagePath = androidPackage.replace('.', '/')
    private val coordinates = ir.source.coordinates.split(':')

    val packageClass = "${moduleName}Package"

    fun files(): List<GeneratedFile> {
        val files = mutableListOf<GeneratedFile>()
        files += GeneratedFile("android/build.gradle", buildGradle())
        files +=
            GeneratedFile(
                "android/src/main/AndroidManifest.xml",
                "${GeneratedHeader.xml(version)}\n<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" />\n",
            )
        files += GeneratedFile("android/src/main/java/$packagePath/HenosisReact.kt", henosisReact())
        files += GeneratedFile("android/src/main/java/$packagePath/$packageClass.kt", reactPackage())
        ir.facades.forEach { facade ->
            files +=
                GeneratedFile("android/src/main/java/$packagePath/${moduleClass(facade)}.kt", module(facade))
        }
        BridgeGenerator.generate(ir, "$androidPackage.bridge").forEach { bridge ->
            files += GeneratedFile("android/src/main/java/$packagePath/bridge/${bridge.path}", bridge.content)
        }
        ir.source.aarFileName?.let { files += GeneratedFile(localMavenPath("pom"), pom()) }
        return files
    }

    fun copies(): List<CopiedFile> {
        val aar = context.aarFile ?: return emptyList()
        if (ir.source.aarFileName == null) return emptyList()
        return listOf(CopiedFile(localMavenPath("aar"), aar))
    }

    private fun moduleClass(facade: IrFacade) = "${ts.turboModuleName(facade)}Module"

    private fun specClass(facade: IrFacade) = "${ts.specName(facade)}Spec"

    private fun localMavenPath(extension: String): String {
        val (group, artifact, ver) = coordinates
        return "android/libs/maven/${group.replace('.', '/')}/$artifact/$ver/$artifact-$ver.$extension"
    }

    private fun pom(): String {
        val (group, artifact, ver) = coordinates
        return """
            |${GeneratedHeader.xml(version)}
            |<project xmlns="http://maven.apache.org/POM/4.0.0">
            |  <modelVersion>4.0.0</modelVersion>
            |  <groupId>$group</groupId>
            |  <artifactId>$artifact</artifactId>
            |  <version>$ver</version>
            |  <packaging>aar</packaging>
            |</project>
            |
            """.trimMargin()
    }

    private fun buildGradle(): String {
        val w = CodeWriter()
        w.line(GeneratedHeader.slash(version))
        w.block("buildscript {") {
            line("ext.$moduleName = [")
            indent {
                line("kotlinVersion: \"${ReactNativeToolchain.LIBRARY_KOTLIN}\",")
                line("minSdkVersion: ${ReactNativeToolchain.MIN_SDK},")
                line("compileSdkVersion: ${ReactNativeToolchain.COMPILE_SDK}")
            }
            line("]")
            line()
            block("ext.getExtOrDefault = { prop ->") {
                block("if (rootProject.ext.has(prop)) {") { line("return rootProject.ext.get(prop)") }
                line()
                line("return $moduleName[prop]")
            }
            line()
            block("repositories {") {
                line("google()")
                line("mavenCentral()")
            }
            line()
            block("dependencies {") {
                line("classpath \"com.android.tools.build:gradle:${ReactNativeToolchain.LIBRARY_AGP}\"")
                line("// noinspection DifferentKotlinGradleVersion")
                line("classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:\${getExtOrDefault('kotlinVersion')}\"")
            }
        }
        w.line()
        w.line("apply plugin: \"com.android.library\"")
        w.line()
        w.block("if (project.extensions.findByName(\"kotlin\") == null) {") { line("apply plugin: \"kotlin-android\"") }
        w.line()
        w.line("apply plugin: \"com.facebook.react\"")
        w.line()
        w.block("android {") {
            line("namespace \"$androidPackage\"")
            line()
            line("compileSdkVersion getExtOrDefault(\"compileSdkVersion\")")
            line()
            block("defaultConfig {") { line("minSdkVersion getExtOrDefault(\"minSdkVersion\")") }
            line()
            block("compileOptions {") {
                line("sourceCompatibility JavaVersion.VERSION_17")
                line("targetCompatibility JavaVersion.VERSION_17")
            }
        }
        if (ir.source.aarFileName != null || ir.source.repositories.isNotEmpty()) {
            w.line()
            w.line("// Consumers resolve the SDK from the same repositories, so every project of the build must see them.")
            if (ir.source.aarFileName != null) w.line("def sdkRepository = uri(\"\${projectDir}/libs/maven\")")
            w.block("rootProject.allprojects {") {
                block("repositories {") {
                    if (ir.source.aarFileName != null) line("maven { url sdkRepository }")
                    ir.source.repositories.forEach { line("maven { url \"$it\" }") }
                }
            }
        }
        w.line()
        w.block("dependencies {") {
            line("implementation \"com.facebook.react:react-android\"")
            line("implementation \"${ir.source.coordinates}\"")
            line("implementation \"org.jetbrains.kotlinx:kotlinx-coroutines-android:${ReactNativeToolchain.COROUTINES}\"")
        }
        return w.toString()
    }

    private fun henosisReact(): String =
        """
        |${GeneratedHeader.slash(version)}
        |package $androidPackage
        |
        |import android.util.Base64
        |import com.facebook.react.bridge.Arguments
        |import com.facebook.react.bridge.Promise
        |import com.facebook.react.bridge.ReadableArray
        |import com.facebook.react.bridge.ReadableMap
        |import com.facebook.react.bridge.WritableArray
        |import com.facebook.react.bridge.WritableMap
        |import kotlinx.coroutines.CancellationException
        |import kotlinx.coroutines.CoroutineScope
        |import kotlinx.coroutines.launch
        |
        |/** Moves values between the shared bridge and the React Native bridge, and runs calls as coroutines. */
        |internal object HenosisReact {
        |    /** Runs [block] on [scope] and settles [promise] with its channel-encoded result or the native error's class name. */
        |    fun call(scope: CoroutineScope, promise: Promise, block: suspend () -> Any?) {
        |        scope.launch {
        |            try {
        |                promise.resolve(toJs(block()))
        |            } catch (e: CancellationException) {
        |                throw e
        |            } catch (e: Throwable) {
        |                promise.reject(e.javaClass.simpleName, e.message, e)
        |            }
        |        }
        |    }
        |
        |    /** Converts a React argument into the plain Kotlin value the bridge decodes. */
        |    fun fromJs(value: Any?): Any? =
        |        when (value) {
        |            is ReadableMap -> value.toHashMap()
        |            is ReadableArray -> value.toArrayList()
        |            else -> value
        |        }
        |
        |    /** Converts a bridge value into something a Promise or event can carry. */
        |    fun toJs(value: Any?): Any? =
        |        when (value) {
        |            null, Unit -> null
        |            is Boolean, is String, is Int -> value
        |            is Number -> value.toDouble()
        |            is ByteArray -> Base64.encodeToString(value, Base64.NO_WRAP)
        |            is Map<*, *> -> Arguments.createMap().also { map -> value.forEach { (k, v) -> map.put(k as String, toJs(v)) } }
        |            is List<*> -> Arguments.createArray().also { array -> value.forEach { array.push(toJs(it)) } }
        |            else -> value.toString()
        |        }
        |
        |    private fun WritableMap.put(key: String, value: Any?) {
        |        when (value) {
        |            null -> putNull(key)
        |            is Boolean -> putBoolean(key, value)
        |            is Int -> putInt(key, value)
        |            is Double -> putDouble(key, value)
        |            is String -> putString(key, value)
        |            is WritableMap -> putMap(key, value)
        |            is WritableArray -> putArray(key, value)
        |            else -> putString(key, value.toString())
        |        }
        |    }
        |
        |    private fun WritableArray.push(value: Any?) {
        |        when (value) {
        |            null -> pushNull()
        |            is Boolean -> pushBoolean(value)
        |            is Int -> pushInt(value)
        |            is Double -> pushDouble(value)
        |            is String -> pushString(value)
        |            is WritableMap -> pushMap(value)
        |            is WritableArray -> pushArray(value)
        |            else -> pushString(value.toString())
        |        }
        |    }
        |}
        |
        """.trimMargin()

    private fun reactPackage(): String {
        val w = CodeWriter("    ")
        w.line(GeneratedHeader.slash(version))
        w.line("package $androidPackage")
        w.line()
        w.lines(
            "import com.facebook.react.BaseReactPackage",
            "import com.facebook.react.bridge.NativeModule",
            "import com.facebook.react.bridge.ReactApplicationContext",
            "import com.facebook.react.module.model.ReactModuleInfo",
            "import com.facebook.react.module.model.ReactModuleInfoProvider",
        )
        w.line()
        w.line("/** Registers one TurboModule per facade. Autolinking picks this class up from `react-native.config.js`. */")
        w.block("class $packageClass : BaseReactPackage() {") {
            line("override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =")
            indent {
                block("when (name) {") {
                    ir.facades.forEach { facade -> line("${moduleClass(facade)}.NAME -> ${moduleClass(facade)}(reactContext)") }
                    line("else -> null")
                }
            }
            line()
            line("override fun getReactModuleInfoProvider() =")
            indent {
                block("ReactModuleInfoProvider {") {
                    line("mapOf(")
                    indent {
                        ir.facades.forEach { facade ->
                            val m = moduleClass(facade)
                            line("$m.NAME to")
                            indent {
                                line("ReactModuleInfo(")
                                indent {
                                    line("name = $m.NAME,")
                                    line("className = $m.NAME,")
                                    line("canOverrideExistingModule = false,")
                                    line("needsEagerInit = false,")
                                    line("isCxxModule = false,")
                                    line("isTurboModule = true,")
                                }
                                line("),")
                            }
                        }
                    }
                    line(")")
                }
            }
        }
        return w.toString()
    }

    private fun module(facade: IrFacade): String {
        val w = CodeWriter("    ")
        val cls = moduleClass(facade)
        val bridge = BridgeGenerator.bridgeClassName(facade)
        w.line(GeneratedHeader.slash(version))
        w.line("package $androidPackage")
        w.line()
        val imports =
            mutableListOf(
                "$androidPackage.bridge.$bridge",
                "com.facebook.react.bridge.Promise",
                "com.facebook.react.bridge.ReactApplicationContext",
            )
        val paramKinds = facade.methods.flatMap { m -> m.parameters.map { it.type.kind } }.toSet()
        if (IrTypeKind.LIST in paramKinds) imports += "com.facebook.react.bridge.ReadableArray"
        if (IrTypeKind.MAP in paramKinds || IrTypeKind.DATA_CLASS in paramKinds) imports += "com.facebook.react.bridge.ReadableMap"
        imports +=
            listOf(
                "kotlinx.coroutines.CoroutineScope",
                "kotlinx.coroutines.Dispatchers",
                "kotlinx.coroutines.SupervisorJob",
                "kotlinx.coroutines.cancel",
            )
        if (facade.events.isNotEmpty()) {
            imports +=
                listOf("kotlinx.coroutines.CancellationException", "kotlinx.coroutines.Job", "kotlinx.coroutines.launch")
        }
        imports.sorted().forEach { w.line("import $it") }
        w.line()
        w.line("/** TurboModule for `${facade.nativeClass}`; all work goes through the shared [$bridge]. */")
        w.block("class $cls(reactContext: ReactApplicationContext) : ${specClass(facade)}(reactContext) {") {
            line("private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)")
            val ctor = if (facade.constructor == IrConstructor.CONTEXT) "$bridge(reactContext.applicationContext)" else "$bridge()"
            line("private val bridge by lazy { $ctor }")
            if (facade.events.isNotEmpty()) {
                line("private var listeners = 0")
                line("private val eventJobs = mutableListOf<Job>()")
            }
            facade.methods.forEach { method ->
                line()
                val params = method.parameters.joinToString(", ") { "${it.name}: ${kotlinSpecType(it.type)}" }
                val sep = if (method.parameters.isEmpty()) "" else ", "
                line("override fun ${method.name}(${params}${sep}promise: Promise) =")
                indent {
                    val args =
                        if (method.parameters.isEmpty()) {
                            "emptyMap()"
                        } else {
                            "mapOf(" + method.parameters.joinToString(", ") { p -> "\"${p.name}\" to ${argument(p.type, p.name)}" } + ")"
                        }
                    line("HenosisReact.call(scope, promise) { bridge.invoke(\"${method.name}\", $args) }")
                }
            }
            if (facade.events.isNotEmpty()) {
                line()
                block("override fun addListener(eventName: String) {") {
                    line("listeners++")
                    line("if (listeners == 1) startEvents()")
                }
                line()
                block("override fun removeListeners(count: Double) {") {
                    line("listeners = maxOf(0, listeners - count.toInt())")
                    line("if (listeners == 0) stopEvents()")
                }
                line()
                block("private fun startEvents() {") {
                    facade.events.forEach { event ->
                        val name = ts.eventName(facade, event.name)
                        line("eventJobs += scope.launch {")
                        indent {
                            line("try {")
                            indent {
                                line(
                                    "bridge.event(\"${event.name}\").collect { reactApplicationContext.emitDeviceEvent(\"$name\", HenosisReact.toJs(it)) }",
                                )
                            }
                            line("} catch (e: CancellationException) {")
                            indent { line("throw e") }
                            line("} catch (e: Throwable) {")
                            indent {
                                line("reactApplicationContext.emitDeviceEvent(")
                                indent {
                                    line("\"$name.error\",")
                                    line("HenosisReact.toJs(mapOf(\"code\" to e.javaClass.simpleName, \"message\" to e.message)),")
                                }
                                line(")")
                            }
                            line("}")
                        }
                        line("}")
                    }
                }
                line()
                block("private fun stopEvents() {") {
                    line("eventJobs.forEach { it.cancel() }")
                    line("eventJobs.clear()")
                }
            }
            line()
            block("override fun invalidate() {") {
                if (facade.events.isNotEmpty()) line("stopEvents()")
                line("scope.cancel()")
                line("super.invalidate()")
            }
            line()
            block("companion object {") { line("const val NAME = ${specClass(facade)}.NAME") }
        }
        return w.toString()
    }

    /** The Kotlin parameter type Codegen generates for a spec parameter. */
    private fun kotlinSpecType(type: IrTypeRef): String {
        val base =
            when (type.kind) {
                IrTypeKind.INT, IrTypeKind.LONG, IrTypeKind.SHORT, IrTypeKind.BYTE, IrTypeKind.DOUBLE, IrTypeKind.FLOAT -> "Double"
                IrTypeKind.BOOLEAN -> "Boolean"
                IrTypeKind.STRING, IrTypeKind.BYTE_ARRAY, IrTypeKind.ENUM -> "String"
                IrTypeKind.LIST -> "ReadableArray"
                IrTypeKind.MAP, IrTypeKind.DATA_CLASS -> "ReadableMap"
                IrTypeKind.UNIT -> "Unit"
            }
        return if (type.nullable) "$base?" else base
    }

    private fun argument(
        type: IrTypeRef,
        name: String,
    ): String =
        when (type.kind) {
            IrTypeKind.LIST, IrTypeKind.MAP, IrTypeKind.DATA_CLASS -> "HenosisReact.fromJs($name)"
            else -> name
        }
}
