package pe.unheval.emotv.data.repository

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.unheval.emotv.data.remote.ReportDto
import pe.unheval.emotv.data.remote.SessionDto
import pe.unheval.emotv.domain.model.Report
import pe.unheval.emotv.domain.model.ReportComment
import pe.unheval.emotv.domain.model.ReportSection
import pe.unheval.emotv.domain.model.ReportStatus
import pe.unheval.emotv.domain.model.SessionAudience
import pe.unheval.emotv.domain.model.SessionState
import pe.unheval.emotv.domain.model.SessionSummary
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

internal fun SessionDto.toDomain() = SessionSummary(
    id = id,
    code = code,
    startedAt = parseInstant(startedAt),
    state = SessionState.fromApi(state),
    audience = SessionAudience.fromApi(audience),
    protocolName = protocolName,
    mine = mine,
    reportStatus = reportStatus?.let(ReportStatus::fromApi),
)

internal fun ReportDto.toDomain() = Report(
    id = id,
    version = version,
    status = ReportStatus.fromApi(status),
    current = current,
    narrative = narrative,
    sessionCode = sessionCode,
    estimatesNotice = estimatesNotice,
    abstractNotice = abstractNotice,
    authoredByMe = authoredByMe,
    requiresReview = requiresReview,
    canEdit = canEdit,
    canReview = canReview,
    comments = comments.map {
        ReportComment(it.id, ReportSection.fromApi(it.section), it.text, parseInstant(it.createdAt))
    },
)

/** Respuesta de un cuestionario tal como se registró: número, texto o lista. */
internal fun JsonElement?.answerText(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> content
    is JsonArray -> mapNotNull { it.answerText() }.joinToString(", ").ifEmpty { null }
    is JsonObject -> toString()
}

// apiCall traduce IllegalArgumentException a AppError.Unexpected.
internal fun parseInstant(text: String): Instant =
    try {
        OffsetDateTime.parse(text).toInstant()
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Fecha inválida", e)
    }

internal fun parseDate(text: String): LocalDate =
    try {
        LocalDate.parse(text)
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Fecha inválida", e)
    }
