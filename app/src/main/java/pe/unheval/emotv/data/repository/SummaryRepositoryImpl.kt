package pe.unheval.emotv.data.repository

import pe.unheval.emotv.data.remote.EmotvApi
import pe.unheval.emotv.data.remote.AudienceDto
import pe.unheval.emotv.data.remote.ProtocolDto
import pe.unheval.emotv.data.remote.SessionDto
import pe.unheval.emotv.data.remote.SummaryDto
import pe.unheval.emotv.domain.model.AudienceOption
import pe.unheval.emotv.domain.model.AuthUser
import pe.unheval.emotv.domain.model.DashboardSummary
import pe.unheval.emotv.domain.model.GuidedPhase
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.SessionAudience
import pe.unheval.emotv.domain.model.SessionProtocol
import pe.unheval.emotv.domain.model.SessionState
import pe.unheval.emotv.domain.model.SessionSummary
import pe.unheval.emotv.domain.repository.AuthRepository
import pe.unheval.emotv.domain.repository.SummaryRepository
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** El resumen vive solo en memoria: las respuestas llevan `Cache-Control: no-store`. */
class SummaryRepositoryImpl(
    private val api: EmotvApi,
    private val authRepository: AuthRepository,
) : SummaryRepository {

    override suspend fun fetchSummary(date: LocalDate, utcOffsetMinutes: Int): Outcome<DashboardSummary> =
        authRepository.authorizedCall { api.summary(date.toString(), utcOffsetMinutes).toDomain() }
}

private fun SummaryDto.toDomain() = DashboardSummary(
    user = AuthUser(id = user.id, email = user.email, role = user.role, roleName = user.roleName),
    date = parseDate(date),
    todaySessions = todaySessions.map { it.toDomain() },
    recentSessions = recentSessions.map { it.toDomain() },
    protocols = protocols.map { it.toDomain() },
    // Una opción que esta versión no conoce no se ofrece.
    audiences = audiences.map { it.toDomain() }.filter { it.audience != SessionAudience.Unknown },
)

private fun SessionDto.toDomain() = SessionSummary(
    id = id,
    code = code,
    startedAt = parseInstant(startedAt),
    state = SessionState.fromApi(state),
    audience = SessionAudience.fromApi(audience),
    guidedPhase = GuidedPhase.fromApi(guidedPhase),
    protocolName = protocolName,
)

private fun ProtocolDto.toDomain() = SessionProtocol(
    id = id,
    name = name,
    version = version,
    forChildren = forChildren,
    audience = SessionAudience.fromApi(audience ?: if (forChildren) "infantil" else "adulto"),
    stepCount = stepCount,
    hasPreQuestionnaire = hasPreQuestionnaire,
    hasPostQuestionnaire = hasPostQuestionnaire,
    confirmation = confirmation,
)

private fun AudienceDto.toDomain() = AudienceOption(SessionAudience.fromApi(value), confirmation)

// apiCall traduce IllegalArgumentException a AppError.Unexpected.
private fun parseInstant(text: String): Instant =
    try {
        OffsetDateTime.parse(text).toInstant()
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Fecha inválida", e)
    }

private fun parseDate(text: String): LocalDate =
    try {
        LocalDate.parse(text)
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Fecha inválida", e)
    }
