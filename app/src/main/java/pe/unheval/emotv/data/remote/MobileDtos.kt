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

/** `detail` es un objeto `{code, message}` o, en errores de esquema de FastAPI, una lista. */
@Serializable
data class ErrorEnvelopeDto(val detail: JsonElement? = null)
