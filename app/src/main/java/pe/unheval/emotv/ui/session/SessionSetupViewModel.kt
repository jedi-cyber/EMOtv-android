package pe.unheval.emotv.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.unheval.emotv.EmotvApplication
import pe.unheval.emotv.domain.model.AudienceOption
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.SessionAudience
import pe.unheval.emotv.domain.model.SessionPlan
import pe.unheval.emotv.domain.model.SessionProtocol
import pe.unheval.emotv.domain.repository.SessionRepository
import pe.unheval.emotv.domain.usecase.LoadDashboardUseCase

/** Datos para abrir la pantalla bloqueada. */
data class LockedSessionArgs(val sessionId: String, val code: String, val audience: SessionAudience)

/** Lo que eligió quien conduce: un protocolo o ninguno. */
sealed interface SetupChoice {
    data class Protocol(val id: String) : SetupChoice
    data object NoProtocol : SetupChoice
}

data class SessionSetupUiState(
    val isLoadingOptions: Boolean = true,
    val protocols: List<SessionProtocol> = emptyList(),
    val audiences: List<AudienceOption> = emptyList(),
    val choice: SetupChoice? = null,
    /** Sin protocolo: con quién es la sesión. Con protocolo lo decide el protocolo. */
    val audience: SessionAudience? = null,
    /** La única casilla de confirmación. Empieza sin marcar y se desmarca al cambiar la elección. */
    val confirmed: Boolean = false,
    /** La vista previa de la cámara frontal está funcionando. */
    val cameraReady: Boolean = false,
    /** Quien conduce revisó la iluminación y el encuadre en la vista previa. */
    val lightingChecked: Boolean = false,
    val isStarting: Boolean = false,
    val error: SessionError? = null,
    /** Sesión creada: pasar al modo sesión bloqueado. */
    val locked: LockedSessionArgs? = null,
    /** Se volvió sin iniciar: regresar al dashboard. */
    val closed: Boolean = false,
) {
    val selectedProtocol: SessionProtocol?
        get() = (choice as? SetupChoice.Protocol)?.let { c -> protocols.firstOrNull { it.id == c.id } }

    val plan: SessionPlan?
        get() = when (choice) {
            is SetupChoice.Protocol -> selectedProtocol?.let { SessionPlan.WithProtocol(it.id) }
            SetupChoice.NoProtocol -> audience?.let { SessionPlan.WithoutProtocol(it) }
            null -> null
        }

    /** Texto de la casilla, tal como lo envía el servidor para el protocolo o el público. */
    val confirmationText: String?
        get() = when (choice) {
            is SetupChoice.Protocol -> selectedProtocol?.confirmation
            SetupChoice.NoProtocol -> audiences.firstOrNull { it.audience == audience }?.confirmation
            null -> null
        }?.takeIf { it.isNotBlank() }

    /** Todo lo que se exige antes de crear la sesión y entregar la tablet. */
    val canHandOver: Boolean
        get() = plan != null && confirmationText != null && confirmed && cameraReady && lightingChecked &&
            !isStarting && locked == null
}

/**
 * Preparación de la sesión: quien conduce elige un protocolo o ninguno (y entonces con quién
 * es la sesión), revisa la cámara y marca la confirmación. La sesión se crea recién al
 * entregar la tablet. Las reglas las impone el servidor; aquí solo se reflejan.
 */
class SessionSetupViewModel(
    private val loadOptions: LoadDashboardUseCase,
    private val sessions: SessionRepository,
) : ViewModel() {

    private val state = MutableStateFlow(SessionSetupUiState())
    val uiState: StateFlow<SessionSetupUiState> = state.asStateFlow()

    init {
        load()
    }

    fun retry() {
        if (state.value.isLoadingOptions) return
        state.update { it.copy(isLoadingOptions = true, error = null) }
        load()
    }

    fun choose(choice: SetupChoice) {
        val current = state.value
        if (current.isStarting || current.choice == choice) return
        if (choice is SetupChoice.Protocol && current.protocols.none { it.id == choice.id }) return
        state.update {
            it.copy(
                choice = choice,
                audience = if (choice == SetupChoice.NoProtocol) it.audience else null,
                confirmed = false,
                error = null,
            )
        }
    }

    fun chooseAudience(audience: SessionAudience) {
        val current = state.value
        if (current.isStarting || current.choice != SetupChoice.NoProtocol || current.audience == audience) return
        if (current.audiences.none { it.audience == audience }) return
        state.update { it.copy(audience = audience, confirmed = false, error = null) }
    }

    fun setConfirmed(checked: Boolean) {
        if (state.value.confirmationText == null || state.value.isStarting) return
        state.update { it.copy(confirmed = checked) }
    }

    fun onCameraReady(ready: Boolean) {
        state.update { it.copy(cameraReady = ready, lightingChecked = it.lightingChecked && ready) }
    }

    fun setLightingChecked(checked: Boolean) {
        if (!state.value.cameraReady) return
        state.update { it.copy(lightingChecked = checked) }
    }

    /** `POST /mobile/sessions` con la confirmación marcada, y entrega de la tablet. */
    fun handOver() {
        val current = state.value
        val plan = current.plan ?: return
        if (!current.canHandOver) return
        state.update { it.copy(isStarting = true, error = null) }
        viewModelScope.launch {
            when (val result = sessions.start(plan)) {
                is Outcome.Success -> state.update {
                    val started = result.value
                    it.copy(isStarting = false, locked = LockedSessionArgs(started.sessionId, started.code, started.audience))
                }
                is Outcome.Failure -> state.update {
                    it.copy(isStarting = false, error = SessionError.from(result.error))
                }
            }
        }
    }

    /** Volver sin iniciar: todavía no existe ninguna sesión en el servidor. */
    fun cancel() {
        if (state.value.isStarting) return
        state.update { it.copy(closed = true) }
    }

    fun onNavigated() = state.update { it.copy(locked = null) }

    private fun load() {
        viewModelScope.launch {
            when (val result = loadOptions()) {
                is Outcome.Success -> state.update { current ->
                    val protocols = result.value.protocols
                    // Sin protocolos utilizables, queda elegida la sesión sin protocolo.
                    val choice = current.choice.takeIf { choice ->
                        choice == SetupChoice.NoProtocol ||
                            (choice is SetupChoice.Protocol && protocols.any { it.id == choice.id })
                    } ?: SetupChoice.NoProtocol.takeIf { protocols.isEmpty() }
                    current.copy(
                        isLoadingOptions = false,
                        protocols = protocols,
                        audiences = result.value.audiences,
                        choice = choice,
                    )
                }
                is Outcome.Failure -> state.update {
                    it.copy(isLoadingOptions = false, error = SessionError.from(result.error))
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EmotvApplication).container
                SessionSetupViewModel(container.loadDashboard, container.sessionRepository)
            }
        }
    }
}
