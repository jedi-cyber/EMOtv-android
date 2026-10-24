package pe.unheval.emotv.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

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
}
