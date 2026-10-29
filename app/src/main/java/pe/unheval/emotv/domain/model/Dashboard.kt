package pe.unheval.emotv.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Resumen de `GET /mobile/summary`: las sesiones que la cuenta ya ve en la web (del Docente,
 * también las de su grupo). Las personas evaluadas no se registran: cada sesión se identifica
 * por su código.
 */
data class DashboardSummary(
    val user: AuthUser,
    val date: LocalDate,
    val todaySessions: List<SessionSummary>,
    val recentSessions: List<SessionSummary>,
    /** Del Docente: reportes que esperan su revisión. */
    val pendingReviews: Int,
)

data class SessionSummary(
    val id: String,
    /** `null` solo en sesiones anteriores a la revisión 3 del contrato. */
    val code: String?,
    val startedAt: Instant,
    val state: SessionState,
    val audience: SessionAudience,
    val protocolName: String?,
    /** `false` en las sesiones de los usuarios del grupo del Docente. */
    val mine: Boolean,
    /** Estado del reporte vigente; `null` si la sesión no tiene reporte. */
    val reportStatus: ReportStatus?,
)

/** Con quién fue la sesión (`audience`). */
enum class SessionAudience(val apiValue: String) {
    Own("propia"),
    Adult("adulto"),
    Child("infantil"),
    Unknown(""),
    ;

    companion object {
        fun fromApi(value: String?) = entries.firstOrNull { it.apiValue == value } ?: Unknown
    }
}

/** `state` de una sesión. `created` y `recognized` aparecen en sesiones de actividades de la web. */
enum class SessionState(val apiValue: String) {
    InProgress("in_progress"),
    Completed("completed"),
    Cancelled("cancelled"),
    Created("created"),
    Recognized("recognized"),
    Unknown(""),
    ;

    val finished: Boolean get() = this == Completed || this == Cancelled

    companion object {
        fun fromApi(value: String) = entries.firstOrNull { it.apiValue == value } ?: Unknown
    }
}
