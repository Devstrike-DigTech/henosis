package com.devstrike.henosis.core.doctor

/** Severity of a doctor finding. Any [ERROR] stops generation. */
enum class DoctorLevel {
    ERROR,
    WARNING,
    ;

    val label: String get() = name.lowercase()
}

/**
 * One line of `heno doctor` output.
 *
 * @property path the config key path the finding is about (`facades[0].methods[3]`), or a
 *   descriptive path for things with no config key (`types[com.x.Profile].id`).
 * @property hint how to fix it, when there is a concrete suggestion.
 */
data class DoctorFinding(
    val level: DoctorLevel,
    val path: String,
    val message: String,
    val hint: String? = null,
) {
    /** `error    facades[0].methods[3]: message  fix: hint` */
    fun format(): String =
        buildString {
            append(level.label.padEnd(LABEL_WIDTH))
            append(path).append(": ").append(message)
            if (hint != null) append("  fix: ").append(hint)
        }

    companion object {
        /** Width of the level column, so paths line up for both `error` and `warning`. */
        const val LABEL_WIDTH: Int = 9

        /** Orders findings by path (with numeric indices in natural order), then errors before warnings, then message. */
        val ORDER: Comparator<DoctorFinding> =
            compareBy<DoctorFinding> { naturalKey(it.path) }.thenBy { it.level }.thenBy { it.message }

        private val digits = Regex("\\d+")

        private fun naturalKey(path: String) = digits.replace(path) { it.value.padStart(8, '0') }
    }
}
