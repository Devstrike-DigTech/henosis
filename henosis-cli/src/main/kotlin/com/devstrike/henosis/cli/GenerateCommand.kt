package com.devstrike.henosis.cli

import com.devstrike.henosis.core.ir.IrJson
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Files
import java.nio.file.Path

/** `heno generate [--target flutter,rn] [--out dir] [--emit-ir]`: runs doctor, then writes the packages. */
class GenerateCommand : CliktCommand(name = "generate") {
    private val config by configOption()

    private val targets by option("-t", "--target", help = "Comma-separated targets: flutter, rn (default: every target in the config)")
        .split(",")
        .default(emptyList())

    private val out by option("-o", "--out", help = "Output directory").path(canBeFile = false).default(Path.of("henosis-out"))

    private val emitIr by option("--emit-ir", help = "Also write ir.json to the output directory").flag()

    override fun help(context: Context): String = "Run doctor, then generate the target packages (and ir.json with --emit-ir)."

    override fun run() {
        val unknown = targets.filter { it !in KNOWN_TARGETS }
        if (unknown.isNotEmpty()) fail("error    --target: unknown target(s) ${unknown.joinToString()}; use flutter and/or rn")
        val result = runDoctor(config)
        val ir = result.ir ?: throw ProgramResult(ExitCode.VALIDATION)
        Files.createDirectories(out)
        if (emitIr) {
            val file = out.resolve("ir.json")
            Files.writeString(file, IrJson.encode(ir))
            echo("Wrote $file")
        }
        val wanted =
            targets.ifEmpty {
                listOfNotNull(ir.targets.flutter?.let { "flutter" }, ir.targets.reactNative?.let { "rn" })
            }
        wanted.forEach { target ->
            echo(
                "generate: the $target generator is not available yet (${if (target == "flutter") "milestone M3" else "milestone M4"})",
                err = true,
            )
        }
        if (!emitIr) fail("error    nothing to generate yet; pass --emit-ir to write the IR")
    }

    private companion object {
        val KNOWN_TARGETS = setOf("flutter", "rn")
    }
}
