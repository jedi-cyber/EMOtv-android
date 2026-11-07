package pe.unheval.emotv.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Pantalla de la persona (docs/mobile-api.md, revisión 4.9 y 4.10). Va en un cliente
 * propio, sin el token de la cuenta: el enlace de la persona solo abre
 * `/participant-view/{token}` y nunca se mezcla con las rutas de consulta.
 */
interface ParticipationApi {
    /** Canjea el código de un solo uso que quien conduce pidió en la web. */
    @POST("mobile/participation")
    suspend fun claim(@Body body: ParticipationRequestDto): ParticipationResponseDto

    @GET("participant-view/{token}")
    suspend fun state(@Path("token") token: String): ParticipantStateDto

    /** Solo la emoción elegida: el dibujo no sale del dispositivo. */
    @POST("participant-view/{token}/answer")
    suspend fun answer(@Path("token") token: String, @Body body: AnswerRequestDto): ReceivedDto

    @POST("participant-view/{token}/stop")
    suspend fun stop(@Path("token") token: String): StoppedDto
}

@Serializable
data class ParticipationRequestDto(val code: String)

@Serializable
data class ParticipationResponseDto(
    @SerialName("participant_token") val participantToken: String,
    @SerialName("state_path") val statePath: String? = null,
)

/** Solo lo que se muestra a la persona; el servidor nunca manda expresiones estimadas. */
@Serializable
data class ParticipantStateDto(
    val phase: String,
    val message: String? = null,
    @SerialName("remaining_seconds") val remainingSeconds: Double? = null,
    val activity: ActivityDto? = null,
    val drawing: ParticipantDrawingDto? = null,
)

@Serializable
data class ActivityDto(
    val kind: String,
    val name: String,
    val number: Int? = null,
    val total: Int? = null,
)

@Serializable
data class ParticipantDrawingDto(
    val instruction: String,
    val question: String,
    val options: List<DrawingOptionDto>,
    /** Cambia con cada intento: un intento nuevo empieza con el lienzo vacío. */
    val round: String,
    val finished: Boolean,
    val chosen: String? = null,
    val message: String? = null,
)

@Serializable
data class DrawingOptionDto(val key: String, val label: String)

@Serializable
data class AnswerRequestDto(val expression: String)

@Serializable
data class ReceivedDto(val received: Boolean = true)

@Serializable
data class StoppedDto(val stopped: Boolean = true)
