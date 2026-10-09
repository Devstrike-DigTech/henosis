plugins {
    id("henosis.kotlin-module")
}

dependencies {
    api(project(":henosis-core"))
    implementation(libs.asm)
    implementation(libs.asm.tree)
    implementation(libs.kotlin.metadata.jvm)
    implementation(libs.maven.resolver.api)
    implementation(libs.maven.resolver.util)
    implementation(libs.maven.resolver.supplier)
    runtimeOnly(libs.slf4j.nop)
}

// Parser tests read the sample SDK's AAR, built by the included `fixtures/sample-sdk` build.
val buildSampleSdk = gradle.includedBuild("sample-sdk").task(":assembleRelease")
tasks.named("test") { dependsOn(buildSampleSdk) }
tasks.named("updateGolden") { dependsOn(buildSampleSdk) }
