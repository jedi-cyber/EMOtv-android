package pe.unheval.emotv.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Resumen de `GET /mobile/summary`: solo datos de la cuenta que inició sesión.
 * Ningún modelo tiene el nombre de un participante; se identifican por su código.
 */
data class DashboardSummary(
    val user: AuthUser,
    val date: LocalDate,
    val todaySessions: List<SessionSummary>,
    val participants: List<Participant>,
)

data class Participant(
    val id: String,
    val code: String,
    val kind: ParticipantKind,
    /** `null` si se registró sin edad (`missing` incluye [MissingRequirement.Age]). */
    val ageYears: Int?,
    /** Con `false` el servidor no permite iniciar una sesión. */
    val ready: Boolean,
    val missing: List<MissingRequirement>,
    /** Textos del servidor que explican lo que falta, listos para mostrar. */
    val warnings: List<String>,
    val lastSession: SessionSummary?,
)

data class SessionSummary(
    val id: String,
    val startedAt: Instant,
    val state: SessionState,
    val guidedPhase: GuidedPhase?,
    val participantId: String,
    val participantCode: String,
    val protocolName: String?,
)

enum class ParticipantKind(val apiValue: String) {
    Adult("adult"),
    Child("child"),
    Unknown(""),
    ;

    companion object {
        fun fromApi(value: String) = entries.firstOrNull { it.apiValue == value } ?: Unknown
    }
}

/** Valores de `missing`. */
enum class MissingRequirement(val apiValue: String) {
    Participation("participation"),
    Camera("camera"),
    ChildAssent("child_assent"),
    Revoked("revoked"),
    Age("age"),
    Other(""),
    ;

    companion object {
        fun fromApi(value: String) = entries.firstOrNull { it.apiValue == value } ?: Other
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

/** Fases de la sesión guiada (docs/mobile-api.md). */
enum class GuidedPhase(val apiValue: String) {
    Confirmation("confirmation"),
    PreQuestionnaire("pre_questionnaire"),
    BeforeAnalyzer("before_analyzer"),
    Calibration("calibration"),
    Stimuli("stimuli"),
    PostQuestionnaire("post_questionnaire"),
    Finished("finished"),
    Stopped("stopped"),
    ;

    companion object {
        fun fromApi(value: String?) = entries.firstOrNull { it.apiValue == value }
    }
}
