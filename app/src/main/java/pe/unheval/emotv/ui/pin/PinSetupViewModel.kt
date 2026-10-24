package pe.unheval.emotv.ui.pin

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
import pe.unheval.emotv.domain.usecase.SetPinUseCase

enum class PinError { Length, Mismatch }

data class PinSetupUiState(
    val pin: String = "",
    val confirmation: String = "",
    val pinError: PinError? = null,
    val confirmationError: PinError? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
)

class PinSetupViewModel(private val setPin: SetPinUseCase) : ViewModel() {

    private val state = MutableStateFlow(PinSetupUiState())
    val uiState: StateFlow<PinSetupUiState> = state.asStateFlow()

    fun onPinChange(value: String) =
        state.update { it.copy(pin = digitsOnly(value), pinError = null) }

    fun onConfirmationChange(value: String) =
        state.update { it.copy(confirmation = digitsOnly(value), confirmationError = null) }

    fun submit() {
        val current = state.value
        if (current.isSaving || current.saved) return
        when {
            !SetPinUseCase.isValid(current.pin) ->
                state.update { it.copy(pinError = PinError.Length) }
            current.pin != current.confirmation ->
                state.update { it.copy(confirmationError = PinError.Mismatch) }
            else -> {
                state.update { it.copy(isSaving = true) }
                viewModelScope.launch {
                    setPin(current.pin)
                    // Se guarda solo el hash; el PIN se descarta de la memoria.
                    state.value = PinSetupUiState(saved = true)
                }
            }
        }
    }

    private fun digitsOnly(value: String) =
        value.filter { it in '0'..'9' }.take(SetPinUseCase.MAX_LENGTH)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EmotvApplication).container
                PinSetupViewModel(container.setPin)
            }
        }
    }
}
