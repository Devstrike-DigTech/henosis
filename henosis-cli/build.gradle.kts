plugins {
    id("henosis.kotlin-module")
    application
}

dependencies {
    implementation(project(":henosis-core"))
    implementation(project(":henosis-parser-android"))
    implementation(libs.clikt)
}

application {
    applicationName = "heno"
    mainClass.set("com.devstrike.henosis.cli.MainKt")
}

// The golden test runs `heno inspect --json` against the sample SDK's AAR.
val buildSampleSdk = gradle.includedBuild("sample-sdk").task(":assembleRelease")
tasks.named("test") { dependsOn(buildSampleSdk) }
tasks.named("updateGolden") { dependsOn(buildSampleSdk) }
