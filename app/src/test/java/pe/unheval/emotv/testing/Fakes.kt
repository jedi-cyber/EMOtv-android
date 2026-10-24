package pe.unheval.emotv.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import pe.unheval.emotv.data.local.PinHasher
import pe.unheval.emotv.data.local.ValueStore
import pe.unheval.emotv.data.remote.AccessTokenHolder
import pe.unheval.emotv.data.remote.EmotvApi
import pe.unheval.emotv.data.remote.LoginRequestDto
import pe.unheval.emotv.data.remote.ParticipantDto
import pe.unheval.emotv.data.remote.RefreshRequestDto
import pe.unheval.emotv.data.remote.ServicesDto
import pe.unheval.emotv.data.remote.SessionDto
import pe.unheval.emotv.data.remote.StatusDto
import pe.unheval.emotv.data.remote.SummaryDto
import pe.unheval.emotv.data.remote.TokenResponseDto
import pe.unheval.emotv.data.remote.UserDto
import pe.unheval.emotv.data.repository.AuthRepositoryImpl
import pe.unheval.emotv.data.repository.PinRepositoryImpl
import pe.unheval.emotv.data.repository.StatusRepositoryImpl
import pe.unheval.emotv.data.repository.SummaryRepositoryImpl
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** API falsa: ninguna prueba llama al servidor real. */
class FakeEmotvApi : EmotvApi {
    var status: () -> StatusDto = { statusDto() }
    var login: (LoginRequestDto) -> TokenResponseDto = { tokenResponse() }
    var refresh: (RefreshRequestDto) -> TokenResponseDto = {
        tokenResponse(accessToken = "acceso-renovado", refreshToken = "renovado")
    }
    var summary: (date: String, utcOffsetMinutes: Int) -> SummaryDto = { _, _ -> summaryDto() }

    val loginRequests = mutableListOf<LoginRequestDto>()
    val refreshRequests = mutableListOf<RefreshRequestDto>()
    val summaryRequests = mutableListOf<Pair<String, Int>>()
    var statusCalls = 0
    var logoutCalls = 0

    override suspend fun status(): StatusDto {
        statusCalls++
        return status.invoke()
    }

    override suspend fun login(body: LoginRequestDto): TokenResponseDto {
        loginRequests += body
        return login.invoke(body)
    }

    override suspend fun refresh(body: RefreshRequestDto): TokenResponseDto {
        refreshRequests += body
        return refresh.invoke(body)
    }

    override suspend fun logout(): Response<Unit> {
        logoutCalls++
        return Response.success(204, Unit)
    }

    override suspend fun summary(date: String, utcOffsetMinutes: Int): SummaryDto {
        summaryRequests += date to utcOffsetMinutes
        return summary.invoke(date, utcOffsetMinutes)
    }
}

class InMemoryValueStore(var value: String? = null) : ValueStore {
    override suspend fun read(): String? = value
    override suspend fun write(value: String) {
        this.value = value
    }
    override suspend fun clear() {
        value = null
    }
}

fun statusDto(
    minAppVersion: String = "1.0.0",
    login: Boolean = true,
    participantSessions: Boolean = true,
) = StatusDto(
    status = "ok",
    apiVersion = "1.0.0rc1",
    minAppVersion = minAppVersion,
    services = ServicesDto(login = login, participantSessions = participantSessions),
)

fun tokenResponse(
    accessToken: String = "acceso-de-prueba",
    refreshToken: String = "renovacion-de-prueba",
    role: String = "student",
    roleName: String = "Usuario",
) = TokenResponseDto(
    accessToken = accessToken,
    expiresIn = 900,
    refreshToken = refreshToken,
    refreshExpiresIn = 2_592_000,
    user = UserDto(id = "user-1", email = "usuario1@example.com", role = role, roleName = roleName),
)

val testUser = UserDto(id = "user-1", email = "usuario1@example.com", role = "student", roleName = "Usuario")

fun sessionDto(
    id: String = "sesion-1",
    startedAt: String = "2026-10-23T14:04:10.922662+00:00",
    state: String = "in_progress",
    guidedPhase: String? = "stimuli",
    participantId: String = "p-adulto",
    participantCode: String = "P-RGNYTB",
) = SessionDto(
    id = id,
    startedAt = startedAt,
    state = state,
    guidedPhase = guidedPhase,
    participantId = participantId,
    participantCode = participantCode,
    protocolName = "Secuencia móvil",
)

/** Adulto con todas sus autorizaciones y una sesión en curso hoy. */
fun readyAdultDto() = ParticipantDto(
    id = "p-adulto",
    code = "P-RGNYTB",
    kind = "adult",
    ageYears = 30,
    ready = true,
    lastSession = sessionDto(),
)

/** Niño sin asentimiento: el servidor no permite iniciar una sesión. */
fun childWithoutAssentDto() = ParticipantDto(
    id = "p-nino",
    code = "P-7KQ2MD",
    kind = "child",
    ageYears = 9,
    ready = false,
    missing = listOf("child_assent"),
    warnings = listOf("Falta el asentimiento del niño o la niña"),
)

fun summaryDto(
    date: String = "2026-10-23",
    todaySessions: List<SessionDto> = listOf(sessionDto()),
    participants: List<ParticipantDto> = listOf(readyAdultDto(), childWithoutAssentDto()),
) = SummaryDto(user = testUser, date = date, todaySessions = todaySessions, participants = participants)

/** Error con la forma de docs/mobile-api.md: `{"detail": {"code", "message"}}`. */
fun mobileError(
    httpStatus: Int,
    code: String,
    message: String,
    retryAfterSeconds: Long? = null,
): HttpException {
    val json = """{"detail": {"code": "$code", "message": "$message"}}"""
    val raw = okhttp3.Response.Builder()
        .code(httpStatus)
        .message("error")
        .protocol(Protocol.HTTP_1_1)
        .request(Request.Builder().url("http://localhost/").build())
        .apply { if (retryAfterSeconds != null) header("Retry-After", retryAfterSeconds.toString()) }
        .build()
    return HttpException(
        Response.error<Any>(json.toResponseBody("application/json".toMediaType()), raw),
    )
}

fun networkError() = IOException("sin conexión")

/** Repositorios reales sobre la API falsa y almacenamiento en memoria. */
class TestGraph(dispatcher: TestDispatcher) {
    val api = FakeEmotvApi()
    val refreshTokenStore = InMemoryValueStore()
    val pinStore = InMemoryValueStore()
    val tokenHolder = AccessTokenHolder()
    val statusRepository = StatusRepositoryImpl(api)
    val authRepository = AuthRepositoryImpl(api, refreshTokenStore, tokenHolder)
    val pinRepository = PinRepositoryImpl(pinStore, PinHasher(iterations = 1_000), dispatcher)
    val summaryRepository = SummaryRepositoryImpl(api, authRepository)
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
