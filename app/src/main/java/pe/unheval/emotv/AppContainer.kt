package pe.unheval.emotv

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import pe.unheval.emotv.data.local.EncryptedRefreshTokenStore
import pe.unheval.emotv.data.local.KeystoreTokenCipher
import pe.unheval.emotv.data.local.PinHashStore
import pe.unheval.emotv.data.remote.AccessTokenHolder
import pe.unheval.emotv.data.remote.EmotvApi
import pe.unheval.emotv.data.remote.NetworkFactory
import pe.unheval.emotv.data.repository.AuthRepositoryImpl
import pe.unheval.emotv.data.repository.PinRepositoryImpl
import pe.unheval.emotv.data.repository.StatusRepositoryImpl
import pe.unheval.emotv.domain.repository.AuthRepository
import pe.unheval.emotv.domain.repository.PinRepository
import pe.unheval.emotv.domain.repository.StatusRepository
import pe.unheval.emotv.domain.usecase.CheckServerStatusUseCase
import pe.unheval.emotv.domain.usecase.LoginUseCase
import pe.unheval.emotv.domain.usecase.LogoutUseCase
import pe.unheval.emotv.domain.usecase.RestoreSessionUseCase
import pe.unheval.emotv.domain.usecase.SetPinUseCase

// Solo guarda el token de renovación cifrado y el hash del PIN. Excluido de las copias de seguridad.
private val Context.deviceStore by preferencesDataStore(name = "emotv_device")

/** Dependencias del aplicativo, creadas a mano para no agregar un framework de inyección. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val tokenHolder = AccessTokenHolder()

    private val api: EmotvApi = NetworkFactory.createApi(BuildConfig.API_BASE_URL, tokenHolder)

    private val statusRepository: StatusRepository = StatusRepositoryImpl(api)

    val authRepository: AuthRepository = AuthRepositoryImpl(
        api = api,
        refreshTokenStore = EncryptedRefreshTokenStore(appContext.deviceStore, KeystoreTokenCipher()),
        tokenHolder = tokenHolder,
    )

    private val pinRepository: PinRepository = PinRepositoryImpl(PinHashStore(appContext.deviceStore))

    val checkServerStatus = CheckServerStatusUseCase(statusRepository, BuildConfig.VERSION_NAME)
    val restoreSession = RestoreSessionUseCase(authRepository, pinRepository)
    val login = LoginUseCase(authRepository, pinRepository)
    val logout = LogoutUseCase(authRepository, pinRepository)
    val setPin = SetPinUseCase(pinRepository)
}
