package com.devstrike.henosis.cli

import com.devstrike.henosis.core.inspect.InspectRenderer
import com.devstrike.henosis.core.inspect.Inspector
import com.devstrike.henosis.parser.android.AndroidSdkParser
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path

/** `heno inspect [sdk] [--json]`: prints the SDK's public API tree with each member tagged ok/warn/unsupported. */
class InspectCommand : CliktCommand(name = "inspect") {
    private val sdk by argument(name = "sdk", help = "SDK archive (.aar or .jar); defaults to source.aar from the config")
        .path(mustExist = true, canBeDir = false, mustBeReadable = true)
        .optional()

    private val config by configOption()

    private val json by option("--json", help = "Print the report as JSON instead of a tree").flag()

    override fun help(context: Context): String = "Print the public API of an SDK, tagging each member ok, warn or unsupported."

    override fun run() {
        val model =
            if (sdk != null) {
                AndroidSdkParser.parse(sdk!!)
            } else {
                val loaded = loadConfig(config)
                val aar = loaded.config.source.aar ?: fail("error    source.aar: not set; pass the SDK path to inspect instead")
                AndroidSdkParser.parse(loaded.resolve(aar))
            }
        val report = Inspector.inspect(model)
        echo(if (json) InspectRenderer.json(report) else InspectRenderer.text(report), trailingNewline = false)
    }
}
