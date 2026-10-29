package pe.unheval.emotv.domain.repository

import kotlinx.coroutines.flow.StateFlow
import pe.unheval.emotv.domain.model.AuthUser
import pe.unheval.emotv.domain.model.DashboardSummary
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.PendingReview
import pe.unheval.emotv.domain.model.Report
import pe.unheval.emotv.domain.model.ReportSection
import pe.unheval.emotv.domain.model.ServerStatus
import pe.unheval.emotv.domain.model.SessionDetail
import pe.unheval.emotv.domain.model.SessionStatistics
import pe.unheval.emotv.domain.model.SessionSummary
import pe.unheval.emotv.domain.model.StatisticsScope
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

/*
 * Ningún dato de las sesiones se guarda en disco: las respuestas llevan
 * `Cache-Control: no-store` y viven en memoria mientras se muestran.
 */

interface SummaryRepository {
    /** Resumen del día [date] según el desfase horario del dispositivo. */
    suspend fun fetchSummary(date: LocalDate, utcOffsetMinutes: Int): Outcome<DashboardSummary>
}

/** Solo consulta: las sesiones se conducen en la web. */
interface SessionRepository {
    /** Las sesiones que la cuenta puede ver, de la más reciente a la más antigua. */
    suspend fun list(): Outcome<List<SessionSummary>>

    suspend fun detail(sessionId: String): Outcome<SessionDetail>
}

/** Cada acción devuelve el reporte actualizado, con lo que la cuenta puede hacer ahora. */
interface ReportRepository {
    /** Reporte vigente de la sesión, o `null` si todavía no tiene. */
    suspend fun forSession(sessionId: String): Outcome<Report?>

    suspend fun create(sessionId: String): Outcome<Report>

    suspend fun get(reportId: String): Outcome<Report>

    suspend fun edit(reportId: String, narrative: String): Outcome<Report>

    suspend fun submit(reportId: String): Outcome<Report>

    suspend fun comment(reportId: String, section: ReportSection, text: String): Outcome<Report>

    suspend fun returnWithComments(reportId: String): Outcome<Report>

    suspend fun approve(reportId: String): Outcome<Report>

    /** Del Docente: reportes de su grupo que esperan su revisión. */
    suspend fun pendingReviews(): Outcome<List<PendingReview>>
}

interface StatisticsRepository {
    suspend fun fetch(scope: StatisticsScope): Outcome<SessionStatistics>
}
