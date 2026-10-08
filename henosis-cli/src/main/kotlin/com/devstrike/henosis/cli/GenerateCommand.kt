package com.devstrike.henosis.cli

import com.devstrike.henosis.core.codegen.GeneratorContext
import com.devstrike.henosis.core.ir.IrJson
import com.devstrike.henosis.gen.flutter.FlutterGenerator
import com.devstrike.henosis.gen.reactnative.ReactNativeGenerator
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
        val loaded = loadConfig(config)
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
                listOfNotNull(
                    ir.targets.flutter?.let { FlutterGenerator.TARGET },
                    ir.targets.reactNative?.let { ReactNativeGenerator.TARGET },
                )
            }
        val context =
            GeneratorContext(
                aarFile =
                    loaded.config.source.aar
                        ?.let { loaded.resolve(it) },
            )
        var generated = 0
        wanted.forEach { target ->
            when (target) {
                FlutterGenerator.TARGET -> {
                    if (ir.targets.flutter == null) fail("error    --target flutter: the config has no targets.flutter section")
                    val dir = FlutterGenerator.generate(ir, context).write(out)
                    echo("Wrote Flutter plugin to $dir")
                    generated++
                }

                ReactNativeGenerator.TARGET -> {
                    if (ir.targets.reactNative == null) fail("error    --target rn: the config has no targets.react_native section")
                    val dir = ReactNativeGenerator.generate(ir, context).write(out)
                    echo("Wrote React Native package to $dir")
                    generated++
                }
            }
        }
        if (generated == 0 &&
            !emitIr
        ) {
            fail("error    nothing was generated; add targets.flutter or targets.react_native, or pass --emit-ir")
        }
    }

    private companion object {
        val KNOWN_TARGETS = setOf(FlutterGenerator.TARGET, ReactNativeGenerator.TARGET)
    }
}
