package pe.unheval.emotv.ui.session

import pe.unheval.emotv.domain.model.AppError
import pe.unheval.emotv.domain.model.isSessionEnded

/** Errores de la sesión con participante. Los textos los pone la pantalla (strings.xml). */
sealed interface SessionError {
    /** Mensaje del servidor en español; puede faltar. */
    data class Server(val code: String?, val message: String?) : SessionError
    data object Network : SessionError

    /** La sesión de la cuenta venció o se cerró: hay que volver a iniciar sesión. */
    data object AccountSessionEnded : SessionError
    data object Unexpected : SessionError

    companion object {
        fun from(error: AppError): SessionError = when {
            error.isSessionEnded() -> AccountSessionEnded
            error is AppError.Server -> Server(error.code, error.message)
            error == AppError.Network -> Network
            else -> Unexpected
        }
    }
}
