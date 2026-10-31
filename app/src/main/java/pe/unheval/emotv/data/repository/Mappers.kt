package pe.unheval.emotv.data.repository

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.unheval.emotv.data.remote.MetricsDto
import pe.unheval.emotv.data.remote.PracticeDto
import pe.unheval.emotv.data.remote.PracticeRowDto
import pe.unheval.emotv.data.remote.PracticeTotalsDto
import pe.unheval.emotv.data.remote.ReportDto
import pe.unheval.emotv.data.remote.SessionDetailDto
import pe.unheval.emotv.data.remote.SessionDto
import pe.unheval.emotv.data.remote.StatisticsDto
import pe.unheval.emotv.data.remote.StatisticsPracticeDto
import pe.unheval.emotv.domain.model.AbstractStimulusMetric
import pe.unheval.emotv.domain.model.EmotionalStimulusMetric
import pe.unheval.emotv.domain.model.ExpressionShare
import pe.unheval.emotv.domain.model.MarkerKind
import pe.unheval.emotv.domain.model.PracticeCounts
import pe.unheval.emotv.domain.model.PracticeOutcome
import pe.unheval.emotv.domain.model.PracticeProtocolResult
import pe.unheval.emotv.domain.model.PracticeTargetResult
import pe.unheval.emotv.domain.model.PracticeTotals
import pe.unheval.emotv.domain.model.QuestionAnswer
import pe.unheval.emotv.domain.model.QuestionnaireMoment
import pe.unheval.emotv.domain.model.Report
import pe.unheval.emotv.domain.model.ReportComment
import pe.unheval.emotv.domain.model.ReportSection
import pe.unheval.emotv.domain.model.ReportStatus
import pe.unheval.emotv.domain.model.SessionAudience
import pe.unheval.emotv.domain.model.SessionDetail
import pe.unheval.emotv.domain.model.SessionMetrics
import pe.unheval.emotv.domain.model.SessionPractice
import pe.unheval.emotv.domain.model.SessionState
import pe.unheval.emotv.domain.model.SessionStatistics
import pe.unheval.emotv.domain.model.SessionSummary
import pe.unheval.emotv.domain.model.StatisticsPractice
import pe.unheval.emotv.domain.model.StatisticsScope
import pe.unheval.emotv.domain.model.TimelineEvent
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

internal fun SessionDetailDto.toDomain(): SessionDetail {
    val lanes = timeline.lanes
    val events = buildList {
        lanes.questionnaires.forEach { item ->
            val moment = if (item.moment == "post") QuestionnaireMoment.Post else QuestionnaireMoment.Pre
            add(
                TimelineEvent.Questionnaire(
                    time = item.time,
                    moment = moment,
                    title = item.title,
                    answers = item.answers.map { QuestionAnswer(it.question, it.answer.answerText()) },
                ),
            )
        }
        lanes.stimuli.forEach {
            if (it.kind == "practice" && it.targetExpression != null) {
                add(
                    TimelineEvent.Practice(
                        time = it.start,
                        targetExpression = it.targetExpression,
                        outcome = PracticeOutcome.fromApi(it.outcome),
                        secondsToAchieve = it.secondsToAchieve,
                    ),
                )
            } else {
                add(TimelineEvent.Stimulus(it.start, it.title, it.skipped))
            }
        }
        lanes.captures.forEach { add(TimelineEvent.Capture(it.time, it.emotion, goodQuality = it.quality == "good")) }
        lanes.said.forEach { add(TimelineEvent.Said(it.time, it.title, it.text)) }
        lanes.markers.forEach { add(TimelineEvent.Marker(it.time, MarkerKind.fromApi(it.kind), it.note)) }
    }
    return SessionDetail(
        session = session.toDomain(),
        durationSeconds = timeline.durationSeconds,
        // Orden estable: a igual tiempo se conserva el orden de las líneas.
        timeline = events.sortedBy { it.time ?: Double.MAX_VALUE },
        metrics = metrics.toDomain(),
    )
}

internal fun MetricsDto.toDomain() = SessionMetrics(
    emotional = emotional.map {
        EmotionalStimulusMetric(it.title, it.skipped, it.expectedExpression, it.counted, it.matchesExpected)
    },
    abstract = abstract.map { AbstractStimulusMetric(it.title, it.skipped, it.said) },
    sentence = totals.sentence,
    abstractNotice = abstractNotice,
    // Sin ejercicios no hay sección de práctica, aunque el servidor envíe el bloque vacío.
    practice = practice?.takeIf { it.exercises.isNotEmpty() || it.byTarget.isNotEmpty() }?.toDomain(),
)

internal fun PracticeDto.toDomain() = SessionPractice(
    totals = totals.toDomain(),
    byTarget = byTarget.toTargetResults(),
    notice = notice,
)

internal fun StatisticsDto.toDomain(scope: StatisticsScope) = SessionStatistics(
    scope = scope,
    notice = notice,
    fewSessionsNotice = fewSessionsNotice,
    total = sessions.total,
    completed = sessions.completed,
    interrupted = sessions.interrupted,
    averageDurationSeconds = sessions.averageDurationSeconds,
    countedCaptures = expressions.countedCaptures,
    excludedCaptures = expressions.excludedCaptures,
    expressions = expressions.rows.map { ExpressionShare(it.expression, it.captures, it.percent, it.sessions) },
    compared = coincidence.compared,
    matched = coincidence.matched,
    practice = practice?.takeIf { it.sessions > 0 || it.byTarget.isNotEmpty() }?.toDomain(),
)

internal fun StatisticsPracticeDto.toDomain() = StatisticsPractice(
    sessions = sessions,
    totals = totals.toDomain(),
    byProtocol = byProtocol.map { PracticeProtocolResult(it.name.orEmpty(), it.sessions, it.toCounts()) },
    byTarget = byTarget.toTargetResults(),
    notice = notice,
)

private fun PracticeTotalsDto.toDomain() = PracticeTotals(
    applied = applied,
    achieved = achieved,
    skipped = skipped,
    stopped = stopped,
    averageSecondsToAchieve = averageSecondsToAchieve,
    sentence = sentence,
    averageSentence = averageSentence,
    feedbackSentence = feedbackSentence,
)

private fun List<PracticeRowDto>.toTargetResults() =
    mapNotNull { row -> row.targetExpression?.let { PracticeTargetResult(it, row.toCounts()) } }

private fun PracticeRowDto.toCounts() = PracticeCounts(applied, achieved, skipped, stopped, averageSecondsToAchieve)

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
