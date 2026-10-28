package pe.unheval.emotv.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/** Endpoints de docs/mobile-api.md. Las rutas son relativas a `BuildConfig.API_BASE_URL`. */
interface EmotvApi {
    @GET("mobile/status")
    suspend fun status(): StatusDto

    @POST("mobile/auth/login")
    suspend fun login(@Body body: LoginRequestDto): TokenResponseDto

    @POST("mobile/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequestDto): TokenResponseDto

    @POST("mobile/auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("mobile/summary")
    suspend fun summary(
        @Query("date") date: String,
        @Query("utc_offset_minutes") utcOffsetMinutes: Int,
    ): SummaryDto

    @POST("mobile/sessions")
    suspend fun startSession(@Body body: StartSessionRequestDto): StartSessionDto

    @GET("mobile/sessions/{id}/next")
    suspend fun next(@Path("id") sessionId: String): SessionStepDto

    /** [moment] es `pre` o `post`. */
    @POST("mobile/sessions/{id}/questionnaires/{moment}")
    suspend fun submitQuestionnaire(
        @Path("id") sessionId: String,
        @Path("moment") moment: String,
        @Body body: QuestionnaireAnswersDto,
    ): PhaseDto

    @POST("mobile/sessions/{id}/advance")
    suspend fun advance(@Path("id") sessionId: String): PhaseDto

    @POST("mobile/sessions/{id}/calibration")
    suspend fun startCalibration(@Path("id") sessionId: String): PhaseDto

    /** Cuerpo `image/jpeg` de hasta 1 000 000 bytes. */
    @POST("mobile/sessions/{id}/frames")
    suspend fun sendFrame(@Path("id") sessionId: String, @Body jpeg: RequestBody): FrameResultDto

    /** Termina una sesión sin protocolo (fase `live`). */
    @POST("mobile/sessions/{id}/finish")
    suspend fun finish(@Path("id") sessionId: String): PhaseDto

    @POST("mobile/sessions/{id}/stop")
    suspend fun stop(@Path("id") sessionId: String, @Body body: StopRequestDto): PhaseDto

    /** URL firmada que entrega `next`; empieza con `/mobile/...` y se resuelve contra el servidor. */
    @Streaming
    @GET
    suspend fun stimulusImage(@Url imageUrl: String): ResponseBody
}
