package pe.unheval.emotv

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pe.unheval.emotv.data.local.EncryptedRefreshTokenStore
import pe.unheval.emotv.data.local.KeystoreTokenCipher
import pe.unheval.emotv.data.local.removeLegacyPinHash
import pe.unheval.emotv.data.remote.AccessTokenHolder
import pe.unheval.emotv.data.remote.EmotvApi
import pe.unheval.emotv.data.remote.NetworkFactory
import pe.unheval.emotv.data.repository.AuthRepositoryImpl
import pe.unheval.emotv.data.repository.ReportRepositoryImpl
import pe.unheval.emotv.data.repository.SessionRepositoryImpl
import pe.unheval.emotv.data.repository.StatisticsRepositoryImpl
import pe.unheval.emotv.data.repository.StatusRepositoryImpl
import pe.unheval.emotv.data.repository.SummaryRepositoryImpl
import pe.unheval.emotv.domain.repository.AuthRepository
import pe.unheval.emotv.domain.repository.ReportRepository
import pe.unheval.emotv.domain.repository.SessionRepository
import pe.unheval.emotv.domain.repository.StatisticsRepository
import pe.unheval.emotv.domain.repository.StatusRepository
import pe.unheval.emotv.domain.repository.SummaryRepository
import pe.unheval.emotv.domain.usecase.CheckServerStatusUseCase
import pe.unheval.emotv.domain.usecase.LoadDashboardUseCase
import pe.unheval.emotv.domain.usecase.LoginUseCase
import pe.unheval.emotv.domain.usecase.LogoutUseCase
import pe.unheval.emotv.domain.usecase.RestoreSessionUseCase
import java.time.Clock

// Solo guarda el token de renovación cifrado. Excluido de las copias de seguridad.
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

    private val summaryRepository: SummaryRepository = SummaryRepositoryImpl(api, authRepository)

    val sessionRepository: SessionRepository = SessionRepositoryImpl(api, authRepository)
    val reportRepository: ReportRepository = ReportRepositoryImpl(api, authRepository)
    val statisticsRepository: StatisticsRepository = StatisticsRepositoryImpl(api, authRepository)

    val checkServerStatus = CheckServerStatusUseCase(statusRepository, BuildConfig.VERSION_NAME)
    val restoreSession = RestoreSessionUseCase(authRepository)
    val login = LoginUseCase(authRepository)
    val logout = LogoutUseCase(authRepository)
    val loadDashboard = LoadDashboardUseCase(summaryRepository, Clock.systemDefaultZone())

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { appContext.deviceStore.removeLegacyPinHash() }
    }
}
