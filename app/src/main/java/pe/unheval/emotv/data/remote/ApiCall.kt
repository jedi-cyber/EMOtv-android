package pe.unheval.emotv.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import pe.unheval.emotv.domain.model.AppError
import pe.unheval.emotv.domain.model.Outcome
import retrofit2.HttpException
import java.io.IOException

internal val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** Ejecuta una llamada y traduce las excepciones de red y HTTP a [AppError]. */
internal suspend fun <T> apiCall(block: suspend () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Outcome.Failure(parseHttpError(e))
    } catch (_: IOException) {
        Outcome.Failure(AppError.Network)
    } catch (_: SerializationException) {
        Outcome.Failure(AppError.Unexpected)
    } catch (_: IllegalArgumentException) {
        Outcome.Failure(AppError.Unexpected)
    }

internal fun parseHttpError(e: HttpException): AppError.Server {
    val response = e.response()
    val body = runCatching { response?.errorBody()?.string() }.getOrNull()
    val detail = body?.let {
        runCatching { ApiJson.decodeFromString(ErrorEnvelopeDto.serializer(), it).detail }.getOrNull()
    } as? JsonObject
    return AppError.Server(
        httpStatus = e.code(),
        code = detail?.get("code")?.jsonPrimitive?.contentOrNull,
        message = detail?.get("message")?.jsonPrimitive?.contentOrNull,
        retryAfterSeconds = response?.headers()?.get("Retry-After")?.trim()?.toLongOrNull(),
    )
}
