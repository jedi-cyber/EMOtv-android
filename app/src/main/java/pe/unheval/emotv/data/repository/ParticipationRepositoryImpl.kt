package pe.unheval.emotv.data.repository

import pe.unheval.emotv.data.remote.AnswerRequestDto
import pe.unheval.emotv.data.remote.ParticipantStateDto
import pe.unheval.emotv.data.remote.ParticipationApi
import pe.unheval.emotv.data.remote.ParticipationRequestDto
import pe.unheval.emotv.data.remote.apiCall
import pe.unheval.emotv.domain.model.ActivityKind
import pe.unheval.emotv.domain.model.DrawingStep
import pe.unheval.emotv.domain.model.EmotionOption
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.ParticipantActivity
import pe.unheval.emotv.domain.model.ParticipantView
import pe.unheval.emotv.domain.repository.ParticipationRepository

class ParticipationRepositoryImpl(private val api: ParticipationApi) : ParticipationRepository {

    override suspend fun claim(code: String): Outcome<String> =
        apiCall { api.claim(ParticipationRequestDto(code.trim())).participantToken }

    override suspend fun view(token: String): Outcome<ParticipantView> =
        when (val result = apiCall { api.state(token) }) {
            is Outcome.Success -> Outcome.Success(result.value.toDomain())
            is Outcome.Failure -> result
        }

    override suspend fun answer(token: String, expression: String): Outcome<Unit> =
        when (val result = apiCall { api.answer(token, AnswerRequestDto(expression)) }) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> result
        }

    override suspend fun stop(token: String): Outcome<Unit> =
        when (val result = apiCall { api.stop(token) }) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> result
        }
}

internal fun ParticipantStateDto.toDomain(): ParticipantView = ParticipantView(
    ended = phase == "finished" || phase == "stopped",
    message = message,
    activity = activity?.let { ParticipantActivity(ActivityKind.from(it.kind), it.name, it.number, it.total) },
    drawing = drawing?.let { step ->
        DrawingStep(
            instruction = step.instruction,
            question = step.question,
            options = step.options.map { EmotionOption(it.key, it.label) },
            round = step.round,
            finished = step.finished,
            chosen = step.chosen,
            message = step.message,
        )
    },
)
