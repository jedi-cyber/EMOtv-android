package pe.unheval.emotv.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Resumen de `GET /mobile/summary`: solo datos de la cuenta que inició sesión.
 * Las personas evaluadas no se registran: cada sesión se identifica por su código.
 */
data class DashboardSummary(
    val user: AuthUser,
    val date: LocalDate,
    val todaySessions: List<SessionSummary>,
    val recentSessions: List<SessionSummary>,
    /** Protocolos que la cuenta puede aplicar; puede estar vacía. */
    val protocols: List<SessionProtocol>,
    /** Opciones de «¿Con quién es la sesión?» para empezar sin protocolo. */
    val audiences: List<AudienceOption>,
)

data class SessionSummary(
    val id: String,
    /** `null` solo en sesiones anteriores a la revisión 3 del contrato. */
    val code: String?,
    val startedAt: Instant,
    val state: SessionState,
    val audience: SessionAudience,
    val guidedPhase: GuidedPhase?,
    val protocolName: String?,
)

/** Con quién es la sesión (`audience` y `session_type`). */
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

/** `state` de una sesión. `created` y `recognized` solo aparecen en sesiones propias de la web. */
enum class SessionState(val apiValue: String) {
    InProgress("in_progress"),
    Completed("completed"),
    Cancelled("cancelled"),
    Created("created"),
    Recognized("recognized"),
    Unknown(""),
    ;

    companion object {
        fun fromApi(value: String) = entries.firstOrNull { it.apiValue == value } ?: Unknown
    }
}

/** Fases de la sesión (docs/mobile-api.md). `Live` solo existe en sesiones sin protocolo. */
enum class GuidedPhase(val apiValue: String) {
    PreQuestionnaire("pre_questionnaire"),
    BeforeAnalyzer("before_analyzer"),
    Calibration("calibration"),
    Stimuli("stimuli"),
    PostQuestionnaire("post_questionnaire"),
    Live("live"),
    Finished("finished"),
    Stopped("stopped"),
    ;

    companion object {
        fun fromApi(value: String?) = entries.firstOrNull { it.apiValue == value }
    }
}
