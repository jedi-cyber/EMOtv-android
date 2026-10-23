package pe.unheval.emotv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.unheval.emotv.EmotvApplication
import pe.unheval.emotv.domain.model.AuthUser
import pe.unheval.emotv.domain.repository.AuthRepository
import pe.unheval.emotv.domain.usecase.LogoutUseCase

class HomeViewModel(
    authRepository: AuthRepository,
    private val logout: LogoutUseCase,
) : ViewModel() {

    val user: StateFlow<AuthUser?> = authRepository.currentUser

    private val loggingOut = MutableStateFlow(false)
    val isLoggingOut: StateFlow<Boolean> = loggingOut.asStateFlow()

    private val loggedOut = MutableStateFlow(false)
    val isLoggedOut: StateFlow<Boolean> = loggedOut.asStateFlow()

    fun logOut() {
        if (loggingOut.value) return
        loggingOut.value = true
        viewModelScope.launch {
            logout()
            loggedOut.value = true
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EmotvApplication).container
                HomeViewModel(container.authRepository, container.logout)
            }
        }
    }
}
