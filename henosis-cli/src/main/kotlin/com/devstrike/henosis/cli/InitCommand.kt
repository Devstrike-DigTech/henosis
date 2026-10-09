package com.devstrike.henosis.cli

import com.devstrike.henosis.core.HenosisVersion
import com.devstrike.henosis.core.api.ApiModel
import com.devstrike.henosis.core.init.StarterConfig
import com.devstrike.henosis.parser.android.AndroidSdkParser
import com.devstrike.henosis.parser.android.MavenCoordinates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Files
import java.nio.file.Path

/** `heno init <aar-or-coords>`: parses the SDK and writes a starter `henosis.yaml` with every class commented out. */
class InitCommand : CliktCommand(name = "init") {
    private val sdk by argument(name = "sdk", help = "Path to the SDK's .aar/.jar, or Maven coordinates group:artifact:version")

    private val out by option("-o", "--out", help = "Where to write the config").path(canBeDir = false).default(Path.of("henosis.yaml"))

    private val name by option("--name", help = "Base name; defaults to one derived from the archive file name")

    private val force by option("--force", help = "Overwrite an existing config").flag()

    override fun help(context: Context): String =
        "Create a starter henosis.yaml for an SDK, with every public class listed and commented out."

    private val repositories by option("--repo", help = "Extra Maven repository URL (repeatable); only with Maven coordinates").multiple()

    override fun run() {
        val coordinates = MavenCoordinates.parse(sdk)
        val archive = Path.of(sdk)
        val isFile = Files.isRegularFile(archive)
        if (!isFile &&
            coordinates == null
        ) {
            fail("error    SDK not found: '$sdk' is neither an existing .aar/.jar file nor group:artifact:version coordinates")
        }
        if (Files.exists(out) && !force) fail("error    $out already exists; pass --force to overwrite it")
        val source: StarterConfig.Source
        val model: ApiModel
        if (isFile) {
            source = StarterConfig.Source.Aar(aarPathFor(archive))
            model = AndroidSdkParser.parse(archive)
        } else {
            source = StarterConfig.Source.Maven(coordinates!!.toString(), repositories)
            model = AndroidSdkParser.parse(resolveMaven(coordinates, repositories))
        }
        val baseName = name ?: StarterConfig.deriveName(if (isFile) archive.fileName.toString() else sdk)
        val text = StarterConfig.render(model, source, baseName, HenosisVersion.current)
        out.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        Files.writeString(out, text)
        echo("Wrote $out (${model.classes.size} public classes found). Uncomment the facades you want, then run `heno doctor -c $out`.")
    }

    /** The archive path as it should appear in the config: relative to the config file when possible. */
    private fun aarPathFor(archive: Path): String {
        val base = out.toAbsolutePath().normalize().parent ?: return archive.toString()
        val absolute = archive.toAbsolutePath().normalize()
        return try {
            base.relativize(absolute).toString().replace('\\', '/')
        } catch (e: IllegalArgumentException) {
            absolute.toString()
        }
    }
}
