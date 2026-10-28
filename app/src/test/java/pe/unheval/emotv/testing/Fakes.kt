package pe.unheval.emotv.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import pe.unheval.emotv.data.local.PinHasher
import pe.unheval.emotv.data.local.ValueStore
import pe.unheval.emotv.data.remote.AccessTokenHolder
import pe.unheval.emotv.data.remote.AudienceDto
import pe.unheval.emotv.data.remote.CalibrationDto
import pe.unheval.emotv.data.remote.EmotvApi
import pe.unheval.emotv.data.remote.FrameResultDto
import pe.unheval.emotv.data.remote.LoginRequestDto
import pe.unheval.emotv.data.remote.PhaseDto
import pe.unheval.emotv.data.remote.ProtocolDto
import pe.unheval.emotv.data.remote.QuestionnaireAnswersDto
import pe.unheval.emotv.data.remote.QuestionnaireDto
import pe.unheval.emotv.data.remote.RefreshRequestDto
import pe.unheval.emotv.data.remote.ServicesDto
import pe.unheval.emotv.data.remote.SessionDto
import pe.unheval.emotv.data.remote.SessionStepDto
import pe.unheval.emotv.data.remote.StartSessionDto
import pe.unheval.emotv.data.remote.StartSessionRequestDto
import pe.unheval.emotv.data.remote.StatusDto
import pe.unheval.emotv.data.remote.StimulusDto
import pe.unheval.emotv.data.remote.StopRequestDto
import pe.unheval.emotv.data.remote.SummaryDto
import pe.unheval.emotv.data.remote.TokenResponseDto
import pe.unheval.emotv.data.remote.UserDto
import pe.unheval.emotv.data.repository.AuthRepositoryImpl
import pe.unheval.emotv.data.repository.PinRepositoryImpl
import pe.unheval.emotv.data.repository.SessionRepositoryImpl
import pe.unheval.emotv.data.repository.StatusRepositoryImpl
import pe.unheval.emotv.data.repository.SummaryRepositoryImpl
import pe.unheval.emotv.domain.repository.FrameSource
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

    // --- Sesión ---

    var startSession: (StartSessionRequestDto) -> StartSessionDto = { startSessionDto() }
    var next: (sessionId: String) -> SessionStepDto = { stepDto(it, "finished") }
    var submitQuestionnaire: (sessionId: String, moment: String, QuestionnaireAnswersDto) -> PhaseDto =
        { _, _, _ -> PhaseDto("calibration") }
    var advance: (sessionId: String) -> PhaseDto = { PhaseDto("calibration") }
    var startCalibration: (sessionId: String) -> PhaseDto = { PhaseDto("calibration") }
    var sendFrame: (sessionId: String, jpeg: ByteArray) -> FrameResultDto = { _, _ -> FrameResultDto("stimuli") }
    var finish: (sessionId: String) -> PhaseDto = { PhaseDto("finished") }
    var stop: (sessionId: String, StopRequestDto) -> PhaseDto = { _, _ -> PhaseDto("stopped") }
    var stimulusImage: (url: String) -> ByteArray = { byteArrayOf(1, 2, 3) }

    val startRequests = mutableListOf<StartSessionRequestDto>()
    val finishRequests = mutableListOf<String>()
    val questionnaireRequests = mutableListOf<Pair<String, QuestionnaireAnswersDto>>()
    val stopRequests = mutableListOf<Pair<String, StopRequestDto>>()
    val imageRequests = mutableListOf<String>()

    /** Copia de cada cuadro recibido, solo para comprobar qué llegó al «servidor». */
    val receivedFrames = mutableListOf<ByteArray>()
    var frameAttempts = 0
    var nextCalls = 0
    var advanceCalls = 0
    var calibrationCalls = 0

    override suspend fun startSession(body: StartSessionRequestDto): StartSessionDto {
        startRequests += body
        return startSession.invoke(body)
    }

    override suspend fun next(sessionId: String): SessionStepDto {
        nextCalls++
        return next.invoke(sessionId)
    }

    override suspend fun submitQuestionnaire(
        sessionId: String,
        moment: String,
        body: QuestionnaireAnswersDto,
    ): PhaseDto {
        questionnaireRequests += moment to body
        return submitQuestionnaire.invoke(sessionId, moment, body)
    }

    override suspend fun advance(sessionId: String): PhaseDto {
        advanceCalls++
        return advance.invoke(sessionId)
    }

    override suspend fun startCalibration(sessionId: String): PhaseDto {
        calibrationCalls++
        return startCalibration.invoke(sessionId)
    }

    override suspend fun sendFrame(sessionId: String, jpeg: RequestBody): FrameResultDto {
        frameAttempts++
        val bytes = Buffer().also(jpeg::writeTo).readByteArray()
        val result = sendFrame.invoke(sessionId, bytes)
        receivedFrames += bytes
        return result
    }

    override suspend fun finish(sessionId: String): PhaseDto {
        finishRequests += sessionId
        return finish.invoke(sessionId)
    }

    override suspend fun stop(sessionId: String, body: StopRequestDto): PhaseDto {
        stopRequests += sessionId to body
        return stop.invoke(sessionId, body)
    }

    override suspend fun stimulusImage(imageUrl: String): ResponseBody {
        imageRequests += imageUrl
        return stimulusImage.invoke(imageUrl).toResponseBody("image/png".toMediaType())
    }
}

/**
 * Cámara falsa: cada cuadro es un arreglo nuevo con forma de JPEG. Guarda la referencia
 * para comprobar que, tras enviarlo, quedó borrado.
 */
class FakeFrameSource : FrameSource {
    val captured = mutableListOf<ByteArray>()
    val qualities = mutableListOf<Int>()

    override suspend fun capture(jpegQuality: Int): ByteArray {
        qualities += jpegQuality
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x11, 0x22, 0x33, 0xFF.toByte(), 0xD9.toByte())
            .also { captured += it }
    }
}

const val ADULT_CONFIRMATION = "Es una sesión mía o cuento con la autorización firmada de la persona adulta"
const val CHILD_CONFIRMATION = "Su tutor autorizó por escrito, está presente y el niño aceptó participar"

fun protocolDto(id: String = "protocolo-1", name: String = "Secuencia móvil", forChildren: Boolean = false) =
    ProtocolDto(
        id = id,
        name = name,
        version = 1,
        forChildren = forChildren,
        audience = if (forChildren) "infantil" else "adulto",
        stepCount = 2,
        hasPreQuestionnaire = true,
        hasPostQuestionnaire = true,
        confirmation = if (forChildren) CHILD_CONFIRMATION else ADULT_CONFIRMATION,
    )

fun audienceDtos() = listOf(
    AudienceDto("propia", ADULT_CONFIRMATION),
    AudienceDto("adulto", ADULT_CONFIRMATION),
    AudienceDto("infantil", CHILD_CONFIRMATION),
)

fun startSessionDto(
    sessionId: String = "sesion-1",
    code: String = "S-4F7K2Q",
    audience: String = "adulto",
    hasProtocol: Boolean = true,
) = StartSessionDto(
    sessionId = sessionId,
    code = code,
    phase = if (hasProtocol) "pre_questionnaire" else "calibration",
    audience = audience,
    hasProtocol = hasProtocol,
    reminder = "Recuerda tu compromiso de confidencialidad.",
)

fun stepDto(
    sessionId: String = "sesion-1",
    phase: String,
    sendFrames: Boolean = false,
    frameIntervalMs: Long? = null,
    captureStatus: List<String> = emptyList(),
    questionnaire: QuestionnaireDto? = null,
    calibration: CalibrationDto? = null,
    stimulus: StimulusDto? = null,
    stopReason: String? = null,
    stepCount: Int = 2,
) = SessionStepDto(
    sessionId = sessionId,
    code = "S-4F7K2Q",
    phase = phase,
    stopReason = stopReason,
    sendFrames = sendFrames,
    frameIntervalMs = frameIntervalMs,
    captureStatus = captureStatus,
    questionnaire = questionnaire,
    calibration = calibration,
    stimulus = stimulus,
    stepCount = stepCount,
)

/** Crea el ViewModel en un `ViewModelStore` para poder cancelar su `viewModelScope` al final. */
inline fun <reified VM : ViewModel> ViewModelStore.create(crossinline build: () -> VM): VM =
    ViewModelProvider.create(this, viewModelFactory { initializer { build() } })[VM::class]

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
    sessions: Boolean = true,
) = StatusDto(
    status = "ok",
    apiVersion = "1.0.0rc1",
    minAppVersion = minAppVersion,
    services = ServicesDto(login = login, sessions = sessions),
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
    code: String? = "S-4F7K2Q",
    startedAt: String = "2026-10-23T14:04:10.922662+00:00",
    state: String = "in_progress",
    audience: String = "adulto",
    guidedPhase: String? = "stimuli",
    protocolName: String? = "Secuencia móvil",
) = SessionDto(
    id = id,
    code = code,
    startedAt = startedAt,
    state = state,
    audience = audience,
    guidedPhase = guidedPhase,
    protocolName = protocolName,
)

fun summaryDto(
    date: String = "2026-10-23",
    todaySessions: List<SessionDto> = listOf(sessionDto()),
    recentSessions: List<SessionDto> = listOf(
        sessionDto(),
        sessionDto(
            id = "sesion-0",
            code = "S-9QW3ZD",
            startedAt = "2026-10-22T16:30:00+00:00",
            state = "completed",
            audience = "propia",
            guidedPhase = "finished",
            protocolName = null,
        ),
    ),
    protocols: List<ProtocolDto> = listOf(protocolDto()),
) = SummaryDto(
    user = testUser,
    date = date,
    todaySessions = todaySessions,
    recentSessions = recentSessions,
    protocols = protocols,
    audiences = audienceDtos(),
)

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
    val sessionRepository = SessionRepositoryImpl(api, authRepository, dispatcher)
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
