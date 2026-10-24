package pe.unheval.emotv.domain.usecase

import pe.unheval.emotv.domain.model.AuthUser
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.repository.AuthRepository
import pe.unheval.emotv.domain.repository.PinRepository

class LoginUseCase(
    private val authRepository: AuthRepository,
    private val pinRepository: PinRepository,
) {
    suspend operator fun invoke(email: String, password: String): Outcome<AuthUser> {
        val result = authRepository.login(email.trim(), password)
        // Cada inicio de sesión define un PIN nuevo.
        if (result is Outcome.Success) pinRepository.clear()
        return result
    }
}

/** A dónde va el aplicativo tras la pantalla de carga. */
enum class StartDestination { Login, PinSetup, Home }

class RestoreSessionUseCase(
    private val authRepository: AuthRepository,
    private val pinRepository: PinRepository,
) {
    suspend operator fun invoke(): Outcome<StartDestination> =
        when (val result = authRepository.restoreSession()) {
            is Outcome.Failure -> result
            is Outcome.Success -> Outcome.Success(
                when {
                    result.value == null -> StartDestination.Login
                    pinRepository.hasPin() -> StartDestination.Home
                    else -> StartDestination.PinSetup
                },
            )
        }
}

class LogoutUseCase(
    private val authRepository: AuthRepository,
    private val pinRepository: PinRepository,
) {
    suspend operator fun invoke() {
        authRepository.logout()
        pinRepository.clear()
    }
}

class SetPinUseCase(private val pinRepository: PinRepository) {
    suspend operator fun invoke(pin: String) = pinRepository.savePin(pin)

    companion object {
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 6

        fun isValid(pin: String): Boolean =
            pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }
    }
}

/** PIN de quien inició sesión, para salir del modo sesión. Solo se compara con su hash. */
class VerifyPinUseCase(private val pinRepository: PinRepository) {
    suspend operator fun invoke(pin: String): Boolean = pinRepository.verifyPin(pin)
}
