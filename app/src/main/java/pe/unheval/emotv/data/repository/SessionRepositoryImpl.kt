package pe.unheval.emotv.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import pe.unheval.emotv.data.remote.CalibrationDto
import pe.unheval.emotv.data.remote.EmotvApi
import pe.unheval.emotv.data.remote.QuestionDto
import pe.unheval.emotv.data.remote.QuestionnaireAnswersDto
import pe.unheval.emotv.data.remote.QuestionnaireDto
import pe.unheval.emotv.data.remote.SessionStepDto
import pe.unheval.emotv.data.remote.StartSessionDto
import pe.unheval.emotv.data.remote.StartSessionRequestDto
import pe.unheval.emotv.data.remote.StimulusDto
import pe.unheval.emotv.data.remote.StopRequestDto
import pe.unheval.emotv.domain.model.Answer
import pe.unheval.emotv.domain.model.Calibration
import pe.unheval.emotv.domain.model.CalibrationState
import pe.unheval.emotv.domain.model.CaptureIssue
import pe.unheval.emotv.domain.model.FrameResult
import pe.unheval.emotv.domain.model.GuidedPhase
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.Question
import pe.unheval.emotv.domain.model.QuestionKind
import pe.unheval.emotv.domain.model.Questionnaire
import pe.unheval.emotv.domain.model.QuestionnaireMoment
import pe.unheval.emotv.domain.model.SessionAudience
import pe.unheval.emotv.domain.model.SessionPlan
import pe.unheval.emotv.domain.model.SessionStep
import pe.unheval.emotv.domain.model.StartedSession
import pe.unheval.emotv.domain.model.Stimulus
import pe.unheval.emotv.domain.model.StimulusKind
import pe.unheval.emotv.domain.model.StopReason
import pe.unheval.emotv.domain.repository.AuthRepository
import pe.unheval.emotv.domain.repository.SessionRepository
import java.io.IOException

/** Las respuestas llevan `Cache-Control: no-store` y el cliente no tiene caché de disco. */
class SessionRepositoryImpl(
    private val api: EmotvApi,
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SessionRepository {

    override suspend fun start(plan: SessionPlan): Outcome<StartedSession> {
        val body = when (plan) {
            is SessionPlan.WithProtocol ->
                StartSessionRequestDto(protocolId = plan.protocolId, authorizationConfirmed = true)
            is SessionPlan.WithoutProtocol ->
                StartSessionRequestDto(sessionType = plan.audience.apiValue, authorizationConfirmed = true)
        }
        return authRepository.authorizedCall { api.startSession(body).toDomain() }
    }

    override suspend fun next(sessionId: String): Outcome<SessionStep> =
        authRepository.authorizedCall { api.next(sessionId).toDomain() }

    override suspend fun submitQuestionnaire(
        sessionId: String,
        moment: QuestionnaireMoment,
        answers: Map<String, Answer>,
    ): Outcome<GuidedPhase?> {
        val body = QuestionnaireAnswersDto(
            answers = JsonObject(answers.mapValues { (_, answer) -> answer.toJson() }),
            entryMode = ENTRY_MODE_PARTICIPANT,
        )
        return authRepository.authorizedCall {
            GuidedPhase.fromApi(api.submitQuestionnaire(sessionId, moment.apiValue, body).phase)
        }
    }

    override suspend fun advance(sessionId: String): Outcome<GuidedPhase?> =
        authRepository.authorizedCall { GuidedPhase.fromApi(api.advance(sessionId).phase) }

    override suspend fun startCalibration(sessionId: String): Outcome<GuidedPhase?> =
        authRepository.authorizedCall { GuidedPhase.fromApi(api.startCalibration(sessionId).phase) }

    override suspend fun sendFrame(sessionId: String, jpeg: ByteArray): Outcome<FrameResult> =
        try {
            // El cuerpo lee directamente de [jpeg], sin copias; no se reintenta más tarde.
            val body = jpeg.toRequestBody(JPEG)
            authRepository.authorizedCall {
                val result = api.sendFrame(sessionId, body)
                FrameResult(
                    phase = GuidedPhase.fromApi(result.phase),
                    captureIssues = result.captureStatus.map(CaptureIssue::fromApi),
                )
            }
        } finally {
            jpeg.fill(0)
        }

    override suspend fun finish(sessionId: String): Outcome<GuidedPhase?> =
        authRepository.authorizedCall { GuidedPhase.fromApi(api.finish(sessionId).phase) }

    override suspend fun stop(sessionId: String, reason: StopReason): Outcome<GuidedPhase?> =
        authRepository.authorizedCall {
            GuidedPhase.fromApi(api.stop(sessionId, StopRequestDto(reason.apiValue)).phase)
        }

    override suspend fun stimulusImage(imageUrl: String): Outcome<ByteArray> =
        authRepository.authorizedCall {
            api.stimulusImage(imageUrl).use { body ->
                withContext(ioDispatcher) {
                    if (body.contentLength() > MAX_STIMULUS_BYTES) throw IOException("Imagen demasiado grande")
                    body.bytes()
                }
            }
        }

    private companion object {
        val JPEG = "image/jpeg".toMediaType()
        const val ENTRY_MODE_PARTICIPANT = "participant"
        const val MAX_STIMULUS_BYTES = 16L * 1024 * 1024
    }
}

private fun Answer.toJson(): JsonElement = when (this) {
    is Answer.Number -> JsonPrimitive(value)
    is Answer.Choice -> JsonPrimitive(option)
    is Answer.Choices -> JsonArray(options.map(::JsonPrimitive))
    is Answer.Text -> JsonPrimitive(text)
}

private fun StartSessionDto.toDomain() = StartedSession(
    sessionId = sessionId,
    code = code,
    audience = SessionAudience.fromApi(audience),
    hasProtocol = hasProtocol,
    reminder = reminder,
)

private fun SessionStepDto.toDomain() = SessionStep(
    sessionId = sessionId,
    code = code,
    phase = GuidedPhase.fromApi(phase),
    stopReason = StopReason.fromApi(stopReason),
    sendFrames = sendFrames,
    frameIntervalMs = frameIntervalMs,
    captureIssues = captureStatus.map(CaptureIssue::fromApi),
    questionnaire = questionnaire?.toDomain(),
    calibration = calibration?.toDomain(),
    stimulus = stimulus?.toDomain(),
    stepCount = stepCount,
)

private fun QuestionnaireDto.toDomain(): Questionnaire {
    // apiCall traduce IllegalArgumentException a AppError.Unexpected.
    val moment = QuestionnaireMoment.fromApi(moment) ?: throw IllegalArgumentException("Momento inválido")
    return Questionnaire(
        moment = moment,
        id = id,
        title = title,
        instructions = instructions,
        answered = answered,
        questions = questions.map { it.toDomain() },
    )
}

private fun QuestionDto.toDomain() = Question(
    id = id,
    kind = QuestionKind.fromApi(kind),
    text = text,
    required = required,
    options = options,
    scalePoints = scalePoints,
    minLabel = minLabel,
    maxLabel = maxLabel,
)

private fun CalibrationDto.toDomain() = Calibration(
    state = CalibrationState.fromApi(state),
    progress = progress.coerceIn(0f, 1f),
    notice = notice,
)

private fun StimulusDto.toDomain() = Stimulus(
    stepIndex = stepIndex,
    kind = StimulusKind.fromApi(kind),
    exposureSeconds = exposureSeconds,
    remainingSeconds = remainingSeconds,
    imageUrl = imageUrl,
    abstractNotice = abstractNotice,
    recordsVerbalResponse = recordsVerbalResponse,
)
