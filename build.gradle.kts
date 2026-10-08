allprojects {
    group = "com.devstrike.henosis"
    version = providers.gradleProperty("henosisVersion").get()
}

tasks.register("updateGolden") {
    group = "verification"
    description = "Re-writes every golden file from the current generator/parser output."
    dependsOn(subprojects.map { "${it.path}:updateGolden" })
}
