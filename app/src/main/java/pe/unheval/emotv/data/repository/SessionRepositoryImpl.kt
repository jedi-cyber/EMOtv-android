package pe.unheval.emotv.data.repository

import pe.unheval.emotv.data.remote.EmotvApi
import pe.unheval.emotv.data.remote.MetricsDto
import pe.unheval.emotv.data.remote.SessionDetailDto
import pe.unheval.emotv.domain.model.AbstractStimulusMetric
import pe.unheval.emotv.domain.model.EmotionalStimulusMetric
import pe.unheval.emotv.domain.model.MarkerKind
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.QuestionAnswer
import pe.unheval.emotv.domain.model.QuestionnaireMoment
import pe.unheval.emotv.domain.model.SessionDetail
import pe.unheval.emotv.domain.model.SessionMetrics
import pe.unheval.emotv.domain.model.SessionSummary
import pe.unheval.emotv.domain.model.TimelineEvent
import pe.unheval.emotv.domain.repository.AuthRepository
import pe.unheval.emotv.domain.repository.SessionRepository

class SessionRepositoryImpl(
    private val api: EmotvApi,
    private val authRepository: AuthRepository,
) : SessionRepository {

    override suspend fun list(): Outcome<List<SessionSummary>> =
        authRepository.authorizedCall { api.sessions().map { it.toDomain() } }

    override suspend fun detail(sessionId: String): Outcome<SessionDetail> =
        authRepository.authorizedCall { api.sessionDetail(sessionId).toDomain() }
}

private fun SessionDetailDto.toDomain(): SessionDetail {
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
        lanes.stimuli.forEach { add(TimelineEvent.Stimulus(it.start, it.title, it.skipped)) }
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

private fun MetricsDto.toDomain() = SessionMetrics(
    emotional = emotional.map {
        EmotionalStimulusMetric(it.title, it.skipped, it.expectedExpression, it.counted, it.matchesExpected)
    },
    abstract = abstract.map { AbstractStimulusMetric(it.title, it.skipped, it.said) },
    sentence = totals.sentence,
    abstractNotice = abstractNotice,
)
