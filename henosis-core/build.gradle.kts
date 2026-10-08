plugins {
    id("henosis.kotlin-module")
    id("org.jetbrains.kotlin.plugin.serialization")
    `java-test-fixtures`
}

dependencies {
    api(libs.kotlinx.serialization.json)
    implementation(libs.kaml)
    testFixturesImplementation(libs.kotest.assertions.core)
}

tasks.processResources {
    val henosisVersion = project.version.toString()
    inputs.property("henosisVersion", henosisVersion)
    filesMatching("henosis/version.txt") {
        expand("version" to henosisVersion)
    }
}
