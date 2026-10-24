package pe.unheval.emotv.ui.dashboard

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
import pe.unheval.emotv.domain.model.AppError
import pe.unheval.emotv.domain.model.DashboardSummary
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.Participant
import pe.unheval.emotv.domain.model.SessionSummary
import pe.unheval.emotv.domain.model.isSessionEnded
import pe.unheval.emotv.domain.usecase.LoadDashboardUseCase
import pe.unheval.emotv.domain.usecase.LogoutUseCase
import java.time.LocalDate
import java.time.ZoneId

/** Errores del resumen. Los textos los pone la pantalla (strings.xml). */
sealed interface DashboardError {
    /** Mensaje del servidor en español; puede faltar. */
    data class Server(val message: String?) : DashboardError
    data object Network : DashboardError
    data object Unexpected : DashboardError
}

/** Sesiones de hoy agrupadas por hora local, para el gráfico de tendencia. */
data class HourlyActivity(val counts: List<Int>) {
    val total: Int get() = counts.sum()
    val firstHour: Int? get() = counts.indexOfFirst { it > 0 }.takeIf { it >= 0 }
    val lastHour: Int? get() = counts.indexOfLast { it > 0 }.takeIf { it >= 0 }
    val busiestHour: Int? get() = if (total == 0) null else counts.indices.maxBy { counts[it] }

    companion object {
        fun of(sessions: List<SessionSummary>, zone: ZoneId): HourlyActivity {
            val counts = IntArray(24)
            sessions.forEach { counts[it.startedAt.atZone(zone).hour]++ }
            return HourlyActivity(counts.toList())
        }
    }
}

/** Datos del servidor; solo viven en memoria y se descartan al cerrar sesión. */
data class DashboardContent(
    val roleName: String,
    val email: String,
    val date: LocalDate,
    val todaySessions: List<SessionSummary>,
    val participants: List<Participant>,
    val activity: HourlyActivity,
)

/** Si se puede iniciar una sesión y, si no, por qué. */
sealed interface StartAvailability {
    data object NoParticipants : StartAvailability
    data object NothingSelected : StartAvailability
    data class Ready(val participant: Participant) : StartAvailability
    data class Blocked(val participant: Participant) : StartAvailability
}

data class DashboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val content: DashboardContent? = null,
    val error: DashboardError? = null,
    val selectedParticipantId: String? = null,
    val isLoggingOut: Boolean = false,
    /** Sesión cerrada (por la persona o porque venció): volver al inicio de sesión. */
    val loggedOut: Boolean = false,
) {
    val selectedParticipant: Participant?
        get() = content?.participants?.firstOrNull { it.id == selectedParticipantId }

    val startAvailability: StartAvailability
        get() {
            val selected = selectedParticipant
            return when {
                content == null || content.participants.isEmpty() -> StartAvailability.NoParticipants
                selected == null -> StartAvailability.NothingSelected
                selected.ready -> StartAvailability.Ready(selected)
                else -> StartAvailability.Blocked(selected)
            }
        }
}

class DashboardViewModel(
    private val loadDashboard: LoadDashboardUseCase,
    private val logout: LogoutUseCase,
    private val zone: ZoneId,
) : ViewModel() {

    private val state = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = state.asStateFlow()

    init {
        load()
    }

    /** Deslizar hacia abajo o el botón Actualizar. */
    fun refresh() {
        val current = state.value
        if (current.isLoading || current.isRefreshing || current.isLoggingOut) return
        if (current.content == null) {
            state.update { it.copy(isLoading = true, error = null) }
        } else {
            state.update { it.copy(isRefreshing = true) }
        }
        load()
    }

    fun selectParticipant(id: String) {
        if (state.value.content?.participants?.any { it.id == id } == true) {
            state.update { it.copy(selectedParticipantId = id) }
        }
    }

    fun logOut() {
        if (state.value.isLoggingOut || state.value.loggedOut) return
        state.update { it.copy(isLoggingOut = true) }
        viewModelScope.launch { endSession() }
    }

    private fun load() {
        viewModelScope.launch {
            when (val result = loadDashboard()) {
                is Outcome.Success -> state.update { it.withSummary(result.value) }
                is Outcome.Failure ->
                    if (result.error.isSessionEnded()) {
                        endSession()
                    } else {
                        state.update {
                            it.copy(isLoading = false, isRefreshing = false, error = result.error.toUi())
                        }
                    }
            }
        }
    }

    private fun DashboardUiState.withSummary(summary: DashboardSummary): DashboardUiState {
        val content = DashboardContent(
            roleName = summary.user.roleName,
            email = summary.user.email,
            date = summary.date,
            todaySessions = summary.todaySessions.sortedBy { it.startedAt },
            participants = summary.participants,
            activity = HourlyActivity.of(summary.todaySessions, zone),
        )
        // La selección se conserva solo si el participante sigue en la lista.
        val selected = selectedParticipantId?.takeIf { id -> content.participants.any { it.id == id } }
        return copy(
            isLoading = false,
            isRefreshing = false,
            content = content,
            error = null,
            selectedParticipantId = selected,
        )
    }

    /** Cierra la sesión y descarta todo dato del resumen que había en memoria. */
    private suspend fun endSession() {
        logout()
        state.value = DashboardUiState(isLoading = false, loggedOut = true)
    }

    private fun AppError.toUi(): DashboardError = when (this) {
        is AppError.Server -> DashboardError.Server(message)
        AppError.Network -> DashboardError.Network
        AppError.Unexpected -> DashboardError.Unexpected
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EmotvApplication).container
                DashboardViewModel(container.loadDashboard, container.logout, ZoneId.systemDefault())
            }
        }
    }
}
