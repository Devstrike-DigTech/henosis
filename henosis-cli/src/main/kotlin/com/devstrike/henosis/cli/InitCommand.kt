package com.devstrike.henosis.cli

import com.devstrike.henosis.core.HenosisVersion
import com.devstrike.henosis.core.init.StarterConfig
import com.devstrike.henosis.parser.android.AndroidSdkParser
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Files
import java.nio.file.Path

/** `heno init <aar-or-coords>`: parses the SDK and writes a starter `henosis.yaml` with every class commented out. */
class InitCommand : CliktCommand(name = "init") {
    private val sdk by argument(name = "sdk", help = "Path to the SDK's .aar (Maven coordinates arrive in M5)")

    private val out by option("-o", "--out", help = "Where to write the config").path(canBeDir = false).default(Path.of("henosis.yaml"))

    private val name by option("--name", help = "Base name; defaults to one derived from the archive file name")

    private val force by option("--force", help = "Overwrite an existing config").flag()

    override fun help(context: Context): String =
        "Create a starter henosis.yaml for an SDK, with every public class listed and commented out."

    override fun run() {
        val archive = Path.of(sdk)
        if (!Files.isRegularFile(archive)) {
            if (sdk.count { it == ':' } == 2 && !sdk.contains('/')) {
                fail("error    Maven coordinates ('$sdk') are not supported yet (milestone M5); download the .aar and pass its path")
            }
            fail("error    SDK archive not found: $archive")
        }
        if (Files.exists(out) && !force) fail("error    $out already exists; pass --force to overwrite it")
        val model = AndroidSdkParser.parse(archive)
        val baseName = name ?: StarterConfig.deriveName(archive.fileName.toString())
        val text = StarterConfig.render(model, aarPathFor(archive), baseName, HenosisVersion.current)
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
