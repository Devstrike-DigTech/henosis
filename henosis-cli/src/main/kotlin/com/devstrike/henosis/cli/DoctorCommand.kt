package com.devstrike.henosis.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult

/** `heno doctor`: validates the config and every selected type; exit code 1 on any error. */
class DoctorCommand : CliktCommand(name = "doctor") {
    private val config by configOption()

    override fun help(context: Context): String = "Validate henosis.yaml against the SDK; one line per finding with a fix hint."

    override fun run() {
        val result = runDoctor(config)
        if (result.hasErrors) throw ProgramResult(ExitCode.VALIDATION)
    }
}
