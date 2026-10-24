package pe.unheval.emotv.data.repository

import pe.unheval.emotv.data.remote.EmotvApi
import pe.unheval.emotv.data.remote.ParticipantDto
import pe.unheval.emotv.data.remote.SessionDto
import pe.unheval.emotv.data.remote.SummaryDto
import pe.unheval.emotv.domain.model.AuthUser
import pe.unheval.emotv.domain.model.DashboardSummary
import pe.unheval.emotv.domain.model.GuidedPhase
import pe.unheval.emotv.domain.model.MissingRequirement
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.Participant
import pe.unheval.emotv.domain.model.ParticipantKind
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
    participants = participants.map { it.toDomain() },
)

private fun ParticipantDto.toDomain() = Participant(
    id = id,
    code = code,
    kind = ParticipantKind.fromApi(kind),
    ageYears = ageYears,
    ready = ready,
    missing = missing.map(MissingRequirement::fromApi),
    warnings = warnings,
    lastSession = lastSession?.toDomain(),
)

private fun SessionDto.toDomain() = SessionSummary(
    id = id,
    startedAt = parseInstant(startedAt),
    state = SessionState.fromApi(state),
    guidedPhase = GuidedPhase.fromApi(guidedPhase),
    participantId = participantId,
    participantCode = participantCode,
    protocolName = protocolName,
)

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
