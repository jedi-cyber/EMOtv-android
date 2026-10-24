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
    @SerialName("participant_sessions") val participantSessions: Boolean = false,
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
 * `GET /mobile/summary`. Los participantes solo traen código y edad: el aplicativo nunca
 * recibe ni muestra su nombre (`ignoreUnknownKeys` descarta cualquier campo adicional).
 */
@Serializable
data class SummaryDto(
    val user: UserDto,
    val date: String,
    @SerialName("today_sessions") val todaySessions: List<SessionDto> = emptyList(),
    val participants: List<ParticipantDto> = emptyList(),
)

@Serializable
data class SessionDto(
    val id: String,
    @SerialName("started_at") val startedAt: String,
    val state: String,
    @SerialName("guided_phase") val guidedPhase: String? = null,
    @SerialName("participant_id") val participantId: String,
    @SerialName("participant_code") val participantCode: String,
    @SerialName("protocol_name") val protocolName: String? = null,
)

@Serializable
data class ParticipantDto(
    val id: String,
    val code: String,
    val kind: String,
    @SerialName("age_years") val ageYears: Int? = null,
    val ready: Boolean = false,
    val missing: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    @SerialName("last_session") val lastSession: SessionDto? = null,
)

@Serializable
data class ProtocolDto(
    val id: String,
    val name: String,
    val version: Int = 1,
    @SerialName("for_children") val forChildren: Boolean = false,
    @SerialName("step_count") val stepCount: Int = 0,
    @SerialName("has_pre_questionnaire") val hasPreQuestionnaire: Boolean = false,
    @SerialName("has_post_questionnaire") val hasPostQuestionnaire: Boolean = false,
)

@Serializable
data class StartSessionRequestDto(
    @SerialName("participant_id") val participantId: String,
    @SerialName("protocol_id") val protocolId: String,
)

@Serializable
data class StartSessionDto(
    @SerialName("session_id") val sessionId: String,
    val phase: String,
    val participant: SessionParticipantDto,
    val confirmation: ConfirmationDto? = null,
)

/** Solo código y tipo: el nombre del participante nunca llega al aplicativo. */
@Serializable
data class SessionParticipantDto(val id: String, val code: String, val kind: String)

@Serializable
data class ConfirmationDto(
    val reminder: String? = null,
    val required: List<String> = emptyList(),
)

@Serializable
data class ConfirmRequestDto(
    @SerialName("commitment_reminder") val commitmentReminder: Boolean,
    @SerialName("authorization_confirmed") val authorizationConfirmed: Boolean,
    @SerialName("guardian_present") val guardianPresent: Boolean,
    @SerialName("child_assent") val childAssent: Boolean,
)

/** `GET .../next`: siempre trae todos los campos; los que no aplican, en `null`. */
@Serializable
data class SessionStepDto(
    @SerialName("session_id") val sessionId: String,
    val phase: String,
    @SerialName("stop_reason") val stopReason: String? = null,
    @SerialName("send_frames") val sendFrames: Boolean = false,
    @SerialName("frame_interval_ms") val frameIntervalMs: Long? = null,
    @SerialName("capture_status") val captureStatus: List<String> = emptyList(),
    val questionnaire: QuestionnaireDto? = null,
    val calibration: CalibrationDto? = null,
    val stimulus: StimulusDto? = null,
    val confirmation: ConfirmationDto? = null,
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

/** `title` existe en la API, pero no se muestra al participante ni se guarda. */
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
