package com.devstrike.henosis.core.doctor

import com.devstrike.henosis.core.api.ApiModel
import com.devstrike.henosis.core.config.HenosisConfig
import com.devstrike.henosis.core.config.LoadedConfig
import com.devstrike.henosis.core.ir.Ir
import java.nio.file.Files

/** The outcome of `heno doctor`: every finding, and the IR when there were no errors. */
data class DoctorResult(
    val findings: List<DoctorFinding>,
    val ir: Ir?,
) {
    val errors: List<DoctorFinding> get() = findings.filter { it.level == DoctorLevel.ERROR }
    val warnings: List<DoctorFinding> get() = findings.filter { it.level == DoctorLevel.WARNING }
    val hasErrors: Boolean get() = errors.isNotEmpty()

    /** `doctor: 2 error(s), 1 warning(s)` */
    fun summary(): String = "doctor: ${errors.size} error(s), ${warnings.size} warning(s)"
}

/**
 * Validates a config against the SDK and builds the IR in one pass, so `doctor` and `generate`
 * can never disagree about what is bindable.
 */
object Doctor {
    /** Checks that need only the config and the file system; run these before reading the SDK. */
    fun checkSource(loaded: LoadedConfig): List<DoctorFinding> {
        val source = loaded.config.source
        val findings = mutableListOf<DoctorFinding>()
        when {
            source.aar == null && source.maven == null -> {
                findings += DoctorFinding(DoctorLevel.ERROR, "source", "no SDK source given", "set source.aar to a local .aar path")
            }

            source.aar != null && source.maven != null -> {
                findings += DoctorFinding(DoctorLevel.ERROR, "source", "both aar and maven are set", "keep exactly one of them")
            }

            source.maven != null -> {
                if (!MAVEN_COORDINATES.matches(source.maven)) {
                    findings +=
                        DoctorFinding(
                            DoctorLevel.ERROR,
                            "source.maven",
                            "'${source.maven}' is not group:artifact:version",
                            "use Maven coordinates such as com.squareup.okio:okio-jvm:3.18.2",
                        )
                }
                source.repositories.forEachIndexed { i, url ->
                    if (!url.startsWith("https://") && !url.startsWith("http://") && !url.startsWith("file:")) {
                        findings +=
                            DoctorFinding(
                                DoctorLevel.ERROR,
                                "source.repositories[$i]",
                                "'$url' is not a repository URL",
                                "use an https:// (or file:) URL",
                            )
                    }
                }
            }

            else -> {
                val aar = loaded.resolve(source.aar!!)
                if (!Files.isRegularFile(aar)) {
                    findings +=
                        DoctorFinding(
                            DoctorLevel.ERROR,
                            "source.aar",
                            "file not found: $aar",
                            "check the path; it is relative to the config file",
                        )
                } else if (!aar.fileName.toString().endsWith(".aar", ignoreCase = true)) {
                    findings +=
                        DoctorFinding(DoctorLevel.ERROR, "source.aar", "not an .aar file: $aar", "point source.aar at the SDK's .aar")
                }
            }
        }
        return findings
    }

    private val MAVEN_COORDINATES = Regex("[A-Za-z0-9_.\\-]+:[A-Za-z0-9_.\\-]+:[A-Za-z0-9_.\\-]+")

    /** Runs every rule against [model] and returns the findings plus the IR when error-free. */
    fun run(
        config: HenosisConfig,
        model: ApiModel,
        henosisVersion: String,
    ): DoctorResult = IrBuilder(config, model, henosisVersion).build()
}
