package pe.unheval.emotv.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.unheval.emotv.EmotvApplication
import pe.unheval.emotv.domain.model.ConfirmationItem
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.ParticipantKind
import pe.unheval.emotv.domain.model.SessionProtocol
import pe.unheval.emotv.domain.model.StartedSession
import pe.unheval.emotv.domain.model.StopReason
import pe.unheval.emotv.domain.repository.SessionRepository
import pe.unheval.emotv.ui.navigation.SessionSetupRoute

/** Datos para abrir la pantalla bloqueada. */
data class LockedSessionArgs(val sessionId: String, val participantKind: ParticipantKind)

data class SessionSetupUiState(
    val participantCode: String,
    val isLoadingProtocols: Boolean = true,
    val protocols: List<SessionProtocol> = emptyList(),
    val selectedProtocolId: String? = null,
    val isStarting: Boolean = false,
    /** Sesión creada en el servidor, a la espera de la confirmación previa. */
    val started: StartedSession? = null,
    val checked: Set<ConfirmationItem> = emptySet(),
    /** La vista previa de la cámara frontal está funcionando. */
    val cameraReady: Boolean = false,
    /** Quien conduce revisó la iluminación y el encuadre en la vista previa. */
    val lightingChecked: Boolean = false,
    val isConfirming: Boolean = false,
    val isCancelling: Boolean = false,
    val error: SessionError? = null,
    /** Confirmada: pasar al modo sesión bloqueado. */
    val locked: LockedSessionArgs? = null,
    /** Se volvió sin iniciar: regresar al dashboard. */
    val closed: Boolean = false,
) {
    val selectedProtocol: SessionProtocol? get() = protocols.firstOrNull { it.id == selectedProtocolId }

    val missingConfirmations: List<ConfirmationItem>
        get() = started?.confirmation?.required.orEmpty().filterNot { it in checked }

    /** Todo lo que se exige antes de entregar la tablet. */
    val canHandOver: Boolean
        get() = started != null && missingConfirmations.isEmpty() && cameraReady && lightingChecked &&
            !isConfirming && !isCancelling
}

/**
 * Preparación de la sesión: quien conduce elige el protocolo, confirma la autorización y
 * revisa la iluminación. Las reglas (autorizaciones, protocolos aprobados) las impone el
 * servidor; aquí solo se reflejan.
 */
class SessionSetupViewModel(
    private val participantId: String,
    participantCode: String,
    private val sessions: SessionRepository,
) : ViewModel() {

    private val state = MutableStateFlow(SessionSetupUiState(participantCode = participantCode))
    val uiState: StateFlow<SessionSetupUiState> = state.asStateFlow()

    init {
        loadProtocols()
    }

    fun retryProtocols() {
        if (state.value.isLoadingProtocols) return
        state.update { it.copy(isLoadingProtocols = true, error = null) }
        loadProtocols()
    }

    fun selectProtocol(id: String) {
        val current = state.value
        if (current.started != null || current.protocols.none { it.id == id }) return
        state.update { it.copy(selectedProtocolId = id, error = null) }
    }

    /** `POST /mobile/sessions`: crea la sesión y trae el recordatorio y las casillas. */
    fun startSession() {
        val current = state.value
        val protocol = current.selectedProtocol ?: return
        if (current.isStarting || current.started != null) return
        state.update { it.copy(isStarting = true, error = null) }
        viewModelScope.launch {
            when (val result = sessions.start(participantId, protocol.id)) {
                is Outcome.Success -> state.update { it.copy(isStarting = false, started = result.value) }
                is Outcome.Failure -> state.update {
                    it.copy(isStarting = false, error = SessionError.from(result.error))
                }
            }
        }
    }

    fun setChecked(item: ConfirmationItem, checked: Boolean) {
        if (state.value.started == null) return
        state.update { it.copy(checked = if (checked) it.checked + item else it.checked - item) }
    }

    fun onCameraReady(ready: Boolean) {
        state.update { it.copy(cameraReady = ready, lightingChecked = it.lightingChecked && ready) }
    }

    fun setLightingChecked(checked: Boolean) {
        if (!state.value.cameraReady) return
        state.update { it.copy(lightingChecked = checked) }
    }

    /** `POST .../confirm` y entrega de la tablet. No avanza sin todas las confirmaciones. */
    fun handOver() {
        val current = state.value
        val started = current.started ?: return
        if (!current.canHandOver) return
        state.update { it.copy(isConfirming = true, error = null) }
        viewModelScope.launch {
            when (val result = sessions.confirm(started.sessionId, current.checked)) {
                is Outcome.Success -> state.update {
                    it.copy(
                        isConfirming = false,
                        locked = LockedSessionArgs(started.sessionId, started.participantKind),
                    )
                }
                is Outcome.Failure -> state.update {
                    it.copy(isConfirming = false, error = SessionError.from(result.error))
                }
            }
        }
    }

    /**
     * Volver sin iniciar. Si ya se creó la sesión, se interrumpe para que no quede en curso;
     * si no hay conexión, el servidor la conserva y se puede cerrar desde la web.
     */
    fun cancel() {
        val current = state.value
        if (current.isCancelling || current.isConfirming || current.closed) return
        val started = current.started
        if (started == null) {
            state.update { it.copy(closed = true) }
            return
        }
        state.update { it.copy(isCancelling = true) }
        viewModelScope.launch {
            sessions.stop(started.sessionId, StopReason.Conductor)
            state.update { it.copy(isCancelling = false, closed = true) }
        }
    }

    fun onNavigated() = state.update { it.copy(locked = null) }

    private fun loadProtocols() {
        viewModelScope.launch {
            when (val result = sessions.protocols(participantId)) {
                is Outcome.Success -> state.update { current ->
                    val protocols = result.value
                    current.copy(
                        isLoadingProtocols = false,
                        protocols = protocols,
                        // Con un único protocolo, queda elegido.
                        selectedProtocolId = current.selectedProtocolId
                            ?.takeIf { id -> protocols.any { it.id == id } }
                            ?: protocols.singleOrNull()?.id,
                    )
                }
                is Outcome.Failure -> state.update {
                    it.copy(isLoadingProtocols = false, error = SessionError.from(result.error))
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EmotvApplication).container
                val route = createSavedStateHandle().toRoute<SessionSetupRoute>()
                SessionSetupViewModel(route.participantId, route.participantCode, container.sessionRepository)
            }
        }
    }
}
