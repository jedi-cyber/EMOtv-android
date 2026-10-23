package pe.unheval.emotv.domain.model

/** Versión `x.y.z`. Lo que siga al número de parche (por ejemplo `-debug`) se ignora. */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    companion object {
        private val PATTERN = Regex("""^\s*(\d+)(?:\.(\d+))?(?:\.(\d+))?""")

        fun parse(text: String): AppVersion? {
            val match = PATTERN.find(text) ?: return null
            val (major, minor, patch) = match.destructured
            return AppVersion(
                major = major.toInt(),
                minor = minor.toIntOrNull() ?: 0,
                patch = patch.toIntOrNull() ?: 0,
            )
        }
    }
}
