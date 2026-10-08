package com.devstrike.henosis.parser.android

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.io.path.extension

/** Reads every `.class` file out of an `.aar` (its nested `classes.jar`) or a plain `.jar`. */
object ArchiveReader {
    /** Returns the raw bytes of each class file in [archive], in archive order. */
    fun readClassFiles(archive: Path): List<ByteArray> {
        require(Files.isRegularFile(archive)) { "SDK archive not found: $archive" }
        return when (archive.extension.lowercase()) {
            "aar" -> {
                ZipFile(archive.toFile()).use { zip ->
                    val classesJar =
                        zip.getEntry("classes.jar")
                            ?: throw IllegalArgumentException("$archive has no classes.jar; is it an Android AAR?")
                    zip.getInputStream(classesJar).use { readClassesFromJar(it) }
                }
            }

            "jar" -> {
                Files.newInputStream(archive).use { readClassesFromJar(it) }
            }

            else -> {
                throw IllegalArgumentException("Unsupported SDK archive '$archive': expected a .aar or .jar file")
            }
        }
    }

    private fun readClassesFromJar(input: InputStream): List<ByteArray> =
        ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }
                .filter { !it.isDirectory && it.name.endsWith(".class") && !it.name.startsWith("META-INF/") }
                .map { zip.readBytes() }
                .toList()
        }
}
