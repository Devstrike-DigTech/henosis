package com.devstrike.henosis.cli

import com.devstrike.henosis.core.HenosisVersion
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption

/** The `heno` root command. Subcommands do the work; this only carries `--version` and help. */
class Heno : CliktCommand(name = "heno") {
    init {
        versionOption(HenosisVersion.current, names = setOf("--version", "-V"))
    }

    override fun help(context: com.github.ajalt.clikt.core.Context): String =
        "Henosis: turn a native Android SDK into Flutter and React Native packages from one config."

    override fun run() = Unit
}

/** Builds the full `heno` command tree. Used by `main` and by tests. */
fun heno(): CliktCommand = Heno().subcommands(InspectCommand())
