package com.devstrike.henosis.parser.android

import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactRequest
import org.eclipse.aether.resolution.ArtifactResolutionException
import org.eclipse.aether.supplier.RepositorySystemSupplier
import org.eclipse.aether.supplier.SessionBuilderSupplier
import java.nio.file.Files
import java.nio.file.Path

/** `group:artifact:version`, parsed and validated. */
data class MavenCoordinates(
    val group: String,
    val artifact: String,
    val version: String,
) {
    override fun toString(): String = "$group:$artifact:$version"

    companion object {
        private val segment = Regex("[A-Za-z0-9_.\\-]+")

        /** Parses `group:artifact:version`; returns `null` when the text is not three valid segments. */
        fun parse(text: String): MavenCoordinates? {
            val parts = text.split(':')
            if (parts.size != 3 || parts.any { !segment.matches(it) }) return null
            return MavenCoordinates(parts[0], parts[1], parts[2])
        }
    }
}

/** Why an artifact could not be fetched; the message is written for the user. */
class MavenResolutionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Downloads an SDK artifact (`.aar` or `.jar`) from Maven repositories with Apache Maven Resolver.
 *
 * Google and Maven Central are always searched, followed by any [extraRepositories]. Downloads are
 * cached under [localRepository] (default `~/.cache/henosis/m2`, overridable with `HENOSIS_CACHE`).
 */
class MavenResolver(
    private val localRepository: Path = defaultCache(),
    private val extraRepositories: List<String> = emptyList(),
    /** False to search only [extraRepositories]; also forced off by `HENOSIS_OFFLINE` or `-Dhenosis.offline`. */
    private val includeDefaultRepositories: Boolean = !offline(),
) {
    /** Resolves [coordinates] and returns the archive on disk, trying `aar` first and then `jar`. */
    fun resolve(coordinates: MavenCoordinates): Path {
        Files.createDirectories(localRepository)
        val system: RepositorySystem = RepositorySystemSupplier().get()
        system.use {
            SessionBuilderSupplier(system).get().withLocalRepositoryBaseDirectories(localRepository).build().use { session ->
                val repositories = repositories()
                val failures = mutableListOf<String>()
                for (extension in listOf("aar", "jar")) {
                    val artifact = DefaultArtifact(coordinates.group, coordinates.artifact, extension, coordinates.version)
                    val request = ArtifactRequest(artifact, repositories, null)
                    try {
                        val result = system.resolveArtifact(session, request)
                        return result.artifact.path
                    } catch (e: ArtifactResolutionException) {
                        failures += "$extension: ${e.message?.lineSequence()?.first() ?: e.javaClass.simpleName}"
                    }
                }
                throw MavenResolutionException(
                    "could not resolve $coordinates as .aar or .jar from ${repositories.joinToString { it.url }}\n  " +
                        failures.joinToString("\n  "),
                )
            }
        }
    }

    private fun repositories(): List<RemoteRepository> {
        val defaults =
            listOf(
                RemoteRepository.Builder("google", "default", "https://dl.google.com/dl/android/maven2/").build(),
                RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build(),
            )
        val extras = extraRepositories.mapIndexed { i, url -> RemoteRepository.Builder("custom$i", "default", url).build() }
        return if (includeDefaultRepositories) defaults + extras else extras
    }

    companion object {
        /** True when `HENOSIS_OFFLINE` is set or `-Dhenosis.offline=true`, which skips Google and Maven Central. */
        fun offline(): Boolean = System.getenv("HENOSIS_OFFLINE") != null || System.getProperty("henosis.offline") == "true"

        /** `$HENOSIS_CACHE/m2`, else `~/.cache/henosis/m2`. */
        fun defaultCache(): Path {
            val base = System.getenv("HENOSIS_CACHE")?.let { Path.of(it) } ?: Path.of(System.getProperty("user.home"), ".cache", "henosis")
            return base.resolve("m2")
        }
    }
}
