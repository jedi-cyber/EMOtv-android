package pe.unheval.emotv.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.unheval.emotv.data.local.ValueStore
import pe.unheval.emotv.data.remote.AccessTokenHolder
import pe.unheval.emotv.data.remote.EmotvApi
import pe.unheval.emotv.data.remote.LoginRequestDto
import pe.unheval.emotv.data.remote.RefreshRequestDto
import pe.unheval.emotv.data.remote.TokenResponseDto
import pe.unheval.emotv.data.remote.apiCall
import pe.unheval.emotv.domain.model.AppError
import pe.unheval.emotv.domain.model.AuthUser
import pe.unheval.emotv.domain.model.ErrorCodes
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val api: EmotvApi,
    private val refreshTokenStore: ValueStore,
    private val tokenHolder: AccessTokenHolder,
) : AuthRepository {

    private val user = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = user.asStateFlow()

    override suspend fun login(email: String, password: String): Outcome<AuthUser> =
        when (val result = apiCall { api.login(LoginRequestDto(email, password)) }) {
            is Outcome.Failure -> result
            is Outcome.Success -> Outcome.Success(accept(result.value))
        }

    // El token de renovación cambia en cada uso: dos renovaciones a la vez revocarían la sesión.
    private val renewLock = Mutex()

    override suspend fun restoreSession(): Outcome<AuthUser?> = renewLock.withLock { renew() }

    override suspend fun renewAccess(rejectedToken: String?): Outcome<Unit> = renewLock.withLock {
        val current = tokenHolder.accessToken
        // Otra llamada ya lo renovó mientras se esperaba el turno.
        if (current != null && current != rejectedToken) return@withLock Outcome.Success(Unit)
        when (val result = renew()) {
            is Outcome.Failure -> result
            is Outcome.Success ->
                if (result.value != null) Outcome.Success(Unit) else Outcome.Failure(SESSION_ENDED)
        }
    }

    override fun currentAccessToken(): String? = tokenHolder.accessToken

    private suspend fun renew(): Outcome<AuthUser?> {
        val refreshToken = refreshTokenStore.read() ?: return Outcome.Success(null)
        return when (val result = apiCall { api.refresh(RefreshRequestDto(refreshToken)) }) {
            is Outcome.Success -> Outcome.Success(accept(result.value))
            is Outcome.Failure -> {
                val error = result.error
                if (error is AppError.Server && error.httpStatus in 400..499) {
                    // Sesión vencida, revocada o de una cuenta que ya no puede usar el aplicativo.
                    clearLocal()
                    Outcome.Success(null)
                } else {
                    result
                }
            }
        }
    }

    override suspend fun logout() {
        if (tokenHolder.accessToken != null) {
            // Si no hay conexión, la sesión local se borra igual; el servidor la vence sola.
            apiCall { api.logout() }
        }
        clearLocal()
    }

    private suspend fun accept(response: TokenResponseDto): AuthUser {
        refreshTokenStore.write(response.refreshToken)
        tokenHolder.accessToken = response.accessToken
        val authUser = AuthUser(
            id = response.user.id,
            email = response.user.email,
            role = response.user.role,
            roleName = response.user.roleName,
        )
        user.value = authUser
        return authUser
    }

    private suspend fun clearLocal() {
        tokenHolder.accessToken = null
        user.value = null
        refreshTokenStore.clear()
    }

    private companion object {
        val SESSION_ENDED = AppError.Server(
            httpStatus = 401,
            code = ErrorCodes.INVALID_REFRESH_TOKEN,
            message = null,
        )
    }
}
