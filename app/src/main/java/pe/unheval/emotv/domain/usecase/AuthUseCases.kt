package pe.unheval.emotv.domain.usecase

import pe.unheval.emotv.domain.model.AuthUser
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.repository.AuthRepository

class LoginUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): Outcome<AuthUser> =
        authRepository.login(email.trim(), password)
}

/** A dónde va el aplicativo tras la pantalla de carga. */
enum class StartDestination { Login, Home }

class RestoreSessionUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(): Outcome<StartDestination> =
        when (val result = authRepository.restoreSession()) {
            is Outcome.Failure -> result
            is Outcome.Success ->
                Outcome.Success(if (result.value == null) StartDestination.Login else StartDestination.Home)
        }
}

class LogoutUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke() = authRepository.logout()
}

/** El Docente revisa reportes y ve las estadísticas de su grupo; el Usuario no. */
fun AuthUser.isDocente(): Boolean = role == "psychologist"
