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

/** `detail` es un objeto `{code, message}` o, en errores de esquema de FastAPI, una lista. */
@Serializable
data class ErrorEnvelopeDto(val detail: JsonElement? = null)
