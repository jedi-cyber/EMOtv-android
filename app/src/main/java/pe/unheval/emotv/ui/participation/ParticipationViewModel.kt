package pe.unheval.emotv.ui.participation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pe.unheval.emotv.EmotvApplication
import pe.unheval.emotv.domain.model.AppError
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.ParticipantView
import pe.unheval.emotv.domain.repository.ParticipationRepository

/** El paso de dibujo, como en la web: dibujar, elegir la emoción y terminado. */
enum class DrawingPhase { Drawing, Choosing, Done }

sealed interface ParticipationUiState {
    data class CodeEntry(
        val code: String = "",
        val sending: Boolean = false,
        val error: AppError? = null,
    ) : ParticipationUiState

    data class Active(
        val view: ParticipantView,
        val phase: DrawingPhase = DrawingPhase.Drawing,
        val sending: Boolean = false,
        val sendFailed: Boolean = false,
        /** No se pudo leer el estado: se sigue intentando con el mismo enlace. */
        val reconnecting: Boolean = false,
        /** «Quiero parar» no llegó al servidor: hay que avisar a quien conduce. */
        val stopFailed: Boolean = false,
    ) : ParticipationUiState

    data object Ended : ParticipationUiState
}

/**
 * Pantalla de la persona en Android. El enlace y el dibujo viven solo aquí, en
 * memoria: sobreviven al giro de la pantalla y a pasar a segundo plano, pero no se
 * guardan en el dispositivo. Política de datos temporales (la misma que la web):
 * el dibujo se borra al terminar el paso (respuesta o tiempo), al empezar otro
 * intento, al terminar la sesión, al salir y cuando se destruye este ViewModel.
 */
class ParticipationViewModel(
    private val repository: ParticipationRepository,
    private val pollIntervalMillis: Long = POLL_INTERVAL_MILLIS,
) : ViewModel() {

    private val state = MutableStateFlow<ParticipationUiState>(ParticipationUiState.CodeEntry())
    val uiState: StateFlow<ParticipationUiState> = state.asStateFlow()

    val board = DrawingBoard()
    private val revisionState = MutableStateFlow(0)
    /** Cambia con cada trazo o herramienta: el lienzo se vuelve a dibujar. */
    val boardRevision: StateFlow<Int> = revisionState.asStateFlow()

    private var token: String? = null
    private var round: String? = null
    private var polling: Job? = null
    private var visible = false

    // --- Código ------------------------------------------------------------------------

    fun onCodeChange(value: String) {
        val current = state.value as? ParticipationUiState.CodeEntry ?: return
        state.value = current.copy(code = value.uppercase().take(MAX_CODE_LENGTH), error = null)
    }

    fun submitCode() {
        val current = state.value as? ParticipationUiState.CodeEntry ?: return
        if (current.sending || current.code.isBlank()) return
        state.value = current.copy(sending = true, error = null)
        viewModelScope.launch {
            when (val result = repository.claim(current.code)) {
                is Outcome.Success -> {
                    token = result.value
                    refresh()
                    if (visible) startPolling()
                }
                is Outcome.Failure -> state.value = current.copy(sending = false, error = result.error)
            }
        }
    }

    // --- Ciclo de vida -------------------------------------------------------------------

    /** La pantalla se ve: lee el estado cada segundo (también sirve para reconectarse). */
    fun onStart() {
        visible = true
        if (token != null) startPolling()
    }

    /** En segundo plano no se consulta al servidor; el dibujo sigue en memoria. */
    fun onStop() {
        visible = false
        polling?.cancel()
        polling = null
    }

    private fun startPolling() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (isActive && token != null) {
                delay(pollIntervalMillis)
                refresh()
            }
        }
    }

    suspend fun refresh() {
        val link = token ?: return
        when (val result = repository.view(link)) {
            is Outcome.Success -> apply(result.value)
            is Outcome.Failure -> when {
                result.error.isGone() -> end()
                else -> state.update { (it as? ParticipationUiState.Active)?.copy(reconnecting = true) ?: it }
            }
        }
    }

    private fun apply(view: ParticipantView) {
        if (view.ended) return end()
        val drawing = view.drawing
        val previous = state.value as? ParticipationUiState.Active
        var phase = previous?.phase ?: DrawingPhase.Drawing
        if (drawing == null) {
            // El paso ya no es un dibujo: no queda nada en memoria.
            if (round != null) discardDrawing()
            round = null
            phase = DrawingPhase.Drawing
        } else {
            if (drawing.round != round) {
                // Otro paso u otro intento («Repetir» lo decide quien conduce): lienzo nuevo.
                discardDrawing()
                round = drawing.round
                phase = DrawingPhase.Drawing
            }
            if (drawing.finished && phase != DrawingPhase.Done) {
                // Respondió (aquí o quien conduce) o terminó el tiempo.
                discardDrawing()
                phase = DrawingPhase.Done
            }
        }
        state.value = ParticipationUiState.Active(
            view = view,
            phase = phase,
            sending = previous?.sending ?: false,
            sendFailed = previous?.sendFailed ?: false,
            stopFailed = previous?.stopFailed ?: false,
        )
    }

    // --- Dibujo --------------------------------------------------------------------------

    private fun changed() = revisionState.update { it + 1 }

    private fun active(): ParticipationUiState.Active? = state.value as? ParticipationUiState.Active

    private fun canDraw(): Boolean = active()?.let { it.phase == DrawingPhase.Drawing && it.view.drawing != null } == true

    fun selectColor(ink: InkColor) { board.selectColor(ink); changed() }
    fun selectSize(size: InkSize) { board.selectSize(size); changed() }
    fun toggleEraser() { board.toggleEraser(); changed() }
    fun undo() { board.undo(); changed() }
    fun clear() { board.clear(); changed() }

    fun strokeStart(pointer: Long, x: Float, y: Float): Boolean =
        canDraw() && board.start(pointer, x, y).also { if (it) changed() }

    fun strokeMove(pointer: Long, x: Float, y: Float) {
        if (board.extend(pointer, x, y)) changed()
    }

    fun strokeEnd(pointer: Long) { board.end(pointer); changed() }
    fun strokeCancel() { board.cancel(); changed() }

    /** «Terminé»: con algo dibujado, se pregunta qué emoción quiso dibujar. */
    fun finishDrawing() {
        val current = active() ?: return
        if (current.phase == DrawingPhase.Drawing && board.hasDrawing) {
            state.value = current.copy(phase = DrawingPhase.Choosing, sendFailed = false)
        }
    }

    fun keepDrawing() {
        val current = active() ?: return
        if (current.phase == DrawingPhase.Choosing && !current.sending) {
            state.value = current.copy(phase = DrawingPhase.Drawing, sendFailed = false)
        }
    }

    /** Envía solo la emoción elegida. Un 409 (ya registrada) cuenta como recibida. */
    fun choose(expression: String) {
        val current = active() ?: return
        val link = token ?: return
        if (current.phase != DrawingPhase.Choosing || current.sending) return
        state.value = current.copy(sending = true, sendFailed = false)
        viewModelScope.launch {
            val result = repository.answer(link, expression)
            val latest = active() ?: return@launch
            when {
                result is Outcome.Success || (result as Outcome.Failure).error.isAlreadyRecorded() -> {
                    discardDrawing()
                    state.value = latest.copy(phase = DrawingPhase.Done, sending = false)
                    refresh()
                }
                result.error.isGone() -> end()
                // Sin conexión u otro error: el dibujo se conserva para volver a intentarlo.
                else -> state.value = latest.copy(sending = false, sendFailed = true)
            }
        }
    }

    /** «Quiero parar»: la sesión termina de inmediato. */
    fun stopSession() {
        val link = token ?: return end()
        viewModelScope.launch {
            val result = repository.stop(link)
            if (result is Outcome.Success || (result as Outcome.Failure).error.isGone()) {
                end()
            } else {
                state.update { (it as? ParticipationUiState.Active)?.copy(stopFailed = true) ?: it }
            }
        }
    }

    /** Salir de la pantalla sin parar la sesión: se olvida el enlace y el dibujo. */
    fun leave() {
        onStop()
        token = null
        discardDrawing()
    }

    private fun end() {
        polling?.cancel()
        polling = null
        token = null
        round = null
        discardDrawing()
        state.value = ParticipationUiState.Ended
    }

    private fun discardDrawing() {
        board.clear()
        changed()
    }

    override fun onCleared() {
        token = null
        board.clear()
    }

    companion object {
        const val POLL_INTERVAL_MILLIS = 1_000L
        const val MAX_CODE_LENGTH = 16

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EmotvApplication).container
                ParticipationViewModel(container.participationRepository)
            }
        }
    }
}

/** El enlace ya no vale: la sesión terminó o se abrió en otra pantalla. */
private fun AppError.isGone(): Boolean = this is AppError.Server && (httpStatus == 404 || httpStatus == 401)

/** El servidor ya tenía la respuesta (por ejemplo, al reintentar tras un corte). */
private fun AppError.isAlreadyRecorded(): Boolean = this is AppError.Server && httpStatus == 409
