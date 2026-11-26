package pe.unheval.emotv.ui.reports

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
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.ReportStatus
import pe.unheval.emotv.domain.model.SessionSummary
import pe.unheval.emotv.domain.repository.ReportRepository
import pe.unheval.emotv.domain.repository.SessionRepository
import pe.unheval.emotv.domain.usecase.LogoutUseCase
import pe.unheval.emotv.ui.common.AccountViewModel
import pe.unheval.emotv.ui.common.Loadable
import pe.unheval.emotv.ui.common.ScreenError

/** Las sesiones que tienen reporte, agrupadas por el estado de su reporte vigente. */
data class ReportGroup(val status: ReportStatus, val sessions: List<SessionSummary>)

data class ReportsUiState(
    val data: Loadable<List<ReportGroup>> = Loadable(),
    /** Sesión cuyo reporte se está buscando para abrirlo. */
    val opening: String? = null,
    val actionError: ScreenError? = null,
    /** Reporte que hay que abrir. */
    val openReport: String? = null,
)

/**
 * Módulo «Reportes»: todos los reportes que la cuenta puede ver, primero los que
 * piden algo (con observaciones y en borrador) y al final los aprobados. Usa la lista
 * de sesiones (`report_status`) y, al tocar una, el reporte vigente de esa sesión.
 */
class ReportsViewModel(
    private val sessions: SessionRepository,
    private val reports: ReportRepository,
    logout: LogoutUseCase,
) : AccountViewModel(logout) {

    private val state = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = state.asStateFlow()

    init {
        load()
    }

    override fun refresh() {
        if (state.value.data.busy) return
        state.update { it.copy(data = it.data.reloading(), actionError = null) }
        load()
    }

    fun open(sessionId: String) {
        if (state.value.opening != null) return
        state.update { it.copy(opening = sessionId, actionError = null) }
        viewModelScope.launch {
            when (val result = reports.forSession(sessionId)) {
                is Outcome.Success -> {
                    val report = result.value
                    if (report != null) {
                        state.update { it.copy(opening = null, openReport = report.id) }
                    } else {
                        // Ya no tiene reporte (por ejemplo, la sesión se eliminó): se actualiza la lista.
                        state.update { it.copy(opening = null) }
                        refresh()
                    }
                }
                is Outcome.Failure -> {
                    val error = result.error.shownOrLogout()
                    state.update { it.copy(opening = null, actionError = error) }
                }
            }
        }
    }

    fun onReportOpened() = state.update { it.copy(openReport = null) }

    private fun load() {
        viewModelScope.launch {
            when (val result = sessions.list()) {
                is Outcome.Success -> state.update { it.copy(data = it.data.loaded(groupByStatus(result.value))) }
                is Outcome.Failure -> result.error.shownOrLogout()?.let { error ->
                    state.update { it.copy(data = it.data.failed(error)) }
                }
            }
        }
    }

    companion object {
        /** Primero lo que espera algo de la cuenta; los aprobados al final. */
        val STATUS_ORDER = listOf(
            ReportStatus.ChangesRequested,
            ReportStatus.Draft,
            ReportStatus.Submitted,
            ReportStatus.Approved,
            ReportStatus.Unknown,
        )

        /** Solo las sesiones con reporte; cada grupo conserva el orden de la lista (la más reciente primero). */
        fun groupByStatus(sessions: List<SessionSummary>): List<ReportGroup> {
            val byStatus = sessions.filter { it.reportStatus != null }.groupBy { it.reportStatus!! }
            return STATUS_ORDER.mapNotNull { status -> byStatus[status]?.let { ReportGroup(status, it) } }
        }

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EmotvApplication).container
                ReportsViewModel(container.sessionRepository, container.reportRepository, container.logout)
            }
        }
    }
}
