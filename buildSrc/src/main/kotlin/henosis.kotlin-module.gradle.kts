import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(17)
}

configure<KtlintExtension> {
    version.set(libs.findVersion("ktlint").get().requiredVersion)
}

dependencies {
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testImplementation"(libs.findLibrary("kotest-assertions-core").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}

val repoRoot = rootProject.layout.projectDirectory
val sampleAar = repoRoot.file("fixtures/sample-sdk/build/outputs/aar/sample-sdk-release.aar")

fun Test.configureHenosisTest(updateGolden: Boolean) {
    useJUnitPlatform()
    systemProperty("henosis.repoRoot", repoRoot.asFile.absolutePath)
    systemProperty("henosis.sampleAar", sampleAar.asFile.absolutePath)
    systemProperty("henosis.updateGolden", updateGolden.toString())
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.named<Test>("test") {
    configureHenosisTest(updateGolden = false)
}

tasks.register<Test>("updateGolden") {
    group = "verification"
    description = "Runs this module's tests in golden-update mode (rewrites golden files instead of comparing)."
    val sourceSets = project.extensions.getByType<SourceSetContainer>()
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    configureHenosisTest(updateGolden = true)
    outputs.upToDateWhen { false }
}
