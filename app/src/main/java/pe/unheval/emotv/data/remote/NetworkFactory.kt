package pe.unheval.emotv.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkFactory {
    /**
     * Cliente sin caché de disco (las respuestas llevan `Cache-Control: no-store`) y sin
     * interceptor de registros: ningún token ni respuesta llega a Logcat.
     */
    fun createApi(baseUrl: String, tokenHolder: AccessTokenHolder): EmotvApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenHolder))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(EmotvApi::class.java)
    }

    /**
     * Cliente de la pantalla de la persona: sin [AuthInterceptor], para que el token de
     * la cuenta nunca acompañe al enlace de la persona.
     */
    fun createParticipationApi(baseUrl: String): ParticipationApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ParticipationApi::class.java)
    }
}
