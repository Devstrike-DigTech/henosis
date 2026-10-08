plugins {
    id("henosis.kotlin-module")
}

dependencies {
    api(project(":henosis-core"))
    implementation(libs.kotlinpoet)
    testImplementation(testFixtures(project(":henosis-core")))
}
