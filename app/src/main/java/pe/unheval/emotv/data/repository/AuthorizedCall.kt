package pe.unheval.emotv.data.repository

import pe.unheval.emotv.data.remote.apiCall
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.isAccessRejected
import pe.unheval.emotv.domain.repository.AuthRepository

/** Si el token de acceso venció, lo renueva una vez y repite la llamada. */
internal suspend fun <T> AuthRepository.authorizedCall(block: suspend () -> T): Outcome<T> {
    val token = currentAccessToken()
    val first = apiCall(block)
    if (first !is Outcome.Failure || !first.error.isAccessRejected()) return first
    return when (val renewed = renewAccess(token)) {
        is Outcome.Failure -> renewed
        is Outcome.Success -> apiCall(block)
    }
}
