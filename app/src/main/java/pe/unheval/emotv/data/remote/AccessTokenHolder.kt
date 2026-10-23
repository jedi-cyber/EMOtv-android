package pe.unheval.emotv.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Token de acceso de corta duración. Según docs/mobile-api.md vive solo en memoria;
 * el token de renovación se guarda cifrado (ver `EncryptedRefreshTokenStore`).
 */
class AccessTokenHolder {
    @Volatile
    var accessToken: String? = null
}

/** Agrega `Authorization: Bearer` cuando hay un token de acceso en memoria. */
class AuthInterceptor(private val holder: AccessTokenHolder) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = holder.accessToken
        val request = chain.request()
        if (token == null || request.header("Authorization") != null) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build(),
        )
    }
}
