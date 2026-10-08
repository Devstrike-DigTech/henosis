package com.devstrike.henosis.gen.flutter

import com.devstrike.henosis.core.codegen.CodeWriter
import com.devstrike.henosis.core.codegen.GeneratedFile
import com.devstrike.henosis.core.codegen.GeneratedHeader
import com.devstrike.henosis.core.ir.Ir
import com.devstrike.henosis.core.ir.IrTypeKind

/**
 * Generates `example/`: a Flutter app that calls every facade method with sample arguments,
 * listens to every event, and shows the results in a list. Its Android host project mirrors
 * `flutter create`'s template (without launcher icons, so no binaries are generated).
 */
internal class FlutterExample(
    private val ir: Ir,
    private val packageName: String,
    private val androidPackage: String,
) {
    private val types = DartTypes(ir)
    private val version = ir.henosisVersion
    private val appPackage = "${androidPackage}_example"
    private val appPackagePath = appPackage.replace('.', '/')

    fun files(): List<GeneratedFile> =
        listOf(
            GeneratedFile("example/pubspec.yaml", pubspec()),
            GeneratedFile(
                "example/analysis_options.yaml",
                "${GeneratedHeader.hash(
                    version,
                )}\nanalyzer:\n  exclude:\n    - build/**\n    - android/**\ninclude: package:flutter_lints/flutter.yaml\n",
            ),
            GeneratedFile("example/.gitignore", gitignore()),
            GeneratedFile("example/lib/main.dart", mainDart()),
            GeneratedFile("example/android/settings.gradle.kts", settingsGradle()),
            GeneratedFile("example/android/build.gradle.kts", rootBuildGradle()),
            GeneratedFile("example/android/gradle.properties", gradleProperties()),
            GeneratedFile("example/android/gradle/wrapper/gradle-wrapper.properties", wrapperProperties()),
            GeneratedFile("example/android/.gitignore", androidGitignore()),
            GeneratedFile("example/android/app/build.gradle.kts", appBuildGradle()),
            GeneratedFile("example/android/app/src/main/AndroidManifest.xml", mainManifest()),
            GeneratedFile("example/android/app/src/debug/AndroidManifest.xml", debugManifest()),
            GeneratedFile("example/android/app/src/profile/AndroidManifest.xml", debugManifest()),
            GeneratedFile("example/android/app/src/main/kotlin/$appPackagePath/MainActivity.kt", mainActivity()),
            GeneratedFile("example/android/app/src/main/res/values/styles.xml", styles(night = false)),
            GeneratedFile("example/android/app/src/main/res/values-night/styles.xml", styles(night = true)),
            GeneratedFile("example/android/app/src/main/res/drawable/launch_background.xml", launchBackground("@android:color/white")),
            GeneratedFile(
                "example/android/app/src/main/res/drawable-v21/launch_background.xml",
                launchBackground("?android:colorBackground"),
            ),
        )

    private fun pubspec(): String =
        """
        |${GeneratedHeader.hash(version)}
        |name: ${packageName}_example
        |description: "Exercises every binding of the $packageName plugin."
        |publish_to: 'none'
        |
        |environment:
        |  sdk: ${FlutterToolchain.DART_SDK_CONSTRAINT}
        |
        |dependencies:
        |  flutter:
        |    sdk: flutter
        |  $packageName:
        |    path: ../
        |
        |dev_dependencies:
        |  flutter_test:
        |    sdk: flutter
        |  flutter_lints: ${FlutterToolchain.FLUTTER_LINTS}
        |
        |flutter:
        |  uses-material-design: true
        |
        """.trimMargin()

    private fun gitignore(): String =
        """
        |${GeneratedHeader.hash(version)}
        |*.class
        |*.log
        |*.pyc
        |*.swp
        |.DS_Store
        |.atom/
        |.build/
        |.buildlog/
        |.history
        |.svn/
        |.swiftpm/
        |migrate_working_dir/
        |*.iml
        |*.ipr
        |*.iws
        |.idea/
        |**/doc/api/
        |**/ios/Flutter/.last_build_id
        |.dart_tool/
        |.flutter-plugins-dependencies
        |.pub-cache/
        |.pub/
        |/build/
        |/coverage/
        |app.*.symbols
        |app.*.map.json
        |/android/app/debug
        |/android/app/profile
        |/android/app/release
        |
        """.trimMargin()

    private fun mainDart(): String {
        val w = CodeWriter()
        w.line(GeneratedHeader.slash(version))
        w.line("import 'dart:async';")
        val allTypes = ir.facades.flatMap { f -> f.methods.flatMap { m -> m.parameters.map { it.type } } }
        if (allTypes.any { types.usesTypedData(it) }) w.line("import 'dart:typed_data';")
        w.line()
        w.line("import 'package:flutter/material.dart';")
        w.line("import 'package:$packageName/$packageName.dart';")
        w.line()
        w.line("void main() => runApp(const ExampleApp());")
        w.line()
        w.line("/// Calls every generated binding once and shows what came back.")
        w.block("class ExampleApp extends StatefulWidget {") {
            line("const ExampleApp({super.key});")
            line()
            line("@override")
            line("State<ExampleApp> createState() => _ExampleAppState();")
        }
        w.line()
        w.block("class _ExampleAppState extends State<ExampleApp> {") {
            line("final List<String> _log = <String>[];")
            line("final List<StreamSubscription<Object?>> _subscriptions = <StreamSubscription<Object?>>[];")
            line()
            line("@override")
            block("void initState() {") {
                line("super.initState();")
                line("unawaited(_run());")
            }
            line()
            line("@override")
            block("void dispose() {") {
                block("for (final StreamSubscription<Object?> subscription in _subscriptions) {") {
                    line("unawaited(subscription.cancel());")
                }
                line("super.dispose();")
            }
            line()
            block("void _record(String line) {") {
                block("if (mounted) {") {
                    line("setState(() => _log.add(line));")
                }
            }
            line()
            block("Future<void> _run() async {") {
                ir.facades.forEach { facade ->
                    facade.methods.forEach { method ->
                        val label = "${facade.name}.${method.name}"
                        val args = method.parameters.joinToString(", ") { types.sample(it.type) }
                        line("try {")
                        indent {
                            if (method.returnType.kind == IrTypeKind.UNIT) {
                                line("await ${facade.name}.instance.${method.name}($args);")
                                line("_record('$label: done');")
                            } else {
                                line("final ${types.name(method.returnType)} result =")
                                indent { line("await ${facade.name}.instance.${method.name}($args);") }
                                line("_record('$label: \$result');")
                            }
                        }
                        line("} on HenosisException catch (e) {")
                        indent { line("_record('$label failed: \$e');") }
                        line("}")
                    }
                    facade.events.forEach { event ->
                        val label = "${facade.name}.${event.name}"
                        line("_subscriptions.add(")
                        indent {
                            line("${facade.name}.instance.${event.name}.listen(")
                            indent {
                                line("(${types.name(event.elementType)} event) => _record('$label: \$event'),")
                                line("onError: (Object error) => _record('$label failed: \$error'),")
                            }
                            line("),")
                        }
                        line(");")
                    }
                }
            }
            line()
            line("@override")
            block("Widget build(BuildContext context) {") {
                line("return MaterialApp(")
                indent {
                    line("home: Scaffold(")
                    indent {
                        line("appBar: AppBar(title: const Text('$packageName example')),")
                        line("body: ListView.builder(")
                        indent {
                            line("itemCount: _log.length,")
                            line("itemBuilder: (BuildContext context, int index) =>")
                            indent { line("ListTile(title: Text(_log[index])),") }
                        }
                        line("),")
                    }
                    line("),")
                }
                line(");")
            }
        }
        return w.toString()
    }

    private fun settingsGradle(): String =
        """
        |${GeneratedHeader.slash(version)}
        |pluginManagement {
        |    val flutterSdkPath =
        |        run {
        |            val properties = java.util.Properties()
        |            file("local.properties").inputStream().use { properties.load(it) }
        |            val flutterSdkPath = properties.getProperty("flutter.sdk")
        |            require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
        |            flutterSdkPath
        |        }
        |
        |    includeBuild("${'$'}flutterSdkPath/packages/flutter_tools/gradle")
        |
        |    repositories {
        |        google()
        |        mavenCentral()
        |        gradlePluginPortal()
        |    }
        |}
        |
        |plugins {
        |    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
        |    id("com.android.application") version "${FlutterToolchain.AGP_VERSION}" apply false
        |    id("org.jetbrains.kotlin.android") version "${FlutterToolchain.KOTLIN_VERSION}" apply false
        |}
        |
        |include(":app")
        |
        """.trimMargin()

    private fun rootBuildGradle(): String =
        """
        |${GeneratedHeader.slash(version)}
        |allprojects {
        |    repositories {
        |        google()
        |        mavenCentral()
        |    }
        |}
        |
        |val newBuildDir: Directory =
        |    rootProject.layout.buildDirectory
        |        .dir("../../build")
        |        .get()
        |rootProject.layout.buildDirectory.value(newBuildDir)
        |
        |subprojects {
        |    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
        |    project.layout.buildDirectory.value(newSubprojectBuildDir)
        |}
        |subprojects {
        |    project.evaluationDependsOn(":app")
        |}
        |
        |tasks.register<Delete>("clean") {
        |    delete(rootProject.layout.buildDirectory)
        |}
        |
        """.trimMargin()

    private fun gradleProperties(): String =
        """
        |${GeneratedHeader.hash(version)}
        |org.gradle.jvmargs=-Xmx8G -XX:MaxMetaspaceSize=4G -XX:ReservedCodeCacheSize=512m -XX:+HeapDumpOnOutOfMemoryError
        |android.useAndroidX=true
        |android.newDsl=false
        |android.builtInKotlin=false
        |
        """.trimMargin()

    private fun wrapperProperties(): String =
        """
        |${GeneratedHeader.hash(version)}
        |distributionBase=GRADLE_USER_HOME
        |distributionPath=wrapper/dists
        |zipStoreBase=GRADLE_USER_HOME
        |zipStorePath=wrapper/dists
        |distributionUrl=https\://services.gradle.org/distributions/gradle-${FlutterToolchain.GRADLE_VERSION}-all.zip
        |
        """.trimMargin()

    private fun androidGitignore(): String =
        """
        |${GeneratedHeader.hash(version)}
        |gradle-wrapper.jar
        |/.gradle
        |/captures/
        |/gradlew
        |/gradlew.bat
        |/local.properties
        |GeneratedPluginRegistrant.java
        |.cxx/
        |key.properties
        |**/*.keystore
        |**/*.jks
        |
        """.trimMargin()

    private fun appBuildGradle(): String =
        """
        |${GeneratedHeader.slash(version)}
        |plugins {
        |    id("com.android.application")
        |    // The Flutter Gradle Plugin must be applied after the Android Gradle plugin.
        |    id("dev.flutter.flutter-gradle-plugin")
        |}
        |
        |android {
        |    namespace = "$appPackage"
        |    compileSdk = flutter.compileSdkVersion
        |    ndkVersion = flutter.ndkVersion
        |
        |    compileOptions {
        |        sourceCompatibility = JavaVersion.VERSION_17
        |        targetCompatibility = JavaVersion.VERSION_17
        |    }
        |
        |    defaultConfig {
        |        applicationId = "$appPackage"
        |        minSdk = flutter.minSdkVersion
        |        targetSdk = flutter.targetSdkVersion
        |        versionCode = flutter.versionCode
        |        versionName = flutter.versionName
        |    }
        |
        |    buildTypes {
        |        release {
        |            // Signed with the debug keys so `flutter run --release` works out of the box.
        |            signingConfig = signingConfigs.getByName("debug")
        |        }
        |    }
        |}
        |
        |kotlin {
        |    compilerOptions {
        |        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        |    }
        |}
        |
        |flutter {
        |    source = "../.."
        |}
        |
        """.trimMargin()

    private fun mainManifest(): String =
        """
        |${GeneratedHeader.xml(version)}
        |<manifest xmlns:android="http://schemas.android.com/apk/res/android">
        |    <application
        |        android:label="${packageName}_example"
        |        android:name="${'$'}{applicationName}">
        |        <activity
        |            android:name=".MainActivity"
        |            android:exported="true"
        |            android:launchMode="singleTop"
        |            android:taskAffinity=""
        |            android:theme="@style/LaunchTheme"
        |            android:configChanges="orientation|keyboardHidden|keyboard|screenSize|smallestScreenSize|locale|layoutDirection|fontScale|screenLayout|density|uiMode"
        |            android:hardwareAccelerated="true"
        |            android:windowSoftInputMode="adjustResize">
        |            <meta-data
        |              android:name="io.flutter.embedding.android.NormalTheme"
        |              android:resource="@style/NormalTheme"
        |              />
        |            <intent-filter>
        |                <action android:name="android.intent.action.MAIN"/>
        |                <category android:name="android.intent.category.LAUNCHER"/>
        |            </intent-filter>
        |        </activity>
        |        <meta-data
        |            android:name="flutterEmbedding"
        |            android:value="2" />
        |    </application>
        |    <queries>
        |        <intent>
        |            <action android:name="android.intent.action.PROCESS_TEXT"/>
        |            <data android:mimeType="text/plain"/>
        |        </intent>
        |    </queries>
        |</manifest>
        |
        """.trimMargin()

    private fun debugManifest(): String =
        """
        |${GeneratedHeader.xml(version)}
        |<manifest xmlns:android="http://schemas.android.com/apk/res/android">
        |    <!-- The INTERNET permission lets the Flutter tool connect for hot reload and debugging. -->
        |    <uses-permission android:name="android.permission.INTERNET"/>
        |</manifest>
        |
        """.trimMargin()

    private fun mainActivity(): String =
        """
        |${GeneratedHeader.slash(version)}
        |package $appPackage
        |
        |import io.flutter.embedding.android.FlutterActivity
        |
        |class MainActivity : FlutterActivity()
        |
        """.trimMargin()

    private fun styles(night: Boolean): String {
        val parent = if (night) "@android:style/Theme.Black.NoTitleBar" else "@android:style/Theme.Light.NoTitleBar"
        return """
            |${GeneratedHeader.xml(version)}
            |<resources>
            |    <style name="LaunchTheme" parent="$parent">
            |        <item name="android:windowBackground">@drawable/launch_background</item>
            |    </style>
            |    <style name="NormalTheme" parent="$parent">
            |        <item name="android:windowBackground">?android:colorBackground</item>
            |    </style>
            |</resources>
            |
            """.trimMargin()
    }

    private fun launchBackground(color: String): String =
        """
        |${GeneratedHeader.xml(version)}
        |<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
        |    <item android:drawable="$color" />
        |</layer-list>
        |
        """.trimMargin()
}
