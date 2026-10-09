package com.devstrike.henosis.core.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import java.nio.file.Files
import java.nio.file.Path

/**
 * A `henosis.yaml` that could not be read. [path] is the key path inside the file
 * (`facades[0].methods[3]`), or `<root>` when the problem is the document itself.
 */
class ConfigException(
    val path: String,
    message: String,
    val line: Int? = null,
    val column: Int? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /** `line:column path: message`, ready to print. */
    fun format(file: Path? = null): String =
        buildString {
            if (file != null) append(file).append(':')
            if (line != null) append(line).append(':').append(column ?: 1).append(' ')
            append(path).append(": ").append(message)
        }
}

/** A parsed config together with the file it came from, so relative paths can be resolved. */
data class LoadedConfig(
    val config: HenosisConfig,
    val file: Path,
) {
    /** Resolves [relative] against the config file's directory. */
    fun resolve(relative: String): Path =
        file
            .toAbsolutePath()
            .parent
            .resolve(relative)
            .normalize()
}

/** Reads and parses `henosis.yaml`. Unknown keys are errors, so typos never pass silently. */
object ConfigLoader {
    private val yaml = Yaml(configuration = YamlConfiguration(strictMode = true))

    fun load(file: Path): LoadedConfig {
        if (!Files.isRegularFile(file)) {
            throw ConfigException("<root>", "config file not found: $file (run `heno init <sdk.aar>` to create one)")
        }
        return LoadedConfig(parse(Files.readString(file)), file)
    }

    /** Parses YAML text; throws [ConfigException] with the exact key path on any problem. */
    fun parse(text: String): HenosisConfig =
        try {
            yaml.decodeFromString(HenosisConfig.serializer(), text)
        } catch (e: YamlException) {
            val path = e.path.toHumanReadableString().ifEmpty { "<root>" }
            throw ConfigException(path, e.message, e.line, e.column, e)
        }
}
