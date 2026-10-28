package pe.unheval.emotv.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pe.unheval.emotv.EmotvApplication
import pe.unheval.emotv.domain.model.Answer
import pe.unheval.emotv.domain.model.AppError
import pe.unheval.emotv.domain.model.CaptureIssue
import pe.unheval.emotv.domain.model.ErrorCodes
import pe.unheval.emotv.domain.model.GuidedPhase
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.Questionnaire
import pe.unheval.emotv.domain.model.SessionAudience
import pe.unheval.emotv.domain.model.SessionStep
import pe.unheval.emotv.domain.model.StopReason
import pe.unheval.emotv.domain.model.isSessionEnded
import pe.unheval.emotv.domain.model.serverCode
import pe.unheval.emotv.domain.repository.FrameSource
import pe.unheval.emotv.domain.repository.SessionRepository
import pe.unheval.emotv.domain.usecase.VerifyPinUseCase
import pe.unheval.emotv.ui.navigation.LockedSessionRoute

/** Por qué la pantalla de la persona evaluada está cubierta por el panel de quien conduce. */
sealed interface SessionPause {
    /** La persona tocó «Quiero parar». */
    data object ParticipantRequest : SessionPause

    /** Quien conduce quiere salir (gesto atrás o fin de la sesión). */
    data object ExitRequest : SessionPause

    /** Sin conexión con el servidor: no se toman ni se guardan capturas. */
    data object Offline : SessionPause

    /** La sesión no puede seguir (cuenta cerrada, sesión ajena o inexistente). */
    data class Failed(val error: SessionError) : SessionPause
}

sealed interface PinCheckError {
    data object Wrong : PinCheckError

    /** Demasiados intentos: se vuelve a pedir tras [seconds] segundos. */
    data class LockedOut(val seconds: Int) : PinCheckError
}

/** Imagen del estímulo en memoria. [key] identifica el estímulo, no la firma de la URL. */
class StimulusImage(val key: String, val bytes: ByteArray) {
    override fun equals(other: Any?) = other is StimulusImage && other.key == key
    override fun hashCode() = key.hashCode()
}

data class LockedSessionUiState(
    /** Código de la sesión, para anotarlo en el formato firmado. */
    val code: String,
    val audience: SessionAudience,
    val step: SessionStep? = null,
    val stimulusImage: StimulusImage? = null,
    /** Respuestas del cuestionario en curso; se descartan al enviarlas. */
    val answers: Map<String, Answer> = emptyMap(),
    val isSubmitting: Boolean = false,
    val submitError: SessionError? = null,
    val captureIssues: List<CaptureIssue> = emptyList(),
    val pause: SessionPause? = null,
    val pin: String = "",
    val pinError: PinCheckError? = null,
    val isCheckingPin: Boolean = false,
    /** Quien conduce ingresó su PIN y puede continuar o terminar la sesión. */
    val unlocked: Boolean = false,
    val stopReason: StopReason = StopReason.Conductor,
    val isEnding: Boolean = false,
    /** Se salió del modo sesión: liberar la cámara, quitar el bloqueo y volver al dashboard. */
    val exited: Boolean = false,
) {
    val isTerminal: Boolean get() = step?.isTerminal == true

    /** La persona evaluada tiene la pantalla y la sesión avanza. */
    val isRunning: Boolean get() = pause == null && !exited && !isTerminal

    /** Se envían cuadros solo mientras el servidor lo pide y la sesión avanza. */
    val isCapturing: Boolean
        get() = isRunning && step?.sendFrames == true &&
            (step.phase == GuidedPhase.Calibration || step.phase == GuidedPhase.Stimuli || step.phase == GuidedPhase.Live)

    /** Tras el PIN, se puede volver a la sesión si sigue activa y hay conexión. */
    val canResume: Boolean
        get() = unlocked && !isTerminal && (pause == SessionPause.ParticipantRequest || pause == SessionPause.ExitRequest)

    /** Se pueden enviar las respuestas: todas las obligatorias tienen valor. */
    val canSubmitQuestionnaire: Boolean
        get() {
            val questionnaire = step?.questionnaire ?: return false
            if (isSubmitting || !isRunning) return false
            return questionnaire.answered || questionnaire.questions.all { question ->
                !question.required || answers[question.id]?.isEmpty == false
            }
        }
}

/**
 * Modo sesión bloqueado. Sigue la fase que indica `GET .../next`; el servidor lleva el
 * tiempo de cada estímulo y decide cuándo enviar cuadros. Las capturas no se guardan: cada
 * cuadro se envía una vez y se borra, y sin conexión no se toman.
 */
class LockedSessionViewModel(
    private val sessionId: String,
    code: String,
    audience: SessionAudience,
    private val sessions: SessionRepository,
    private val verifyPin: VerifyPinUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(LockedSessionUiState(code = code, audience = audience))
    val uiState: StateFlow<LockedSessionUiState> = state.asStateFlow()

    // Despierta el ciclo para pedir `next` sin esperar al siguiente intervalo.
    private val wake = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var frameSource: FrameSource? = null
    private var jpegQuality = DEFAULT_JPEG_QUALITY
    private var loadingImageKey: String? = null
    private var pinFailures = 0
    private var lockoutJob: Job? = null

    init {
        viewModelScope.launch { followProtocol() }
        viewModelScope.launch { captureFrames() }
    }

    /** La cámara la abre la pantalla, ligada a su ciclo de vida. */
    fun attachFrameSource(source: FrameSource) {
        frameSource = source
    }

    fun detachFrameSource() {
        frameSource = null
    }

    // --- Persona evaluada ----------------------------------------------------------------

    fun answer(questionId: String, answer: Answer?) {
        if (!state.value.isRunning) return
        state.update {
            it.copy(
                answers = if (answer == null) it.answers - questionId else it.answers + (questionId to answer),
                submitError = null,
            )
        }
    }

    fun submitQuestionnaire() {
        val current = state.value
        val questionnaire = current.step?.questionnaire ?: return
        if (!current.canSubmitQuestionnaire) return
        state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            val result = if (questionnaire.answered) {
                sessions.advance(sessionId)
            } else {
                val answers = current.answers.filterValues { !it.isEmpty }
                sessions.submitQuestionnaire(sessionId, questionnaire.moment, answers)
            }
            when (result) {
                is Outcome.Success -> {
                    state.update { it.copy(isSubmitting = false, answers = emptyMap()) }
                    wake.trySend(Unit)
                }
                is Outcome.Failure -> {
                    state.update { it.copy(isSubmitting = false) }
                    when {
                        // Ya se respondió o cambió la fase: se pregunta qué sigue.
                        result.error.serverCode == ErrorCodes.CONFLICT -> wake.trySend(Unit)
                        isFatalOrOffline(result.error) -> onFailure(result.error)
                        else -> state.update { it.copy(submitError = SessionError.from(result.error)) }
                    }
                }
            }
        }
    }

    /** `POST .../calibration`, cuando la persona está lista. */
    fun startCalibration() {
        if (!state.value.isRunning || state.value.isSubmitting) return
        state.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = sessions.startCalibration(sessionId)
            state.update { it.copy(isSubmitting = false) }
            when (result) {
                is Outcome.Success -> wake.trySend(Unit)
                is Outcome.Failure -> if (isFatalOrOffline(result.error)) onFailure(result.error) else wake.trySend(Unit)
            }
        }
    }

    /** «Quiero parar»: deja de capturar de inmediato y pide el PIN de quien conduce. */
    fun requestStop() {
        if (state.value.exited || state.value.pause is SessionPause.Failed) return
        state.update {
            it.copy(
                pause = SessionPause.ParticipantRequest,
                stopReason = StopReason.Participant,
                unlocked = false,
                pin = "",
            )
        }
    }

    // --- Quien conduce ------------------------------------------------------------------

    /** Gesto atrás o «Entregar la tablet» al terminar. */
    fun requestExit() {
        if (state.value.exited || state.value.pause != null) return
        state.update {
            it.copy(pause = SessionPause.ExitRequest, stopReason = StopReason.Conductor, unlocked = false, pin = "")
        }
    }

    /** Volver a la sesión desde la petición de salida, antes de ingresar el PIN. */
    fun dismissExitRequest() {
        val current = state.value
        if (current.pause != SessionPause.ExitRequest || current.unlocked || current.isTerminal) return
        state.update { it.copy(pause = null, pin = "", pinError = null) }
        wake.trySend(Unit)
    }

    fun retryConnection() {
        wake.trySend(Unit)
    }

    fun onPinChange(value: String) {
        if (state.value.pinError is PinCheckError.LockedOut) return
        state.update { it.copy(pin = value.filter { c -> c in '0'..'9' }.take(PIN_MAX_LENGTH), pinError = null) }
    }

    fun submitPin() {
        val current = state.value
        if (current.pause == null || current.unlocked || current.isCheckingPin) return
        if (current.pinError is PinCheckError.LockedOut || current.pin.isEmpty()) return
        state.update { it.copy(isCheckingPin = true) }
        viewModelScope.launch {
            val valid = verifyPin(current.pin)
            if (valid) {
                pinFailures = 0
                state.update { it.copy(isCheckingPin = false, pin = "", pinError = null, unlocked = true) }
            } else {
                pinFailures++
                val error = if (pinFailures >= MAX_PIN_FAILURES) {
                    lockOut()
                    PinCheckError.LockedOut(PIN_LOCKOUT_SECONDS)
                } else {
                    PinCheckError.Wrong
                }
                state.update { it.copy(isCheckingPin = false, pin = "", pinError = error) }
            }
        }
    }

    fun setStopReason(reason: StopReason) {
        if (!state.value.unlocked) return
        state.update { it.copy(stopReason = reason) }
    }

    /** Devuelve la tablet a la persona evaluada. */
    fun resume() {
        if (!state.value.canResume) return
        state.update { it.copy(pause = null, unlocked = false, pin = "", pinError = null) }
        wake.trySend(Unit)
    }

    /**
     * Sale del modo sesión con el PIN ya ingresado. Si la sesión sigue activa, la interrumpe
     * con el motivo elegido; si eso falla (sin conexión), se puede cerrar desde la web.
     *
     * Una sesión sin protocolo (`live`) que termina quien conduce se cierra con `finish`, que
     * la conserva, y queda en pantalla con su código antes de volver al inicio.
     */
    fun endSession() {
        val current = state.value
        if (!current.unlocked || current.isEnding || current.exited) return
        state.update { it.copy(isEnding = true) }
        viewModelScope.launch {
            val active = !current.isTerminal && current.pause !is SessionPause.Failed
            if (active && current.step?.phase == GuidedPhase.Live && current.stopReason == StopReason.Conductor) {
                if (finishLive()) return@launch
            } else if (active) {
                sessions.stop(sessionId, current.stopReason)
            }
            frameSource = null
            // Se descartan las respuestas, la imagen del estímulo y todo lo demás en memoria.
            state.value = LockedSessionUiState(code = current.code, audience = current.audience, exited = true)
            wake.trySend(Unit)
        }
    }

    /**
     * `POST .../finish`. Devuelve `true` si la sesión quedó terminada y en pantalla; si falla,
     * se sale igual y la sesión se puede cerrar desde la web.
     */
    private suspend fun finishLive(): Boolean {
        val result = sessions.finish(sessionId)
        val finished = result is Outcome.Success || result.let { it is Outcome.Failure && it.error.serverCode == ErrorCodes.CONFLICT }
        if (!finished) return false
        frameSource = null
        state.update {
            it.copy(
                step = it.step?.copy(phase = GuidedPhase.Finished, sendFrames = false, captureIssues = emptyList()),
                pause = SessionPause.ExitRequest,
                captureIssues = emptyList(),
                isEnding = false,
            )
        }
        return true
    }

    // --- Ciclo de la sesión ---------------------------------------------------------------

    private suspend fun followProtocol() {
        while (true) {
            val current = state.value
            when {
                current.exited -> return
                current.pause == SessionPause.Offline -> {
                    fetchNext()
                    waitForWake(OFFLINE_RETRY_MS)
                }
                current.isRunning -> {
                    fetchNext()
                    waitForWake(pollDelay(state.value.step))
                }
                // En pausa o terminada: se espera a que quien conduce decida.
                else -> wake.receive()
            }
        }
    }

    private suspend fun waitForWake(millis: Long) {
        withTimeoutOrNull(millis) { wake.receive() }
    }

    private suspend fun fetchNext() {
        when (val result = sessions.next(sessionId)) {
            is Outcome.Success -> onStep(result.value)
            is Outcome.Failure -> onFailure(result.error)
        }
    }

    private fun onStep(step: SessionStep) {
        state.update { current ->
            val sameQuestionnaire = current.step?.questionnaire?.identity() == step.questionnaire?.identity()
            val pause = when {
                current.pause != SessionPause.Offline -> current.pause
                // Volvió la conexión: si quien conduce ya desbloqueó, decide él o ella.
                current.unlocked -> SessionPause.ExitRequest
                else -> null
            }
            current.copy(
                step = step,
                pause = pause,
                answers = if (sameQuestionnaire) current.answers else emptyMap(),
                submitError = if (sameQuestionnaire) current.submitError else null,
                captureIssues = if (step.sendFrames) step.captureIssues else emptyList(),
                stimulusImage = current.stimulusImage?.takeIf { it.key == step.imageKey() },
            )
        }
        loadStimulusImage(step)
    }

    private fun loadStimulusImage(step: SessionStep) {
        val url = step.stimulus?.imageUrl ?: return
        val key = step.imageKey() ?: return
        if (state.value.stimulusImage?.key == key || loadingImageKey == key) return
        loadingImageKey = key
        viewModelScope.launch {
            val result = sessions.stimulusImage(url)
            loadingImageKey = null
            when (result) {
                is Outcome.Success ->
                    if (state.value.step?.imageKey() == key) {
                        state.update { it.copy(stimulusImage = StimulusImage(key, result.value)) }
                    }
                is Outcome.Failure ->
                    // La URL vence a los 120 segundos: `next` entrega una nueva.
                    if (result.error.serverCode == ErrorCodes.INVALID_OR_EXPIRED_URL) {
                        wake.trySend(Unit)
                    } else if (isFatalOrOffline(result.error)) {
                        onFailure(result.error)
                    }
            }
        }
    }

    private suspend fun captureFrames() {
        while (true) {
            val current = state.value
            if (current.exited) return
            val source = frameSource
            val step = current.step
            if (source == null || step == null || !current.isCapturing) {
                delay(FRAME_IDLE_MS)
                continue
            }
            sendOneFrame(source)
            delay(step.frameIntervalMs?.coerceAtLeast(MIN_FRAME_INTERVAL_MS) ?: DEFAULT_FRAME_INTERVAL_MS)
        }
    }

    private suspend fun sendOneFrame(source: FrameSource) {
        val jpeg = source.capture(jpegQuality) ?: return
        // Si se pausó mientras la cámara entregaba el cuadro, se descarta sin enviarlo.
        if (!state.value.isCapturing) {
            jpeg.fill(0)
            return
        }
        val phaseBefore = state.value.step?.phase
        when (val result = sessions.sendFrame(sessionId, jpeg)) {
            is Outcome.Success -> {
                state.update { it.copy(captureIssues = result.value.captureIssues) }
                if (result.value.phase != phaseBefore) wake.trySend(Unit)
            }
            is Outcome.Failure -> when (result.error.serverCode) {
                // El servidor pide un cuadro más liviano: se baja la calidad del siguiente.
                ErrorCodes.TOO_LARGE ->
                    jpegQuality = (jpegQuality - JPEG_QUALITY_STEP).coerceAtLeast(MIN_JPEG_QUALITY)
                // La sesión cambió de fase o terminó.
                ErrorCodes.CONFLICT -> wake.trySend(Unit)
                // Cuadro ilegible: se envía otro en el siguiente intervalo.
                ErrorCodes.INVALID -> Unit
                else -> if (isFatalOrOffline(result.error)) onFailure(result.error)
            }
        }
    }

    private fun onFailure(error: AppError) {
        when {
            error == AppError.Network -> state.update {
                // Una pausa pedida por alguien se mantiene; si no, se avisa a quien conduce.
                if (it.pause == null) it.copy(pause = SessionPause.Offline, captureIssues = emptyList()) else it
            }
            isFatal(error) -> state.update {
                it.copy(pause = SessionPause.Failed(SessionError.from(error)), unlocked = false, pin = "")
            }
            // Otros errores son pasajeros: se vuelve a consultar en el siguiente intervalo.
            else -> Unit
        }
    }

    private fun isFatal(error: AppError): Boolean =
        error.isSessionEnded() ||
            error.serverCode == ErrorCodes.FORBIDDEN ||
            error.serverCode == ErrorCodes.NOT_FOUND

    private fun isFatalOrOffline(error: AppError) = error == AppError.Network || isFatal(error)

    private fun lockOut() {
        lockoutJob?.cancel()
        lockoutJob = viewModelScope.launch {
            delay(PIN_LOCKOUT_SECONDS * 1_000L)
            pinFailures = 0
            state.update { if (it.pinError is PinCheckError.LockedOut) it.copy(pinError = null) else it }
        }
    }

    private fun pollDelay(step: SessionStep?): Long {
        val remaining = step?.stimulus?.remainingSeconds
        return when {
            step?.phase == GuidedPhase.PreQuestionnaire || step?.phase == GuidedPhase.PostQuestionnaire ->
                QUESTIONNAIRE_POLL_MS
            remaining != null && remaining > 0 && remaining * 1_000 < POLL_MS ->
                (remaining * 1_000).toLong() + REMAINING_MARGIN_MS
            else -> POLL_MS
        }
    }

    companion object {
        const val POLL_MS = 1_000L
        const val QUESTIONNAIRE_POLL_MS = 3_000L
        const val OFFLINE_RETRY_MS = 3_000L
        const val FRAME_IDLE_MS = 250L
        const val DEFAULT_FRAME_INTERVAL_MS = 500L
        const val MIN_FRAME_INTERVAL_MS = 200L
        private const val REMAINING_MARGIN_MS = 50L
        const val DEFAULT_JPEG_QUALITY = 80
        const val MIN_JPEG_QUALITY = 40
        const val JPEG_QUALITY_STEP = 15
        const val MAX_PIN_FAILURES = 5
        const val PIN_LOCKOUT_SECONDS = 30
        private const val PIN_MAX_LENGTH = 6

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EmotvApplication).container
                val route = createSavedStateHandle().toRoute<LockedSessionRoute>()
                LockedSessionViewModel(
                    sessionId = route.sessionId,
                    code = route.code,
                    audience = SessionAudience.fromApi(route.audience),
                    sessions = container.sessionRepository,
                    verifyPin = container.verifyPin,
                )
            }
        }
    }
}

private fun Questionnaire.identity() = moment to id

/** Clave del estímulo sin la firma: la URL cambia en cada `next`, la imagen no. */
private fun SessionStep.imageKey(): String? {
    val stimulus = stimulus ?: return null
    val url = stimulus.imageUrl ?: return null
    return "${stimulus.stepIndex}:${url.substringBefore('?')}"
}
