package pe.unheval.emotv.ui.statistics

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.unheval.emotv.EmotvApplication
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.SessionStatistics
import pe.unheval.emotv.domain.model.StatisticsScope
import pe.unheval.emotv.domain.repository.StatisticsRepository
import pe.unheval.emotv.domain.usecase.LogoutUseCase
import pe.unheval.emotv.domain.usecase.isDocente
import pe.unheval.emotv.ui.common.AccountViewModel
import pe.unheval.emotv.ui.common.Loadable

/**
 * El Docente ve siempre las de su grupo: sus sesiones propias son de demostración y no cuentan,
 * así que «Mis sesiones» le daría cero (contrato 4.4).
 */
data class StatisticsUiState(
    val scope: StatisticsScope = StatisticsScope.Mine,
    val data: Loadable<SessionStatistics> = Loadable(),
)

/** Estadísticas agregadas: cuentan sesiones, no personas. */
class StatisticsViewModel(
    private val statistics: StatisticsRepository,
    isDocente: Boolean,
    logout: LogoutUseCase,
) : AccountViewModel(logout) {

    private val state = MutableStateFlow(
        StatisticsUiState(scope = if (isDocente) StatisticsScope.Group else StatisticsScope.Mine),
    )
    val uiState: StateFlow<StatisticsUiState> = state.asStateFlow()
    private var loading: Job? = null

    init {
        load()
    }

    override fun refresh() {
        if (state.value.data.busy) return
        state.update { it.copy(data = it.data.reloading()) }
        load()
    }

    private fun load() {
        loading?.cancel()
        val scope = state.value.scope
        loading = viewModelScope.launch {
            when (val result = statistics.fetch(scope)) {
                is Outcome.Success -> state.update { it.copy(data = it.data.loaded(result.value)) }
                is Outcome.Failure -> result.error.shownOrLogout()?.let { error ->
                    state.update { it.copy(data = it.data.failed(error)) }
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EmotvApplication).container
                StatisticsViewModel(
                    statistics = container.statisticsRepository,
                    isDocente = container.authRepository.currentUser.value?.isDocente() == true,
                    logout = container.logout,
                )
            }
        }
    }
}
