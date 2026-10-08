package com.devstrike.henosis.gen.reactnative

import com.devstrike.henosis.core.codegen.BinaryFile
import com.devstrike.henosis.core.codegen.CodeWriter
import com.devstrike.henosis.core.codegen.GeneratedFile
import com.devstrike.henosis.core.codegen.GeneratedHeader
import com.devstrike.henosis.core.ir.Ir
import com.devstrike.henosis.core.ir.IrTypeKind

/**
 * Generates `example/`: a React Native app (New Architecture, Android) that calls every binding
 * with sample arguments, listens to every event and lists the results. The Android project mirrors
 * the React Native 0.87 app template without launcher icons or a checked-in keystore, so the only
 * binary emitted is the Gradle wrapper jar.
 */
internal class ReactNativeExample(
    private val ir: Ir,
    private val npmName: String,
    private val moduleName: String,
    private val androidPackage: String,
) {
    private val types = TsTypes(ir)
    private val version = ir.henosisVersion
    private val appPackage = "$androidPackage.example"
    private val appPackagePath = appPackage.replace('.', '/')
    private val appName = "${moduleName}Example"

    fun files(): List<GeneratedFile> =
        listOf(
            GeneratedFile("example/package.json", packageJson()),
            GeneratedFile(
                "example/app.json",
                "${GeneratedHeader.slash(
                    version,
                )}\n{\n  \"name\": \"$appName\",\n  \"displayName\": \"$appName\"\n}\n".let {
                    jsonWithComment(it)
                },
            ),
            GeneratedFile("example/index.js", indexJs()),
            GeneratedFile("example/babel.config.js", babelConfig()),
            GeneratedFile("example/metro.config.js", metroConfig()),
            GeneratedFile("example/react-native.config.js", reactNativeConfig()),
            GeneratedFile("example/.watchmanconfig", "{}\n"),
            GeneratedFile("example/src/App.tsx", appTsx()),
            GeneratedFile("example/android/settings.gradle", settingsGradle()),
            GeneratedFile("example/android/build.gradle", rootBuildGradle()),
            GeneratedFile("example/android/gradle.properties", gradleProperties()),
            GeneratedFile("example/android/gradle/wrapper/gradle-wrapper.properties", wrapperProperties()),
            GeneratedFile("example/android/gradlew", resource("gradlew"), executable = true),
            GeneratedFile("example/android/gradlew.bat", resource("gradlew.bat")),
            GeneratedFile("example/android/app/build.gradle", appBuildGradle()),
            GeneratedFile(
                "example/android/app/proguard-rules.pro",
                "${GeneratedHeader.hash(version)}\n# Add project-specific ProGuard rules here.\n",
            ),
            GeneratedFile("example/android/app/src/main/AndroidManifest.xml", manifest()),
            GeneratedFile("example/android/app/src/main/java/$appPackagePath/MainActivity.kt", mainActivity()),
            GeneratedFile("example/android/app/src/main/java/$appPackagePath/MainApplication.kt", mainApplication()),
            GeneratedFile(
                "example/android/app/src/main/res/values/strings.xml",
                "${GeneratedHeader.xml(version)}\n<resources>\n    <string name=\"app_name\">$appName</string>\n</resources>\n",
            ),
            GeneratedFile("example/android/app/src/main/res/values/styles.xml", styles()),
            GeneratedFile("example/android/app/src/main/res/drawable/rn_edit_text_material.xml", editTextDrawable()),
        )

    /** The Gradle wrapper jar, embedded as a resource; the only binary in the package. */
    fun binaries(): List<BinaryFile> =
        listOf(BinaryFile("example/android/gradle/wrapper/gradle-wrapper.jar", resourceBytes("gradle-wrapper.jar")))

    private fun resource(name: String): String = String(resourceBytes(name), Charsets.UTF_8)

    private fun resourceBytes(name: String): ByteArray =
        ReactNativeExample::class.java.getResourceAsStream("/henosis/gradle-wrapper/$name")?.use { it.readBytes() }
            ?: error("missing embedded resource $name")

    /** JSON cannot carry comments, so `app.json` is the one file without a header; the header line is dropped. */
    private fun jsonWithComment(text: String): String = text.lines().drop(1).joinToString("\n")

    private fun packageJson(): String =
        """
        |{
        |  "name": "$npmName-example",
        |  "version": "0.0.1",
        |  "private": true,
        |  "henosis": "${GeneratedHeader.text(version)}",
        |  "scripts": {
        |    "android": "react-native run-android",
        |    "start": "react-native start",
        |    "build:android": "react-native build-android --extra-params \"--no-daemon --console=plain -PreactNativeArchitectures=arm64-v8a\""
        |  },
        |  "dependencies": {
        |    "react": "${ReactNativeToolchain.REACT}",
        |    "react-native": "${ReactNativeToolchain.REACT_NATIVE}"
        |  },
        |  "devDependencies": {
        |    "@babel/core": "${ReactNativeToolchain.BABEL_CORE}",
        |    "@babel/preset-env": "${ReactNativeToolchain.BABEL_PRESET_ENV}",
        |    "@babel/runtime": "${ReactNativeToolchain.BABEL_RUNTIME}",
        |    "@react-native-community/cli": "${ReactNativeToolchain.CLI}",
        |    "@react-native-community/cli-platform-android": "${ReactNativeToolchain.CLI}",
        |    "@react-native/babel-preset": "${ReactNativeToolchain.REACT_NATIVE}",
        |    "@react-native/metro-config": "${ReactNativeToolchain.REACT_NATIVE}",
        |    "@react-native/typescript-config": "${ReactNativeToolchain.REACT_NATIVE}",
        |    "@types/react": "${ReactNativeToolchain.TYPES_REACT}",
        |    "react-native-builder-bob": "${ReactNativeToolchain.BUILDER_BOB}",
        |    "react-native-monorepo-config": "${ReactNativeToolchain.MONOREPO_CONFIG}"
        |  },
        |  "engines": {
        |    "node": "${ReactNativeToolchain.NODE_ENGINE}"
        |  }
        |}
        |
        """.trimMargin()

    private fun indexJs(): String =
        """
        |${GeneratedHeader.slash(version)}
        |import { AppRegistry } from 'react-native';
        |import App from './src/App';
        |import { name as appName } from './app.json';
        |
        |AppRegistry.registerComponent(appName, () => App);
        |
        """.trimMargin()

    private fun babelConfig(): String =
        """
        |${GeneratedHeader.slash(version)}
        |const path = require('path');
        |const { getConfig } = require('react-native-builder-bob/babel-config');
        |const pkg = require('../package.json');
        |
        |const root = path.resolve(__dirname, '..');
        |
        |module.exports = getConfig(
        |  {
        |    presets: ['module:@react-native/babel-preset'],
        |  },
        |  { root, pkg }
        |);
        |
        """.trimMargin()

    private fun metroConfig(): String =
        """
        |${GeneratedHeader.slash(version)}
        |const path = require('path');
        |const { getDefaultConfig } = require('@react-native/metro-config');
        |const { withMetroConfig } = require('react-native-monorepo-config');
        |
        |const root = path.resolve(__dirname, '..');
        |
        |/** @type {import('metro-config').MetroConfig} */
        |const config = withMetroConfig(getDefaultConfig(__dirname), {
        |  root,
        |  dirname: __dirname,
        |  conditions: ['${TsNames.sourceCondition(npmName)}'],
        |});
        |
        |module.exports = config;
        |
        """.trimMargin()

    private fun reactNativeConfig(): String =
        """
        |${GeneratedHeader.slash(version)}
        |const path = require('path');
        |const pkg = require('../package.json');
        |
        |module.exports = {
        |  dependencies: {
        |    [pkg.name]: {
        |      root: path.join(__dirname, '..'),
        |      platforms: {
        |        // The codegen script needs the platform listed explicitly, even with no extra settings.
        |        android: {},
        |      },
        |    },
        |  },
        |};
        |
        """.trimMargin()

    private fun appTsx(): String {
        val w = CodeWriter()
        w.line(GeneratedHeader.slash(version))
        w.line("import { useEffect, useState } from 'react';")
        w.line("import { ScrollView, StyleSheet, Text, View } from 'react-native';")
        w.line("import { ${(ir.facades.map { it.name } + "HenosisError").sorted().joinToString(", ")} } from '$npmName';")
        w.line()
        w.block("function describe(value: unknown): string {") {
            line("if (value instanceof ArrayBuffer) {")
            indent { line("return `ArrayBuffer(\${value.byteLength} bytes)`;") }
            line("}")
            line("return typeof value === 'string' ? value : JSON.stringify(value);")
        }
        w.line()
        w.block("function failure(error: unknown): string {") {
            line("return error instanceof HenosisError ? `\${error.code}: \${error.message}` : String(error);")
        }
        w.line()
        w.line("/** Calls every generated binding once and shows what came back. */")
        w.block("export default function App() {") {
            line("const [log, setLog] = useState<string[]>([]);")
            line()
            line("useEffect(() => {")
            indent {
                line("const record = (line: string) => setLog((previous) => [...previous, line]);")
                line("const run = async () => {")
                indent {
                    ir.facades.forEach { facade ->
                        facade.methods.forEach { method ->
                            val label = "${facade.name}.${method.name}"
                            val args = method.parameters.joinToString(", ") { types.sample(it.type) }
                            line("try {")
                            indent {
                                if (method.returnType.kind == IrTypeKind.UNIT) {
                                    line("await ${facade.name}.instance.${method.name}($args);")
                                    line("record('$label: done');")
                                } else {
                                    line("const result = await ${facade.name}.instance.${method.name}($args);")
                                    line("record(`$label: \${describe(result)}`);")
                                }
                            }
                            line("} catch (error) {")
                            indent { line("record(`$label failed: \${failure(error)}`);") }
                            line("}")
                        }
                    }
                }
                line("};")
                line("const unsubscribes: Array<() => void> = [];")
                ir.facades.forEach { facade ->
                    facade.events.forEach { event ->
                        val label = "${facade.name}.${event.name}"
                        line("unsubscribes.push(")
                        indent {
                            line("${facade.name}.instance.${event.name}.addListener(")
                            indent {
                                line("(event) => record(`$label: \${describe(event)}`),")
                                line("(error) => record(`$label failed: \${failure(error)}`)")
                            }
                            line(")")
                        }
                        line(");")
                    }
                }
                line("void run();")
                line("return () => {")
                indent { line("unsubscribes.forEach((unsubscribe) => unsubscribe());") }
                line("};")
            }
            line("}, []);")
            line()
            line("return (")
            indent {
                line("<View style={styles.container}>")
                indent {
                    line("<Text style={styles.title}>$npmName example</Text>")
                    line("<ScrollView>")
                    indent {
                        line("{log.map((line, index) => (")
                        indent {
                            line("<Text key={index} style={styles.line}>")
                            indent { line("{line}") }
                            line("</Text>")
                        }
                        line("))}")
                    }
                    line("</ScrollView>")
                }
                line("</View>")
            }
            line(");")
        }
        w.line()
        w.block("const styles = StyleSheet.create({", footer = "});") {
            line("container: { flex: 1, paddingTop: 48, paddingHorizontal: 16 },")
            line("title: { fontSize: 18, fontWeight: 'bold', marginBottom: 12 },")
            line("line: { fontFamily: 'monospace', marginBottom: 4 },")
        }
        return w.toString()
    }

    private fun settingsGradle(): String =
        """
        |${GeneratedHeader.slash(version)}
        |pluginManagement { includeBuild("../node_modules/@react-native/gradle-plugin") }
        |plugins { id("com.facebook.react.settings") }
        |extensions.configure(com.facebook.react.ReactSettingsExtension) { ex -> ex.autolinkLibrariesFromCommand() }
        |rootProject.name = '$appName'
        |include ':app'
        |includeBuild('../node_modules/@react-native/gradle-plugin')
        |
        """.trimMargin()

    private fun rootBuildGradle(): String =
        """
        |${GeneratedHeader.slash(version)}
        |buildscript {
        |    ext {
        |        buildToolsVersion = "${ReactNativeToolchain.BUILD_TOOLS}"
        |        minSdkVersion = ${ReactNativeToolchain.MIN_SDK}
        |        compileSdkVersion = ${ReactNativeToolchain.COMPILE_SDK}
        |        targetSdkVersion = ${ReactNativeToolchain.TARGET_SDK}
        |        ndkVersion = "${ReactNativeToolchain.NDK}"
        |        kotlinVersion = "${ReactNativeToolchain.APP_KOTLIN}"
        |    }
        |    repositories {
        |        google()
        |        mavenCentral()
        |    }
        |    dependencies {
        |        classpath("com.android.tools.build:gradle")
        |        classpath("com.facebook.react:react-native-gradle-plugin")
        |        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin")
        |    }
        |}
        |
        |apply plugin: "com.facebook.react.rootproject"
        |
        """.trimMargin()

    private fun gradleProperties(): String =
        """
        |${GeneratedHeader.hash(version)}
        |org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
        |android.useAndroidX=true
        |reactNativeArchitectures=armeabi-v7a,arm64-v8a,x86,x86_64
        |newArchEnabled=true
        |hermesEnabled=true
        |edgeToEdgeEnabled=true
        |android.builtInKotlin=false
        |android.newDsl=false
        |
        """.trimMargin()

    private fun wrapperProperties(): String =
        """
        |${GeneratedHeader.hash(version)}
        |distributionBase=GRADLE_USER_HOME
        |distributionPath=wrapper/dists
        |distributionUrl=https\://services.gradle.org/distributions/gradle-${ReactNativeToolchain.GRADLE}-bin.zip
        |networkTimeout=10000
        |validateDistributionUrl=true
        |zipStoreBase=GRADLE_USER_HOME
        |zipStorePath=wrapper/dists
        |
        """.trimMargin()

    private fun appBuildGradle(): String =
        """
        |${GeneratedHeader.slash(version)}
        |apply plugin: "com.android.application"
        |apply plugin: "org.jetbrains.kotlin.android"
        |apply plugin: "com.facebook.react"
        |
        |react {
        |    autolinkLibrariesWithApp()
        |}
        |
        |def enableProguardInReleaseBuilds = false
        |def jscFlavor = 'io.github.react-native-community:jsc-android:2026004.+'
        |
        |android {
        |    ndkVersion rootProject.ext.ndkVersion
        |    buildToolsVersion rootProject.ext.buildToolsVersion
        |    compileSdk rootProject.ext.compileSdkVersion
        |
        |    namespace "$appPackage"
        |    defaultConfig {
        |        applicationId "$appPackage"
        |        minSdkVersion rootProject.ext.minSdkVersion
        |        targetSdkVersion rootProject.ext.targetSdkVersion
        |        versionCode 1
        |        versionName "1.0"
        |    }
        |    buildTypes {
        |        release {
        |            // Signed with the debug key so the example runs out of the box; never ship it.
        |            signingConfig signingConfigs.debug
        |            minifyEnabled enableProguardInReleaseBuilds
        |            proguardFiles getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
        |        }
        |    }
        |}
        |
        |dependencies {
        |    // The version of react-native is set by the React Native Gradle Plugin
        |    implementation("com.facebook.react:react-android")
        |
        |    if (hermesEnabled.toBoolean()) {
        |        implementation("com.facebook.react:hermes-android")
        |    } else {
        |        implementation jscFlavor
        |    }
        |}
        |
        """.trimMargin()

    private fun manifest(): String =
        """
        |${GeneratedHeader.xml(version)}
        |<manifest xmlns:android="http://schemas.android.com/apk/res/android">
        |
        |    <uses-permission android:name="android.permission.INTERNET" />
        |
        |    <application
        |      android:name=".MainApplication"
        |      android:label="@string/app_name"
        |      android:allowBackup="false"
        |      android:theme="@style/AppTheme"
        |      android:usesCleartextTraffic="${'$'}{usesCleartextTraffic}"
        |      android:supportsRtl="true">
        |      <activity
        |        android:name=".MainActivity"
        |        android:label="@string/app_name"
        |        android:configChanges="keyboard|keyboardHidden|orientation|screenLayout|screenSize|smallestScreenSize|uiMode"
        |        android:launchMode="singleTask"
        |        android:windowSoftInputMode="adjustResize"
        |        android:exported="true">
        |        <intent-filter>
        |            <action android:name="android.intent.action.MAIN" />
        |            <category android:name="android.intent.category.LAUNCHER" />
        |        </intent-filter>
        |      </activity>
        |    </application>
        |</manifest>
        |
        """.trimMargin()

    private fun mainActivity(): String =
        """
        |${GeneratedHeader.slash(version)}
        |package $appPackage
        |
        |import com.facebook.react.ReactActivity
        |import com.facebook.react.ReactActivityDelegate
        |import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
        |import com.facebook.react.defaults.DefaultReactActivityDelegate
        |
        |class MainActivity : ReactActivity() {
        |    /** The main component registered from JavaScript. */
        |    override fun getMainComponentName(): String = "$appName"
        |
        |    override fun createReactActivityDelegate(): ReactActivityDelegate =
        |        DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)
        |}
        |
        """.trimMargin()

    private fun mainApplication(): String =
        """
        |${GeneratedHeader.slash(version)}
        |package $appPackage
        |
        |import android.app.Application
        |import com.facebook.react.PackageList
        |import com.facebook.react.ReactApplication
        |import com.facebook.react.ReactHost
        |import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
        |import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
        |
        |class MainApplication : Application(), ReactApplication {
        |    override val reactHost: ReactHost by lazy {
        |        getDefaultReactHost(
        |            context = applicationContext,
        |            packageList = PackageList(this).packages,
        |        )
        |    }
        |
        |    override fun onCreate() {
        |        super.onCreate()
        |        loadReactNative(this)
        |    }
        |}
        |
        """.trimMargin()

    private fun styles(): String =
        """
        |${GeneratedHeader.xml(version)}
        |<resources>
        |    <style name="AppTheme" parent="Theme.AppCompat.DayNight.NoActionBar">
        |        <item name="android:editTextBackground">@drawable/rn_edit_text_material</item>
        |    </style>
        |</resources>
        |
        """.trimMargin()

    private fun editTextDrawable(): String =
        """
        |${GeneratedHeader.xml(version)}
        |<!-- Copyright (C) 2014 The Android Open Source Project
        |
        |     Licensed under the Apache License, Version 2.0 (the "License");
        |     you may not use this file except in compliance with the License.
        |     You may obtain a copy of the License at
        |
        |          http://www.apache.org/licenses/LICENSE-2.0
        |
        |     Unless required by applicable law or agreed to in writing, software
        |     distributed under the License is distributed on an "AS IS" BASIS,
        |     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        |     See the License for the specific language governing permissions and
        |     limitations under the License.
        |-->
        |<inset xmlns:android="http://schemas.android.com/apk/res/android"
        |       android:insetLeft="@dimen/abc_edit_text_inset_horizontal_material"
        |       android:insetRight="@dimen/abc_edit_text_inset_horizontal_material"
        |       android:insetTop="@dimen/abc_edit_text_inset_top_material"
        |       android:insetBottom="@dimen/abc_edit_text_inset_bottom_material"
        |       >
        |
        |    <selector>
        |        <!-- A copy of abc_edit_text_material without the state_pressed/state_focused item,
        |             which crashes on some devices (react-native/pull/29452). -->
        |        <item android:state_enabled="false" android:drawable="@drawable/abc_textfield_default_mtrl_alpha"/>
        |        <item android:drawable="@drawable/abc_textfield_activated_mtrl_alpha"/>
        |    </selector>
        |
        |</inset>
        |
        """.trimMargin()
}
