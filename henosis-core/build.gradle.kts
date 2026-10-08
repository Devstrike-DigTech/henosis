plugins {
    id("henosis.kotlin-module")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    api(libs.kotlinx.serialization.json)
}

tasks.processResources {
    val henosisVersion = project.version.toString()
    inputs.property("henosisVersion", henosisVersion)
    filesMatching("henosis/version.txt") {
        expand("version" to henosisVersion)
    }
}
