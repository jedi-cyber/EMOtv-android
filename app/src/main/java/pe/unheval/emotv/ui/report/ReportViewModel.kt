package pe.unheval.emotv.ui.report

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
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
import pe.unheval.emotv.domain.model.Report
import pe.unheval.emotv.domain.model.ReportSection
import pe.unheval.emotv.domain.repository.ReportRepository
import pe.unheval.emotv.domain.usecase.LogoutUseCase
import pe.unheval.emotv.ui.common.AccountViewModel
import pe.unheval.emotv.ui.common.Loadable
import pe.unheval.emotv.ui.common.ScreenError

/** Acción en curso sobre el reporte. */
enum class ReportAction { Save, Submit, Comment, Return, Approve }

data class ReportUiState(
    val data: Loadable<Report> = Loadable(),
    /** Texto que el autor está escribiendo; no se guarda en el dispositivo. */
    val narrative: String = "",
    val commentText: String = "",
    val commentSection: ReportSection = ReportSection.General,
    val running: ReportAction? = null,
    val actionError: ScreenError? = null,
) {
    val hasUnsavedText: Boolean get() = data.content?.let { narrative != it.narrative } ?: false
}

/**
 * Reporte de una sesión. El servidor decide qué puede hacer la cuenta (`can_edit`,
 * `can_review`); cada acción devuelve el reporte actualizado.
 */
class ReportViewModel(
    private val reportId: String,
    private val reports: ReportRepository,
    logout: LogoutUseCase,
) : AccountViewModel(logout) {

    private val state = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = state.asStateFlow()

    init {
        load()
    }

    override fun refresh() {
        val current = state.value
        // No se recarga encima de un texto sin guardar.
        if (current.data.busy || current.running != null || current.hasUnsavedText) return
        state.update { it.copy(data = it.data.reloading()) }
        load()
    }

    fun onNarrativeChange(text: String) = state.update { it.copy(narrative = text) }

    fun onCommentChange(text: String) = state.update { it.copy(commentText = text) }

    fun onSectionChange(section: ReportSection) = state.update { it.copy(commentSection = section) }

    /** Del autor: guarda el texto. Así responde también a las observaciones del Docente. */
    fun save() = run(ReportAction.Save) { reports.edit(reportId, state.value.narrative) }

    /** Del autor: guarda el texto si cambió y lo envía a revisión (o, si es Docente, lo cierra). */
    fun submit() = run(ReportAction.Submit) {
        val saved = if (state.value.hasUnsavedText) reports.edit(reportId, state.value.narrative) else null
        if (saved is Outcome.Failure) saved else reports.submit(reportId)
    }

    /** Del Docente asignado. */
    fun comment() {
        val text = state.value.commentText.trim()
        if (text.isEmpty()) return
        run(ReportAction.Comment) { reports.comment(reportId, state.value.commentSection, text) }
    }

    fun returnWithComments() = run(ReportAction.Return) { reports.returnWithComments(reportId) }

    fun approve() = run(ReportAction.Approve) { reports.approve(reportId) }

    private fun run(action: ReportAction, call: suspend () -> Outcome<Report>) {
        if (state.value.running != null || state.value.data.content == null) return
        state.update { it.copy(running = action, actionError = null) }
        viewModelScope.launch {
            when (val result = call()) {
                is Outcome.Success -> state.update {
                    it.copy(
                        data = it.data.loaded(result.value),
                        narrative = result.value.narrative,
                        commentText = if (action == ReportAction.Comment) "" else it.commentText,
                        running = null,
                    )
                }
                is Outcome.Failure -> {
                    val error = result.error.shownOrLogout()
                    state.update { it.copy(running = null, actionError = error) }
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            when (val result = reports.get(reportId)) {
                is Outcome.Success -> state.update {
                    it.copy(data = it.data.loaded(result.value), narrative = result.value.narrative)
                }
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
                ReportViewModel(
                    reportId = checkNotNull(createSavedStateHandle().get<String>("reportId")),
                    reports = container.reportRepository,
                    logout = container.logout,
                )
            }
        }
    }
}
