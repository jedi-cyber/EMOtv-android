package pe.unheval.emotv.domain.model

/** Resultado de una operación contra el servidor o el almacenamiento. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

/** Errores que la interfaz sabe mostrar. */
sealed interface AppError {
    /**
     * Error propio de `/mobile`: `{"detail": {"code": ..., "message": ...}}`.
     * [message] viene en español y se puede mostrar tal cual.
     */
    data class Server(
        val httpStatus: Int,
        val code: String?,
        val message: String?,
        val retryAfterSeconds: Long? = null,
    ) : AppError

    /** Sin conexión con el servidor o tiempo de espera agotado. */
    data object Network : AppError

    /** Respuesta que no se pudo interpretar. */
    data object Unexpected : AppError
}

/** Códigos de error de docs/mobile-api.md que el aplicativo trata de forma distinta. */
object ErrorCodes {
    const val INVALID_CREDENTIALS = "invalid_credentials"
    const val INVALID_REFRESH_TOKEN = "invalid_refresh_token"
    const val INVALID_TOKEN = "invalid_token"
    const val UNAUTHORIZED = "unauthorized"
    const val ROLE_NOT_ALLOWED = "role_not_allowed"
    const val PASSWORD_CHANGE_REQUIRED = "password_change_required"
    const val TOO_MANY_ATTEMPTS = "too_many_attempts"
}

/** El token de acceso venció o falta: se puede renovar con el token de renovación. */
fun AppError.isAccessRejected(): Boolean =
    this is AppError.Server && httpStatus == 401 &&
        (code == ErrorCodes.INVALID_TOKEN || code == ErrorCodes.UNAUTHORIZED)

/** La sesión terminó (renovación rechazada o token inválido): hay que volver a iniciar sesión. */
fun AppError.isSessionEnded(): Boolean =
    this is AppError.Server && httpStatus == 401 &&
        (code == ErrorCodes.INVALID_REFRESH_TOKEN || isAccessRejected())
