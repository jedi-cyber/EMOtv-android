package pe.unheval.emotv.ui.sessions

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.unheval.emotv.EmotvApplication
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.Report
import pe.unheval.emotv.domain.model.SessionDetail
import pe.unheval.emotv.domain.repository.ReportRepository
import pe.unheval.emotv.domain.repository.SessionRepository
import pe.unheval.emotv.domain.usecase.LogoutUseCase
import pe.unheval.emotv.ui.common.AccountViewModel
import pe.unheval.emotv.ui.common.Loadable
import pe.unheval.emotv.ui.common.ScreenError

data class SessionDetailContent(val detail: SessionDetail, val report: Report?)

data class SessionDetailUiState(
    val data: Loadable<SessionDetailContent> = Loadable(),
    val isCreatingReport: Boolean = false,
    val actionError: ScreenError? = null,
    /** Reporte recién creado que hay que abrir. */
    val openReport: String? = null,
)

/** Línea de tiempo, métricas y estado del reporte de una sesión. */
class SessionDetailViewModel(
    private val sessionId: String,
    private val sessions: SessionRepository,
    private val reports: ReportRepository,
    logout: LogoutUseCase,
) : AccountViewModel(logout) {

    private val state = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = state.asStateFlow()

    init {
        load()
    }

    override fun refresh() {
        if (state.value.data.busy) return
        state.update { it.copy(data = it.data.reloading()) }
        load()
    }

    /** Quien condujo la sesión empieza su reporte (borrador) y lo abre. */
    fun createReport() {
        if (state.value.isCreatingReport) return
        state.update { it.copy(isCreatingReport = true, actionError = null) }
        viewModelScope.launch {
            when (val result = reports.create(sessionId)) {
                is Outcome.Success -> state.update { current ->
                    val content = current.data.content?.copy(report = result.value)
                    current.copy(
                        isCreatingReport = false,
                        data = content?.let(current.data::loaded) ?: current.data,
                        openReport = result.value.id,
                    )
                }
                is Outcome.Failure -> {
                    val error = result.error.shownOrLogout()
                    state.update { it.copy(isCreatingReport = false, actionError = error) }
                }
            }
        }
    }

    fun onReportOpened() = state.update { it.copy(openReport = null) }

    private fun load() {
        viewModelScope.launch {
            val detail = async { sessions.detail(sessionId) }
            val report = async { reports.forSession(sessionId) }
            val detailResult = detail.await()
            val reportResult = report.await()
            val failure = (detailResult as? Outcome.Failure) ?: (reportResult as? Outcome.Failure)
            if (failure != null) {
                failure.error.shownOrLogout()?.let { error -> state.update { it.copy(data = it.data.failed(error)) } }
                return@launch
            }
            val content = SessionDetailContent(
                detail = (detailResult as Outcome.Success).value,
                report = (reportResult as Outcome.Success).value,
            )
            state.update { it.copy(data = it.data.loaded(content)) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EmotvApplication).container
                SessionDetailViewModel(
                    sessionId = checkNotNull(createSavedStateHandle().get<String>("sessionId")),
                    sessions = container.sessionRepository,
                    reports = container.reportRepository,
                    logout = container.logout,
                )
            }
        }
    }
}
