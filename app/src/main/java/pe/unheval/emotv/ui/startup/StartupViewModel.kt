package pe.unheval.emotv.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.unheval.emotv.BuildConfig
import pe.unheval.emotv.EmotvApplication
import pe.unheval.emotv.domain.model.AppError
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.usecase.CheckServerStatusUseCase
import pe.unheval.emotv.domain.usecase.RestoreSessionUseCase
import pe.unheval.emotv.domain.usecase.StartDestination
import pe.unheval.emotv.domain.usecase.StatusCheck

sealed interface StartupUiState {
    data object Loading : StartupUiState
    data object Offline : StartupUiState
    data object ServerError : StartupUiState
    data object LoginUnavailable : StartupUiState
    data class UpdateRequired(val currentVersion: String, val minVersion: String) : StartupUiState
    data class Ready(val destination: StartDestination) : StartupUiState
}

class StartupViewModel(
    private val checkServerStatus: CheckServerStatusUseCase,
    private val restoreSession: RestoreSessionUseCase,
    private val appVersionName: String,
) : ViewModel() {

    private val state = MutableStateFlow<StartupUiState>(StartupUiState.Loading)
    val uiState: StateFlow<StartupUiState> = state.asStateFlow()

    /** La pantalla de inicio del sistema se mantiene solo durante la primera comprobación. */
    var firstCheckDone = false
        private set

    init {
        check()
    }

    fun retry() {
        if (state.value != StartupUiState.Loading) check()
    }

    private fun check() {
        state.value = StartupUiState.Loading
        viewModelScope.launch {
            state.value = when (val status = checkServerStatus()) {
                is StatusCheck.Failed -> status.error.toUiState()
                StatusCheck.LoginUnavailable -> StartupUiState.LoginUnavailable
                is StatusCheck.UpdateRequired ->
                    StartupUiState.UpdateRequired(appVersionName, status.minVersion)
                is StatusCheck.Ready -> when (val session = restoreSession()) {
                    is Outcome.Failure -> session.error.toUiState()
                    is Outcome.Success -> StartupUiState.Ready(session.value)
                }
            }
            firstCheckDone = true
        }
    }

    private fun AppError.toUiState(): StartupUiState =
        if (this == AppError.Network) StartupUiState.Offline else StartupUiState.ServerError

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EmotvApplication).container
                StartupViewModel(
                    checkServerStatus = container.checkServerStatus,
                    restoreSession = container.restoreSession,
                    appVersionName = BuildConfig.VERSION_NAME,
                )
            }
        }
    }
}
