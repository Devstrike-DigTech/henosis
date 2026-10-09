plugins {
    id("henosis.kotlin-module")
    application
}

dependencies {
    implementation(project(":henosis-core"))
    implementation(project(":henosis-parser-android"))
    implementation(project(":henosis-gen-flutter"))
    implementation(project(":henosis-gen-react-native"))
    implementation(project(":henosis-verify"))
    implementation(libs.clikt)
    testImplementation(libs.json.schema.validator)
    testImplementation(testFixtures(project(":henosis-core")))
}

application {
    applicationName = "heno"
    mainClass.set("com.devstrike.henosis.cli.MainKt")
}

// The golden test runs `heno inspect --json` against the sample SDK's AAR.
val buildSampleSdk = gradle.includedBuild("sample-sdk").task(":assembleRelease")
tasks.named("test") { dependsOn(buildSampleSdk) }
tasks.named("updateGolden") { dependsOn(buildSampleSdk) }

// The README's tour runs `heno` against the sample SDK right after installDist, so build it too.
tasks.named("installDist") { dependsOn(buildSampleSdk) }
