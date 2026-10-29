package pe.unheval.emotv.domain.model

/** `mine`: las sesiones propias. `group`: las del grupo del Docente. */
enum class StatisticsScope(val apiValue: String) { Mine("mine"), Group("group") }

/** Estadísticas agregadas: cuentan sesiones, no personas, y no se desglosan por usuario. */
data class SessionStatistics(
    val scope: StatisticsScope,
    /** Aviso del servidor; se muestra siempre. */
    val notice: String,
    val fewSessionsNotice: String?,
    val total: Int,
    val completed: Int,
    val interrupted: Int,
    val averageDurationSeconds: Double?,
    val countedCaptures: Int,
    val excludedCaptures: Int,
    val expressions: List<ExpressionShare>,
    val compared: Int,
    val matched: Int,
)

/** Parte de las capturas de buena calidad estimadas en una expresión. */
data class ExpressionShare(val expression: String, val captures: Int, val percent: Double, val sessions: Int)
