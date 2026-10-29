package pe.unheval.emotv.ui.reviews

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
import pe.unheval.emotv.domain.model.PendingReview
import pe.unheval.emotv.domain.repository.ReportRepository
import pe.unheval.emotv.domain.usecase.LogoutUseCase
import pe.unheval.emotv.ui.common.AccountViewModel
import pe.unheval.emotv.ui.common.Loadable

/** Del Docente: reportes de su grupo que esperan su revisión. */
class ReviewsViewModel(
    private val reports: ReportRepository,
    logout: LogoutUseCase,
) : AccountViewModel(logout) {

    private val state = MutableStateFlow(Loadable<List<PendingReview>>())
    val uiState: StateFlow<Loadable<List<PendingReview>>> = state.asStateFlow()

    init {
        load()
    }

    override fun refresh() {
        if (state.value.busy) return
        state.update { it.reloading() }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            when (val result = reports.pendingReviews()) {
                is Outcome.Success -> state.update { it.loaded(result.value) }
                is Outcome.Failure -> result.error.shownOrLogout()?.let { error -> state.update { it.failed(error) } }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EmotvApplication).container
                ReviewsViewModel(container.reportRepository, container.logout)
            }
        }
    }
}
