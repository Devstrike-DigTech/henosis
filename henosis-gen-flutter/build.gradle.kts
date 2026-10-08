plugins {
    id("henosis.kotlin-module")
}

dependencies {
    api(project(":henosis-core"))
    implementation(project(":henosis-gen-bridge"))
    testImplementation(testFixtures(project(":henosis-core")))
}
