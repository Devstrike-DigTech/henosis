package com.devstrike.henosis.cli

import com.devstrike.henosis.core.inspect.InspectRenderer
import com.devstrike.henosis.core.inspect.Inspector
import com.devstrike.henosis.parser.android.AndroidSdkParser
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path

/** `heno inspect <sdk> [--json]`: prints the SDK's public API tree with each member tagged ok/warn/unsupported. */
class InspectCommand : CliktCommand(name = "inspect") {
    private val sdk by argument(name = "sdk", help = "Path to the SDK archive (.aar or .jar) to inspect")
        .path(mustExist = true, canBeDir = false, mustBeReadable = true)

    private val json by option("--json", help = "Print the report as JSON instead of a tree").flag()

    override fun help(context: Context): String = "Print the public API of an SDK, tagging each member ok, warn or unsupported."

    override fun run() {
        val model = AndroidSdkParser.parse(sdk)
        val report = Inspector.inspect(model)
        echo(if (json) InspectRenderer.json(report) else InspectRenderer.text(report), trailingNewline = false)
    }
}
