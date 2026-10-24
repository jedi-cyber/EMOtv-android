package pe.unheval.emotv.domain.repository

import kotlinx.coroutines.flow.StateFlow
import pe.unheval.emotv.domain.model.AuthUser
import pe.unheval.emotv.domain.model.DashboardSummary
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.ServerStatus
import java.time.LocalDate

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

    /**
     * Renueva el token de acceso después de que el servidor rechazó [rejectedToken]. Si
     * otra llamada ya lo renovó, no vuelve a renovar: el token de renovación cambia en cada
     * uso y reutilizarlo revoca la sesión completa. Si el servidor rechaza la renovación,
     * borra la sesión local y devuelve el error `401 invalid_refresh_token`.
     */
    suspend fun renewAccess(rejectedToken: String?): Outcome<Unit>

    /** Token de acceso en memoria, para que [renewAccess] sepa cuál fue rechazado. */
    fun currentAccessToken(): String?

    /** Cierra la sesión en el servidor (si se puede) y borra todo dato local. */
    suspend fun logout()
}

interface SummaryRepository {
    /** Resumen del día [date] según el desfase horario del dispositivo. No se guarda en disco. */
    suspend fun fetchSummary(date: LocalDate, utcOffsetMinutes: Int): Outcome<DashboardSummary>
}

interface PinRepository {
    suspend fun hasPin(): Boolean
    suspend fun savePin(pin: String)
    suspend fun verifyPin(pin: String): Boolean
    suspend fun clear()
}
