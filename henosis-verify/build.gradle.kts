plugins {
    id("henosis.kotlin-module")
}

dependencies {
    api(project(":henosis-core"))
    testImplementation(testFixtures(project(":henosis-core")))
}
