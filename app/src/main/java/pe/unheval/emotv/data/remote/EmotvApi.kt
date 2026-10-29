package pe.unheval.emotv.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Endpoints de docs/mobile-api.md (revisión 4). Las rutas son relativas a
 * `BuildConfig.API_BASE_URL`. El aplicativo solo consulta y actúa sobre el reporte: no
 * conduce sesiones ni envía imágenes.
 */
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

    @GET("mobile/sessions")
    suspend fun sessions(): List<SessionDto>

    @GET("mobile/sessions/{id}")
    suspend fun sessionDetail(@Path("id") sessionId: String): SessionDetailDto

    /** `404` si la sesión aún no tiene reporte. */
    @GET("mobile/sessions/{id}/report")
    suspend fun sessionReport(@Path("id") sessionId: String): ReportDto

    @POST("mobile/sessions/{id}/report")
    suspend fun createReport(@Path("id") sessionId: String): ReportDto

    @GET("mobile/reports/{id}")
    suspend fun report(@Path("id") reportId: String): ReportDto

    @PUT("mobile/reports/{id}")
    suspend fun editReport(@Path("id") reportId: String, @Body body: NarrativeRequestDto): ReportDto

    @POST("mobile/reports/{id}/submit")
    suspend fun submitReport(@Path("id") reportId: String): ReportDto

    @POST("mobile/reports/{id}/comments")
    suspend fun commentReport(@Path("id") reportId: String, @Body body: CommentRequestDto): ReportDto

    @POST("mobile/reports/{id}/return")
    suspend fun returnReport(@Path("id") reportId: String): ReportDto

    @POST("mobile/reports/{id}/approve")
    suspend fun approveReport(@Path("id") reportId: String): ReportDto

    @GET("mobile/reviews")
    suspend fun reviews(): List<SessionDto>

    /** [scope] es `mine` o `group`. */
    @GET("mobile/statistics")
    suspend fun statistics(@Query("scope") scope: String): StatisticsDto
}
