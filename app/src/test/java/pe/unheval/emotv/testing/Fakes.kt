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
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import pe.unheval.emotv.data.local.ValueStore
import pe.unheval.emotv.data.remote.AccessTokenHolder
import pe.unheval.emotv.data.remote.CoincidenceDto
import pe.unheval.emotv.data.remote.CommentDto
import pe.unheval.emotv.data.remote.CommentRequestDto
import pe.unheval.emotv.data.remote.EmotvApi
import pe.unheval.emotv.data.remote.ExpressionRowDto
import pe.unheval.emotv.data.remote.LoginRequestDto
import pe.unheval.emotv.data.remote.MetricsDto
import pe.unheval.emotv.data.remote.NarrativeRequestDto
import pe.unheval.emotv.data.remote.RefreshRequestDto
import pe.unheval.emotv.data.remote.ReportDto
import pe.unheval.emotv.data.remote.ServicesDto
import pe.unheval.emotv.data.remote.SessionDetailDto
import pe.unheval.emotv.data.remote.SessionDto
import pe.unheval.emotv.data.remote.StatisticsDto
import pe.unheval.emotv.data.remote.StatisticsExpressionsDto
import pe.unheval.emotv.data.remote.StatisticsSessionsDto
import pe.unheval.emotv.data.remote.StatusDto
import pe.unheval.emotv.data.remote.SummaryDto
import pe.unheval.emotv.data.remote.TokenResponseDto
import pe.unheval.emotv.data.remote.TotalsDto
import pe.unheval.emotv.data.remote.UserDto
import pe.unheval.emotv.data.repository.AuthRepositoryImpl
import pe.unheval.emotv.data.repository.ReportRepositoryImpl
import pe.unheval.emotv.data.repository.SessionRepositoryImpl
import pe.unheval.emotv.data.repository.StatisticsRepositoryImpl
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

    // --- Consulta y reporte ---

    var sessions: () -> List<SessionDto> = { listOf(sessionDto()) }
    var sessionDetail: (sessionId: String) -> SessionDetailDto = { sessionDetailDto(sessionDto(id = it)) }
    var sessionReport: (sessionId: String) -> ReportDto = {
        throw mobileError(404, "not_found", "La sesión aún no tiene reporte.")
    }
    var createReport: (sessionId: String) -> ReportDto = { reportDto(canEdit = true, authoredByMe = true) }
    var report: (reportId: String) -> ReportDto = { reportDto(id = it) }
    var editReport: (reportId: String, NarrativeRequestDto) -> ReportDto = { id, body ->
        reportDto(id = id, narrative = body.narrative, canEdit = true, authoredByMe = true)
    }
    var submitReport: (reportId: String) -> ReportDto = { reportDto(id = it, status = "submitted") }
    var commentReport: (reportId: String, CommentRequestDto) -> ReportDto = { id, _ -> reportDto(id = id) }
    var returnReport: (reportId: String) -> ReportDto = { reportDto(id = it, status = "changes_requested") }
    var approveReport: (reportId: String) -> ReportDto = { reportDto(id = it, status = "approved") }
    var reviews: () -> List<SessionDto> = { emptyList() }
    var statistics: (scope: String) -> StatisticsDto = { statisticsDto(scope = it) }

    /** Cada llamada como «MÉTODO ruta», en orden, para comprobar qué pidió el aplicativo. */
    val calls = mutableListOf<String>()
    val narrativeRequests = mutableListOf<NarrativeRequestDto>()
    val commentRequests = mutableListOf<CommentRequestDto>()

    override suspend fun sessions(): List<SessionDto> {
        calls += "GET sessions"
        return sessions.invoke()
    }

    override suspend fun sessionDetail(sessionId: String): SessionDetailDto {
        calls += "GET sessions/$sessionId"
        return sessionDetail.invoke(sessionId)
    }

    override suspend fun sessionReport(sessionId: String): ReportDto {
        calls += "GET sessions/$sessionId/report"
        return sessionReport.invoke(sessionId)
    }

    override suspend fun createReport(sessionId: String): ReportDto {
        calls += "POST sessions/$sessionId/report"
        return createReport.invoke(sessionId)
    }

    override suspend fun report(reportId: String): ReportDto {
        calls += "GET reports/$reportId"
        return report.invoke(reportId)
    }

    override suspend fun editReport(reportId: String, body: NarrativeRequestDto): ReportDto {
        calls += "PUT reports/$reportId"
        narrativeRequests += body
        return editReport.invoke(reportId, body)
    }

    override suspend fun submitReport(reportId: String): ReportDto {
        calls += "POST reports/$reportId/submit"
        return submitReport.invoke(reportId)
    }

    override suspend fun commentReport(reportId: String, body: CommentRequestDto): ReportDto {
        calls += "POST reports/$reportId/comments"
        commentRequests += body
        return commentReport.invoke(reportId, body)
    }

    override suspend fun returnReport(reportId: String): ReportDto {
        calls += "POST reports/$reportId/return"
        return returnReport.invoke(reportId)
    }

    override suspend fun approveReport(reportId: String): ReportDto {
        calls += "POST reports/$reportId/approve"
        return approveReport.invoke(reportId)
    }

    override suspend fun reviews(): List<SessionDto> {
        calls += "GET reviews"
        return reviews.invoke()
    }

    override suspend fun statistics(scope: String): StatisticsDto {
        calls += "GET statistics?scope=$scope"
        return statistics.invoke(scope)
    }
}

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
    reports: Boolean = true,
    statistics: Boolean = true,
) = StatusDto(
    status = "ok",
    apiVersion = "1.0.0rc1",
    minAppVersion = minAppVersion,
    services = ServicesDto(login = login, sessions = sessions, reports = reports, statistics = statistics),
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
    protocolName: String? = "Secuencia móvil",
    mine: Boolean = true,
    reportStatus: String? = null,
    reportId: String? = null,
) = SessionDto(
    id = id,
    code = code,
    startedAt = startedAt,
    state = state,
    audience = audience,
    protocolName = protocolName,
    mine = mine,
    reportStatus = reportStatus,
    reportId = reportId,
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
            protocolName = null,
            reportStatus = "draft",
        ),
    ),
    pendingReviews: Int = 0,
    user: UserDto = testUser,
) = SummaryDto(
    user = user,
    date = date,
    todaySessions = todaySessions,
    recentSessions = recentSessions,
    pendingReviews = pendingReviews,
)

fun sessionDetailDto(session: SessionDto = sessionDto()) = SessionDetailDto(
    session = session,
    metrics = MetricsDto(totals = TotalsDto(sentence = "Se registraron 3 capturas.")),
)

fun reportDto(
    id: String = "reporte-1",
    status: String = "draft",
    narrative: String = "",
    canEdit: Boolean = false,
    canReview: Boolean = false,
    authoredByMe: Boolean = false,
    requiresReview: Boolean = true,
    comments: List<CommentDto> = emptyList(),
) = ReportDto(
    id = id,
    status = status,
    narrative = narrative,
    sessionCode = "S-4F7K2Q",
    estimatesNotice = "Las expresiones son estimadas.",
    authoredByMe = authoredByMe,
    requiresReview = requiresReview,
    canEdit = canEdit,
    canReview = canReview,
    comments = comments,
)

fun statisticsDto(scope: String = "mine", total: Int = 4) = StatisticsDto(
    scope = scope,
    notice = "Cuentan sesiones, no personas.",
    sessions = StatisticsSessionsDto(
        total = total,
        completed = total - 1,
        interrupted = 1,
        averageDurationSeconds = 125.0,
    ),
    expressions = StatisticsExpressionsDto(
        countedCaptures = 10,
        excludedCaptures = 2,
        rows = listOf(ExpressionRowDto("neutral", captures = 6, percent = 60.0, sessions = 3)),
    ),
    coincidence = CoincidenceDto(compared = 2, matched = 1),
)

val docenteUser = UserDto(id = "docente-1", email = "docente1@example.com", role = "psychologist", roleName = "Docente")

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
class TestGraph {
    val api = FakeEmotvApi()
    val refreshTokenStore = InMemoryValueStore()
    val tokenHolder = AccessTokenHolder()
    val statusRepository = StatusRepositoryImpl(api)
    val authRepository = AuthRepositoryImpl(api, refreshTokenStore, tokenHolder)
    val summaryRepository = SummaryRepositoryImpl(api, authRepository)
    val sessionRepository = SessionRepositoryImpl(api, authRepository)
    val reportRepository = ReportRepositoryImpl(api, authRepository)
    val statisticsRepository = StatisticsRepositoryImpl(api, authRepository)
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
