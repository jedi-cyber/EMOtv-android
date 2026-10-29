package pe.unheval.emotv.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class StatusDto(
    val status: String,
    @SerialName("api_version") val apiVersion: String,
    @SerialName("min_app_version") val minAppVersion: String,
    val services: ServicesDto = ServicesDto(),
)

@Serializable
data class ServicesDto(
    val login: Boolean = false,
    val sessions: Boolean = false,
    val reports: Boolean = false,
    val statistics: Boolean = false,
)

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class RefreshRequestDto(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class TokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("refresh_expires_in") val refreshExpiresIn: Long,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val role: String,
    @SerialName("role_name") val roleName: String,
)

/**
 * `GET /mobile/summary`. Las personas evaluadas no se registran: cada sesión se identifica
 * solo por su código (`ignoreUnknownKeys` descarta cualquier campo adicional).
 */
@Serializable
data class SummaryDto(
    val user: UserDto,
    val date: String,
    @SerialName("today_sessions") val todaySessions: List<SessionDto> = emptyList(),
    @SerialName("recent_sessions") val recentSessions: List<SessionDto> = emptyList(),
    @SerialName("pending_reviews") val pendingReviews: Int = 0,
)

/** `SessionSummary`; en `GET /mobile/reviews` trae además `report_id`. */
@Serializable
data class SessionDto(
    val id: String,
    /** `null` solo en sesiones anteriores a la revisión 3 del contrato. */
    val code: String? = null,
    @SerialName("started_at") val startedAt: String,
    val state: String,
    val audience: String? = null,
    @SerialName("protocol_name") val protocolName: String? = null,
    val mine: Boolean = true,
    @SerialName("report_status") val reportStatus: String? = null,
    @SerialName("report_id") val reportId: String? = null,
)

// --- Detalle de la sesión ----------------------------------------------------------------

@Serializable
data class SessionDetailDto(
    val session: SessionDto,
    val timeline: TimelineDto = TimelineDto(),
    val metrics: MetricsDto = MetricsDto(),
)

@Serializable
data class TimelineDto(
    @SerialName("duration_seconds") val durationSeconds: Double = 0.0,
    val lanes: LanesDto = LanesDto(),
)

/** Tiempos en segundos desde el inicio de la sesión. `heart_rate` está reservado y se ignora. */
@Serializable
data class LanesDto(
    val questionnaires: List<QuestionnaireLaneDto> = emptyList(),
    val stimuli: List<StimulusLaneDto> = emptyList(),
    val captures: List<CaptureLaneDto> = emptyList(),
    val said: List<SaidLaneDto> = emptyList(),
    val markers: List<MarkerLaneDto> = emptyList(),
)

@Serializable
data class QuestionnaireLaneDto(
    val time: Double? = null,
    val moment: String = "",
    val title: String = "",
    val answers: List<AnswerDto> = emptyList(),
)

/** `answer` puede ser un número, un texto, una lista o `null` (sin respuesta). */
@Serializable
data class AnswerDto(
    val question: String = "",
    val answer: JsonElement? = null,
)

@Serializable
data class StimulusLaneDto(
    @SerialName("step_index") val stepIndex: Int = 0,
    val title: String = "",
    val start: Double? = null,
    val skipped: Boolean = false,
)

@Serializable
data class CaptureLaneDto(
    val time: Double? = null,
    /** Expresión **estimada** por el modelo; `null` si no se detectó un rostro. */
    val emotion: String? = null,
    val quality: String = "",
)

@Serializable
data class SaidLaneDto(
    val time: Double? = null,
    val title: String = "",
    val text: String = "",
)

@Serializable
data class MarkerLaneDto(
    val time: Double? = null,
    val kind: String = "",
    val note: String? = null,
)

@Serializable
data class MetricsDto(
    val emotional: List<EmotionalMetricDto> = emptyList(),
    val abstract: List<AbstractMetricDto> = emptyList(),
    val totals: TotalsDto = TotalsDto(),
    @SerialName("abstract_notice") val abstractNotice: String? = null,
)

@Serializable
data class EmotionalMetricDto(
    @SerialName("step_index") val stepIndex: Int = 0,
    val title: String = "",
    val skipped: Boolean = false,
    @SerialName("expected_expression") val expectedExpression: String? = null,
    val counted: Boolean = false,
    @SerialName("matches_expected") val matchesExpected: Boolean? = null,
)

@Serializable
data class AbstractMetricDto(
    @SerialName("step_index") val stepIndex: Int = 0,
    val title: String = "",
    val skipped: Boolean = false,
    val said: String? = null,
)

@Serializable
data class TotalsDto(val sentence: String = "")

// --- Reporte ----------------------------------------------------------------------------

/** `Report`. La línea de tiempo y las métricas que también trae se ignoran: se ven en el detalle de la sesión. */
@Serializable
data class ReportDto(
    val id: String,
    val version: Int = 1,
    val status: String,
    val current: Boolean = true,
    val narrative: String = "",
    @SerialName("session_code") val sessionCode: String? = null,
    @SerialName("estimates_notice") val estimatesNotice: String = "",
    @SerialName("abstract_notice") val abstractNotice: String? = null,
    @SerialName("authored_by_me") val authoredByMe: Boolean = false,
    @SerialName("requires_review") val requiresReview: Boolean = true,
    @SerialName("can_edit") val canEdit: Boolean = false,
    @SerialName("can_review") val canReview: Boolean = false,
    val comments: List<CommentDto> = emptyList(),
)

@Serializable
data class CommentDto(
    val id: String,
    val section: String = "general",
    val text: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class NarrativeRequestDto(val narrative: String)

@Serializable
data class CommentRequestDto(val section: String, val text: String)

// --- Estadísticas -----------------------------------------------------------------------

/** Cuentan sesiones, no personas; el servidor no envía filas por usuario. */
@Serializable
data class StatisticsDto(
    val scope: String = "mine",
    val notice: String = "",
    @SerialName("few_sessions_notice") val fewSessionsNotice: String? = null,
    val sessions: StatisticsSessionsDto = StatisticsSessionsDto(),
    val expressions: StatisticsExpressionsDto = StatisticsExpressionsDto(),
    val coincidence: CoincidenceDto = CoincidenceDto(),
)

@Serializable
data class StatisticsSessionsDto(
    val total: Int = 0,
    val completed: Int = 0,
    val interrupted: Int = 0,
    @SerialName("average_duration_seconds") val averageDurationSeconds: Double? = null,
)

@Serializable
data class StatisticsExpressionsDto(
    @SerialName("counted_captures") val countedCaptures: Int = 0,
    @SerialName("excluded_captures") val excludedCaptures: Int = 0,
    val rows: List<ExpressionRowDto> = emptyList(),
)

@Serializable
data class ExpressionRowDto(
    val expression: String,
    val captures: Int = 0,
    val percent: Double = 0.0,
    val sessions: Int = 0,
)

@Serializable
data class CoincidenceDto(val compared: Int = 0, val matched: Int = 0)

/** `detail` es un objeto `{code, message}` o, en errores de esquema de FastAPI, una lista. */
@Serializable
data class ErrorEnvelopeDto(val detail: JsonElement? = null)
