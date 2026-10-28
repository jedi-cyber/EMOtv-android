package pe.unheval.emotv.domain.repository

import kotlinx.coroutines.flow.StateFlow
import pe.unheval.emotv.domain.model.Answer
import pe.unheval.emotv.domain.model.AuthUser
import pe.unheval.emotv.domain.model.DashboardSummary
import pe.unheval.emotv.domain.model.FrameResult
import pe.unheval.emotv.domain.model.GuidedPhase
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.QuestionnaireMoment
import pe.unheval.emotv.domain.model.ServerStatus
import pe.unheval.emotv.domain.model.SessionPlan
import pe.unheval.emotv.domain.model.SessionStep
import pe.unheval.emotv.domain.model.StartedSession
import pe.unheval.emotv.domain.model.StopReason
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

/**
 * Sesión con o sin protocolo. Nada se guarda en disco: las respuestas, los estímulos y los
 * cuadros viven en memoria mientras se usan.
 */
interface SessionRepository {
    /**
     * Crea la sesión con la confirmación ya marcada (`authorization_confirmed: true`). Solo
     * se llama después de que quien conduce marcó la casilla.
     */
    suspend fun start(plan: SessionPlan): Outcome<StartedSession>

    suspend fun next(sessionId: String): Outcome<SessionStep>

    /** Respuestas de la persona evaluada (`entry_mode: participant`). No se devuelven. */
    suspend fun submitQuestionnaire(
        sessionId: String,
        moment: QuestionnaireMoment,
        answers: Map<String, Answer>,
    ): Outcome<GuidedPhase?>

    suspend fun advance(sessionId: String): Outcome<GuidedPhase?>

    suspend fun startCalibration(sessionId: String): Outcome<GuidedPhase?>

    /**
     * Envía un cuadro JPEG para que el servidor lo analice en memoria y lo descarte.
     * Al terminar, haya salido bien o no, borra el contenido de [jpeg].
     */
    suspend fun sendFrame(sessionId: String, jpeg: ByteArray): Outcome<FrameResult>

    /** Termina una sesión sin protocolo (fase `live`). */
    suspend fun finish(sessionId: String): Outcome<GuidedPhase?>

    suspend fun stop(sessionId: String, reason: StopReason): Outcome<GuidedPhase?>

    /** Imagen del estímulo, en memoria (sin caché de disco). */
    suspend fun stimulusImage(imageUrl: String): Outcome<ByteArray>
}

/**
 * Cámara frontal. Entrega un cuadro JPEG en memoria, o `null` si la cámara aún no tiene
 * uno. Nunca escribe imágenes en el almacenamiento.
 */
fun interface FrameSource {
    suspend fun capture(jpegQuality: Int): ByteArray?
}
