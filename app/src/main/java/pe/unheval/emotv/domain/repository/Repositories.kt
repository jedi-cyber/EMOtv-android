package pe.unheval.emotv.domain.repository

import kotlinx.coroutines.flow.StateFlow
import pe.unheval.emotv.domain.model.AuthUser
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.ServerStatus

interface StatusRepository {
    suspend fun fetchStatus(): Outcome<ServerStatus>
}

interface AuthRepository {
    /** Cuenta con sesión iniciada; vive solo en memoria. */
    val currentUser: StateFlow<AuthUser?>

    suspend fun login(email: String, password: String): Outcome<AuthUser>

    /**
     * Renueva la sesión guardada. Devuelve `null` si no hay sesión o si el servidor
     * la rechazó (en ese caso, se borra).
     */
    suspend fun restoreSession(): Outcome<AuthUser?>

    /** Cierra la sesión en el servidor (si se puede) y borra todo dato local. */
    suspend fun logout()
}

interface PinRepository {
    suspend fun hasPin(): Boolean
    suspend fun savePin(pin: String)
    suspend fun verifyPin(pin: String): Boolean
    suspend fun clear()
}
