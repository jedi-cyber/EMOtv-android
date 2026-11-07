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
import pe.unheval.emotv.domain.model.GameAction
import pe.unheval.emotv.domain.model.GameType
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
        /** Una acción del juego va en camino: no se envía otra hasta saber qué pasó. */
        val gameSending: Boolean = false,
        /** La última acción del juego no llegó: se puede tocar otra vez. */
        val gameFailed: Boolean = false,
    ) : ParticipationUiState

    data object Ended : ParticipationUiState
}

/** La imagen de la ronda (un estímulo de la sesión), solo en memoria y solo la de ahora. */
class RoundImage(val exposure: String, val bytes: ByteArray)

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

    private val imageState = MutableStateFlow<RoundImage?>(null)
    /** La imagen del juego en curso; se borra al cambiar de ronda, al terminar y al salir. */
    val roundImage: StateFlow<RoundImage?> = imageState.asStateFlow()
    private var loadingExposure: String? = null
    private val optionImagesState = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    /** Al buscar la misma emoción: la imagen de cada cara de la ronda, por su clave. */
    val optionImages: StateFlow<Map<String, ByteArray>> = optionImagesState.asStateFlow()

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
            gameSending = previous?.gameSending ?: false,
            gameFailed = (previous?.gameFailed ?: false) && previous?.view?.game?.round == view.game?.round,
        )
        syncRoundImage(view)
    }

    // --- Juego de emparejar --------------------------------------------------------------

    /**
     * Pide las imágenes cuando cambia la ronda y suelta las anteriores: la de la
     * ronda y, al buscar la misma emoción, la de cada cara para elegir.
     */
    private fun syncRoundImage(view: ParticipantView) {
        val game = view.game
        val exposure = view.exposure.takeIf { game?.type?.playable == true }
        if (game == null || exposure == null) {
            discardImage()
            return
        }
        if (imageState.value?.exposure == exposure || loadingExposure == exposure) return
        imageState.value = null
        optionImagesState.value = emptyMap()
        loadingExposure = exposure
        val link = token ?: return
        val optionKeys = if (game.type == GameType.Similar) game.options.map { it.key } else emptyList()
        viewModelScope.launch {
            val result = repository.stimulus(link)
            val faces = optionKeys.mapNotNull { key ->
                (repository.gameOption(link, key) as? Outcome.Success)?.let { key to it.value }
            }.toMap()
            // Si mientras tanto cambió la ronda, estas imágenes ya no sirven.
            if (loadingExposure != exposure) return@launch
            loadingExposure = null
            if (result is Outcome.Success) imageState.value = RoundImage(exposure, result.value)
            optionImagesState.value = faces
        }
    }

    /**
     * Una acción del juego, como en la web. No se envía si otra va en camino ni si
     * no corresponde a lo que se ve. Un `409` o `422` quiere decir que el servidor
     * ya la registró (un reintento tras un corte) o que la ronda cambió: no se
     * repite, se vuelve a leer el estado.
     */
    fun play(action: GameAction, expression: String? = null) {
        val current = active() ?: return
        val game = current.view.game ?: return
        val link = token ?: return
        if (current.gameSending || !game.type.playable) return
        val allowed = when (action) {
            GameAction.Answer -> game.state.isPlaying && game.options.any { it.key == expression && it.available }
            GameAction.Help -> game.canHelp
            GameAction.Next -> game.state.isRevealed
        }
        if (!allowed) return
        state.value = current.copy(gameSending = true, gameFailed = false)
        viewModelScope.launch {
            val result = repository.game(link, action, expression)
            val failure = (result as? Outcome.Failure)?.error
            when {
                failure != null && failure.isGone() -> return@launch end()
                failure == null || failure.isAlreadyRecorded() || failure.isNotApplicable() -> {
                    state.update { (it as? ParticipationUiState.Active)?.copy(gameSending = false) ?: it }
                    refresh()
                }
                else -> state.update {
                    (it as? ParticipationUiState.Active)?.copy(gameSending = false, gameFailed = true) ?: it
                }
            }
        }
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
        discardImage()
    }

    private fun end() {
        polling?.cancel()
        polling = null
        token = null
        round = null
        discardDrawing()
        discardImage()
        state.value = ParticipationUiState.Ended
    }

    private fun discardImage() {
        imageState.value = null
        optionImagesState.value = emptyMap()
        loadingExposure = null
    }

    private fun discardDrawing() {
        board.clear()
        changed()
    }

    override fun onCleared() {
        token = null
        board.clear()
        discardImage()
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

/** La opción ya no se muestra (ya la eligió, o la quitó la ayuda): no se registra otra vez. */
private fun AppError.isNotApplicable(): Boolean = this is AppError.Server && httpStatus == 422
