package com.devstrike.henosis.parser.android

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Resolves from a file-based repository built from the sample SDK, so no network is involved. */
class MavenResolverTest {
    private val work: Path = Path.of("build/tmp/maven-resolver").also { Files.createDirectories(it) }

    private fun fileRepository(): Path {
        val repo = work.resolve("repo")
        val dir = repo.resolve("com/devstrike/sample-sdk/1.2.3")
        Files.createDirectories(dir)
        Files.copy(SampleSdk.aar, dir.resolve("sample-sdk-1.2.3.aar"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        Files.writeString(
            dir.resolve("sample-sdk-1.2.3.pom"),
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.devstrike</groupId>
              <artifactId>sample-sdk</artifactId>
              <version>1.2.3</version>
              <packaging>aar</packaging>
            </project>
            """.trimIndent(),
        )
        return repo
    }

    @Test
    fun `parses and rejects coordinates`() {
        MavenCoordinates.parse("com.squareup.okio:okio-jvm:3.18.2") shouldBe MavenCoordinates("com.squareup.okio", "okio-jvm", "3.18.2")
        MavenCoordinates.parse("okio:3.18.2").shouldBeNull()
        MavenCoordinates.parse("a:b:c:d").shouldBeNull()
        MavenCoordinates.parse("a b:c:1").shouldBeNull()
    }

    @Test
    fun `resolves an aar from a file repository and parses it`() {
        val repo = fileRepository()
        val cache = work.resolve("cache")
        val resolver =
            MavenResolver(localRepository = cache, extraRepositories = listOf(repo.toUri().toString()), includeDefaultRepositories = false)
        val archive = resolver.resolve(MavenCoordinates("com.devstrike", "sample-sdk", "1.2.3"))
        archive.fileName.toString() shouldBe "sample-sdk-1.2.3.aar"
        archive.toAbsolutePath().startsWith(cache.toAbsolutePath()) shouldBe true
        AndroidSdkParser.parse(archive).classes.map { it.name } shouldContain "com.devstrike.sample.Greeter"
    }

    @Test
    fun `a missing artifact fails with every repository and extension named`() {
        val repo = fileRepository()
        val resolver =
            MavenResolver(
                localRepository = work.resolve("cache"),
                extraRepositories = listOf(repo.toUri().toString()),
                includeDefaultRepositories = false,
            )
        val e = shouldThrow<MavenResolutionException> { resolver.resolve(MavenCoordinates("com.devstrike", "nope", "9.9.9")) }
        e.message shouldContain "com.devstrike:nope:9.9.9"
        e.message shouldContain "aar:"
        e.message shouldContain "jar:"
    }
}
