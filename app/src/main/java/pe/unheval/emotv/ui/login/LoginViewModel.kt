package pe.unheval.emotv.ui.login

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
import pe.unheval.emotv.domain.model.ErrorCodes
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.usecase.LoginUseCase

enum class FieldError { Required, InvalidEmail }

/** Errores del inicio de sesión. Los textos los pone la pantalla (strings.xml). */
sealed interface LoginError {
    /** Mensaje del servidor (`invalid_credentials`, `role_not_allowed`, etc.); puede faltar. */
    data class Server(val code: String?, val message: String?) : LoginError
    data class TooManyAttempts(val message: String?, val retryAfterSeconds: Long?) : LoginError
    data object Network : LoginError
    data object Unexpected : LoginError
}

sealed interface LoginNavigation {
    data object PinSetup : LoginNavigation
    data class PasswordChangeRequired(val message: String?) : LoginNavigation
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val isLoading: Boolean = false,
    val error: LoginError? = null,
    val navigation: LoginNavigation? = null,
)

class LoginViewModel(private val login: LoginUseCase) : ViewModel() {

    private val state = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = state.asStateFlow()

    fun onEmailChange(value: String) =
        state.update { it.copy(email = value, emailError = null, error = null) }

    fun onPasswordChange(value: String) =
        state.update { it.copy(password = value, passwordError = null, error = null) }

    fun submit() {
        val current = state.value
        if (current.isLoading) return

        val email = current.email.trim()
        val emailError = when {
            email.isEmpty() -> FieldError.Required
            !EMAIL.matches(email) -> FieldError.InvalidEmail
            else -> null
        }
        val passwordError = if (current.password.isEmpty()) FieldError.Required else null
        if (emailError != null || passwordError != null) {
            state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = login(email, current.password)
            state.update {
                when (result) {
                    // La contraseña no se conserva en memoria más de lo necesario.
                    is Outcome.Success -> it.copy(
                        password = "",
                        isLoading = false,
                        navigation = LoginNavigation.PinSetup,
                    )
                    is Outcome.Failure -> failure(it, result.error)
                }
            }
        }
    }

    fun onNavigated() = state.update { it.copy(navigation = null) }

    private fun failure(current: LoginUiState, error: AppError): LoginUiState {
        val base = current.copy(isLoading = false)
        return when {
            error is AppError.Server && error.code == ErrorCodes.PASSWORD_CHANGE_REQUIRED ->
                base.copy(
                    password = "",
                    navigation = LoginNavigation.PasswordChangeRequired(error.message),
                )
            error is AppError.Server && error.code == ErrorCodes.TOO_MANY_ATTEMPTS ->
                base.copy(error = LoginError.TooManyAttempts(error.message, error.retryAfterSeconds))
            error is AppError.Server -> base.copy(error = LoginError.Server(error.code, error.message))
            error == AppError.Network -> base.copy(error = LoginError.Network)
            else -> base.copy(error = LoginError.Unexpected)
        }
    }

    companion object {
        private val EMAIL = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EmotvApplication).container
                LoginViewModel(container.login)
            }
        }
    }
}
