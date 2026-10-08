plugins {
    id("henosis.kotlin-module")
}

dependencies {
    api(project(":henosis-core"))
    implementation(libs.asm)
    implementation(libs.asm.tree)
    implementation(libs.kotlin.metadata.jvm)
}

// Parser tests read the sample SDK's AAR, built by the included `fixtures/sample-sdk` build.
val buildSampleSdk = gradle.includedBuild("sample-sdk").task(":assembleRelease")
tasks.named("test") { dependsOn(buildSampleSdk) }
tasks.named("updateGolden") { dependsOn(buildSampleSdk) }
