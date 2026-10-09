package com.devstrike.henosis.gen.flutter

import com.devstrike.henosis.core.codegen.CodeWriter
import com.devstrike.henosis.core.codegen.CopiedFile
import com.devstrike.henosis.core.codegen.GeneratedFile
import com.devstrike.henosis.core.codegen.GeneratedHeader
import com.devstrike.henosis.core.codegen.GeneratorContext
import com.devstrike.henosis.core.ir.Ir
import com.devstrike.henosis.core.ir.IrConstructor
import com.devstrike.henosis.gen.bridge.BridgeGenerator

/** Generates the plugin's `android/` directory: Gradle files, the FlutterPlugin glue and the shared Kotlin bridge. */
internal class FlutterAndroid(
    private val ir: Ir,
    private val packageName: String,
    private val androidPackage: String,
    private val context: GeneratorContext,
) {
    private val pluginClass = "${DartNames.pascalCase(packageName)}Plugin"
    private val packagePath = androidPackage.replace('.', '/')
    private val version = ir.henosisVersion

    /** Fully qualified plugin class, for `pubspec.yaml`. */
    val pluginClassName: String get() = pluginClass

    fun files(): List<GeneratedFile> {
        val files = mutableListOf<GeneratedFile>()
        files += GeneratedFile("android/build.gradle.kts", buildGradle())
        files += GeneratedFile("android/settings.gradle.kts", "${GeneratedHeader.slash(version)}\nrootProject.name = \"$packageName\"\n")
        files +=
            GeneratedFile(
                "android/.gitignore",
                "${GeneratedHeader.hash(
                    version,
                )}\n*.iml\n.gradle\n/local.properties\n/.idea/workspace.xml\n/.idea/libraries\n.DS_Store\n/build\n/captures\n.cxx\n",
            )
        files +=
            GeneratedFile(
                "android/src/main/AndroidManifest.xml",
                "${GeneratedHeader.xml(version)}\n<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" />\n",
            )
        files += GeneratedFile("android/src/main/kotlin/$packagePath/HenosisChannels.kt", channels())
        files += GeneratedFile("android/src/main/kotlin/$packagePath/$pluginClass.kt", plugin())
        BridgeGenerator.generate(ir, "$androidPackage.bridge").forEach { bridge ->
            files += GeneratedFile("android/src/main/kotlin/$packagePath/bridge/${bridge.path}", bridge.content)
        }
        ir.source.aarFileName?.let { files += GeneratedFile(localMavenPath("pom"), pom()) }
        return files
    }

    /** The SDK `.aar` copied into the package-local Maven repository, when the source is a local archive. */
    fun copies(): List<CopiedFile> {
        val aar = context.aarFile ?: return emptyList()
        if (ir.source.aarFileName == null) return emptyList()
        return listOf(CopiedFile(localMavenPath("aar"), aar))
    }

    private val coordinates = ir.source.coordinates.split(':')

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
        val w = CodeWriter("    ")
        w.line(GeneratedHeader.slash(version))
        w.line("group = \"$androidPackage\"")
        w.line("version = \"${ir.version}\"")
        w.line()
        w.block("buildscript {") {
            line("val kotlinVersion = \"${FlutterToolchain.KOTLIN_VERSION}\"")
            block("repositories {") {
                line("google()")
                line("mavenCentral()")
            }
            line()
            block("dependencies {") {
                line("classpath(\"com.android.tools.build:gradle:${FlutterToolchain.AGP_VERSION}\")")
                line("classpath(\"org.jetbrains.kotlin:kotlin-gradle-plugin:\$kotlinVersion\")")
            }
        }
        w.line()
        if (ir.source.aarFileName != null) {
            w.line("// The native SDK shipped with this plugin, as a one-artifact Maven repository.")
            w.line("val sdkRepository = uri(\"\${projectDir}/libs/maven\")")
            w.line()
        }
        w.block("allprojects {") {
            block("repositories {") {
                line("google()")
                line("mavenCentral()")
            }
        }
        if (ir.source.aarFileName != null || ir.source.repositories.isNotEmpty()) {
            w.line()
            w.line("// Consumers resolve the SDK from the same repositories, so every project of the build must see them")
            w.line("// (a subproject's own `allprojects` covers only itself).")
            w.block("rootProject.allprojects {") {
                block("repositories {") {
                    if (ir.source.aarFileName != null) line("maven { url = sdkRepository }")
                    ir.source.repositories.forEach { line("maven { url = uri(\"$it\") }") }
                }
            }
        }
        w.line()
        w.block("plugins {") { line("id(\"com.android.library\")") }
        w.line()
        w.block("android {") {
            line("namespace = \"$androidPackage\"")
            line()
            line("compileSdk = ${FlutterToolchain.COMPILE_SDK}")
            line()
            block("compileOptions {") {
                line("sourceCompatibility = JavaVersion.VERSION_17")
                line("targetCompatibility = JavaVersion.VERSION_17")
            }
            line()
            block("sourceSets {") {
                block("getByName(\"main\") {") { line("java.srcDirs(\"src/main/kotlin\")") }
            }
            line()
            block("defaultConfig {") { line("minSdk = ${FlutterToolchain.MIN_SDK}") }
        }
        w.line()
        w.block("kotlin {") {
            block("compilerOptions {") { line("jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17") }
        }
        w.line()
        w.block("dependencies {") {
            line("implementation(\"${ir.source.coordinates}\")")
            line("implementation(\"org.jetbrains.kotlinx:kotlinx-coroutines-android:${FlutterToolchain.COROUTINES_VERSION}\")")
        }
        return w.toString()
    }

    private fun channels(): String =
        """
        |${GeneratedHeader.slash(version)}
        |package $androidPackage
        |
        |import io.flutter.plugin.common.EventChannel
        |import io.flutter.plugin.common.MethodCall
        |import io.flutter.plugin.common.MethodChannel
        |import kotlinx.coroutines.CancellationException
        |import kotlinx.coroutines.CoroutineScope
        |import kotlinx.coroutines.Job
        |import kotlinx.coroutines.flow.Flow
        |import kotlinx.coroutines.launch
        |
        |/** Runs each method call as a coroutine on [scope] and maps results and failures onto the channel. */
        |internal class HenosisMethodHandler(
        |    private val scope: CoroutineScope,
        |    private val invoke: suspend (String, Map<String, Any?>) -> Any?,
        |) : MethodChannel.MethodCallHandler {
        |    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        |        val args = (call.arguments as? Map<*, *>)?.entries?.associate { (it.key as String) to it.value } ?: emptyMap()
        |        scope.launch {
        |            try {
        |                result.success(invoke(call.method, args))
        |            } catch (e: NoSuchMethodException) {
        |                result.notImplemented()
        |            } catch (e: CancellationException) {
        |                throw e
        |            } catch (e: Throwable) {
        |                result.error(e.javaClass.simpleName, e.message, null)
        |            }
        |        }
        |    }
        |}
        |
        |/** Collects a [Flow] while Dart listens and forwards every value, error and completion. */
        |internal class HenosisStreamHandler(
        |    private val scope: CoroutineScope,
        |    private val flow: () -> Flow<Any?>,
        |) : EventChannel.StreamHandler {
        |    private var job: Job? = null
        |
        |    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        |        job = scope.launch {
        |            try {
        |                flow().collect { events.success(it) }
        |                events.endOfStream()
        |            } catch (e: CancellationException) {
        |                throw e
        |            } catch (e: Throwable) {
        |                events.error(e.javaClass.simpleName, e.message, null)
        |            }
        |        }
        |    }
        |
        |    override fun onCancel(arguments: Any?) {
        |        job?.cancel()
        |        job = null
        |    }
        |}
        |
        """.trimMargin()

    private fun plugin(): String {
        val w = CodeWriter("    ")
        w.line(GeneratedHeader.slash(version))
        w.line("package $androidPackage")
        w.line()
        ir.facades.forEach { w.line("import $androidPackage.bridge.${BridgeGenerator.bridgeClassName(it)}") }
        w.lines(
            "import io.flutter.embedding.engine.plugins.FlutterPlugin",
            "import io.flutter.plugin.common.EventChannel",
            "import io.flutter.plugin.common.MethodChannel",
            "import kotlinx.coroutines.CoroutineScope",
            "import kotlinx.coroutines.Dispatchers",
            "import kotlinx.coroutines.SupervisorJob",
            "import kotlinx.coroutines.cancel",
        )
        w.line()
        w.line("/** Registers one method channel per facade and one event channel per event. */")
        w.block("class $pluginClass : FlutterPlugin {") {
            line("private var scope: CoroutineScope? = null")
            line("private val methodChannels = mutableListOf<MethodChannel>()")
            line("private val eventChannels = mutableListOf<EventChannel>()")
            line()
            block("override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {") {
                line("val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)")
                line("this.scope = scope")
                line("val messenger = binding.binaryMessenger")
                if (ir.facades.any { it.constructor == IrConstructor.CONTEXT }) line("val context = binding.applicationContext")
                ir.facades.forEach { facade ->
                    val bridge = BridgeGenerator.bridgeClassName(facade)
                    val local = facade.name.replaceFirstChar { it.lowercase() }
                    val ctor = if (facade.constructor == IrConstructor.CONTEXT) "$bridge(context)" else "$bridge()"
                    line()
                    line("val $local = lazy { $ctor }")
                    line("methodChannels += MethodChannel(messenger, \"henosis/${ir.name}/${facade.name}\").apply {")
                    indent {
                        line(
                            "setMethodCallHandler(HenosisMethodHandler(scope) { method, args -> $local.value.invoke(method, args) })",
                        )
                    }
                    line("}")
                    facade.events.forEach { event ->
                        line("eventChannels += EventChannel(messenger, \"henosis/${ir.name}/${facade.name}/${event.name}\").apply {")
                        indent { line("setStreamHandler(HenosisStreamHandler(scope) { $local.value.event(\"${event.name}\") })") }
                        line("}")
                    }
                }
            }
            line()
            block("override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {") {
                line("methodChannels.forEach { it.setMethodCallHandler(null) }")
                line("eventChannels.forEach { it.setStreamHandler(null) }")
                line("methodChannels.clear()")
                line("eventChannels.clear()")
                line("scope?.cancel()")
                line("scope = null")
            }
        }
        return w.toString()
    }
}
