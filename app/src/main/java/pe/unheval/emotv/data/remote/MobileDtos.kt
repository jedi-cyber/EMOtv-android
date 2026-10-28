package pe.unheval.emotv.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
    val protocols: List<ProtocolDto> = emptyList(),
    val audiences: List<AudienceDto> = emptyList(),
)

@Serializable
data class SessionDto(
    val id: String,
    /** `null` solo en sesiones anteriores a la revisión 3 del contrato. */
    val code: String? = null,
    @SerialName("started_at") val startedAt: String,
    val state: String,
    val audience: String? = null,
    @SerialName("guided_phase") val guidedPhase: String? = null,
    @SerialName("protocol_name") val protocolName: String? = null,
)

@Serializable
data class ProtocolDto(
    val id: String,
    val name: String,
    val version: Int = 1,
    @SerialName("for_children") val forChildren: Boolean = false,
    val audience: String? = null,
    @SerialName("step_count") val stepCount: Int = 0,
    @SerialName("has_pre_questionnaire") val hasPreQuestionnaire: Boolean = false,
    @SerialName("has_post_questionnaire") val hasPostQuestionnaire: Boolean = false,
    val confirmation: String = "",
)

/** Opción de «¿Con quién es la sesión?» para empezar sin protocolo. */
@Serializable
data class AudienceDto(val value: String, val confirmation: String = "")

/** Con protocolo va `protocol_id`; sin protocolo, `session_type`. */
@Serializable
data class StartSessionRequestDto(
    @SerialName("protocol_id") val protocolId: String? = null,
    @SerialName("session_type") val sessionType: String? = null,
    @SerialName("authorization_confirmed") val authorizationConfirmed: Boolean,
)

@Serializable
data class StartSessionDto(
    @SerialName("session_id") val sessionId: String,
    val code: String,
    val phase: String,
    val audience: String,
    @SerialName("has_protocol") val hasProtocol: Boolean = true,
    val reminder: String? = null,
)

/** `GET .../next`: siempre trae todos los campos; los que no aplican, en `null`. */
@Serializable
data class SessionStepDto(
    @SerialName("session_id") val sessionId: String,
    val code: String? = null,
    val phase: String,
    @SerialName("stop_reason") val stopReason: String? = null,
    @SerialName("send_frames") val sendFrames: Boolean = false,
    @SerialName("frame_interval_ms") val frameIntervalMs: Long? = null,
    @SerialName("capture_status") val captureStatus: List<String> = emptyList(),
    val questionnaire: QuestionnaireDto? = null,
    val calibration: CalibrationDto? = null,
    val stimulus: StimulusDto? = null,
    @SerialName("step_count") val stepCount: Int? = null,
)

@Serializable
data class QuestionnaireDto(
    val moment: String,
    val id: String,
    val version: Int = 1,
    val title: String = "",
    val instructions: String? = null,
    val answered: Boolean = false,
    val questions: List<QuestionDto> = emptyList(),
)

@Serializable
data class QuestionDto(
    val id: String,
    val kind: String,
    val text: String,
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    @SerialName("scale_points") val scalePoints: Int? = null,
    @SerialName("min_label") val minLabel: String? = null,
    @SerialName("max_label") val maxLabel: String? = null,
)

@Serializable
data class CalibrationDto(
    val state: String,
    val progress: Float = 0f,
    val notice: String? = null,
)

/** `title` existe en la API, pero no se muestra a la persona evaluada ni se guarda. */
@Serializable
data class StimulusDto(
    @SerialName("step_index") val stepIndex: Int,
    val kind: String? = null,
    @SerialName("exposure_seconds") val exposureSeconds: Double? = null,
    @SerialName("remaining_seconds") val remainingSeconds: Double? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("abstract_notice") val abstractNotice: String? = null,
    @SerialName("records_verbal_response") val recordsVerbalResponse: Boolean = false,
)

@Serializable
data class QuestionnaireAnswersDto(
    val answers: JsonObject,
    @SerialName("entry_mode") val entryMode: String,
)

@Serializable
data class PhaseDto(val phase: String? = null)

@Serializable
data class FrameResultDto(
    val phase: String? = null,
    @SerialName("capture_status") val captureStatus: List<String> = emptyList(),
)

@Serializable
data class StopRequestDto(val reason: String)

/** `detail` es un objeto `{code, message}` o, en errores de esquema de FastAPI, una lista. */
@Serializable
data class ErrorEnvelopeDto(val detail: JsonElement? = null)
