package pe.unheval.emotv.domain.model

/**
 * Modelos de la sesión (docs/mobile-api.md). El aplicativo no lleva lógica propia: muestra
 * lo que indica `GET /mobile/sessions/{id}/next`.
 */

/** Protocolo que la cuenta puede aplicar. Su público decide con quién es la sesión. */
data class SessionProtocol(
    val id: String,
    val name: String,
    val version: Int,
    val forChildren: Boolean,
    val audience: SessionAudience,
    val stepCount: Int,
    val hasPreQuestionnaire: Boolean,
    val hasPostQuestionnaire: Boolean,
    /** Texto de la única casilla de confirmación, tal como lo envía el servidor. */
    val confirmation: String,
)

/** Opción para empezar sin protocolo, con el texto de su confirmación. */
data class AudienceOption(val audience: SessionAudience, val confirmation: String)

/** Qué se aplica: un protocolo, o ninguno eligiendo con quién es la sesión. */
sealed interface SessionPlan {
    data class WithProtocol(val protocolId: String) : SessionPlan
    data class WithoutProtocol(val audience: SessionAudience) : SessionPlan
}

/** Sesión recién creada con `POST /mobile/sessions`, ya con la confirmación enviada. */
data class StartedSession(
    val sessionId: String,
    /** Código aleatorio que quien conduce anota en el formato firmado. */
    val code: String,
    val audience: SessionAudience,
    val hasProtocol: Boolean,
    val reminder: String?,
)

/** Respuesta de `GET .../next`: qué mostrar ahora. */
data class SessionStep(
    val sessionId: String,
    val code: String?,
    /** `null` si el servidor envía una fase que esta versión no conoce: se sigue consultando. */
    val phase: GuidedPhase?,
    val stopReason: StopReason?,
    val sendFrames: Boolean,
    val frameIntervalMs: Long?,
    val captureIssues: List<CaptureIssue>,
    val questionnaire: Questionnaire?,
    val calibration: Calibration?,
    val stimulus: Stimulus?,
    val stepCount: Int?,
) {
    val isTerminal: Boolean get() = phase == GuidedPhase.Finished || phase == GuidedPhase.Stopped
}

enum class QuestionnaireMoment(val apiValue: String) {
    Pre("pre"),
    Post("post"),
    ;

    companion object {
        fun fromApi(value: String) = entries.firstOrNull { it.apiValue == value }
    }
}

/** Cuestionario para mostrar. El aplicativo no puntúa ni interpreta las respuestas. */
data class Questionnaire(
    val moment: QuestionnaireMoment,
    val id: String,
    val title: String,
    val instructions: String?,
    /** Ya se respondió (por ejemplo, en la web): se continúa con `POST .../advance`. */
    val answered: Boolean,
    val questions: List<Question>,
)

data class Question(
    val id: String,
    val kind: QuestionKind,
    val text: String,
    val required: Boolean,
    val options: List<String>,
    val scalePoints: Int?,
    val minLabel: String?,
    val maxLabel: String?,
)

enum class QuestionKind(val apiValue: String) {
    Faces("faces"),
    Scale("scale"),
    SingleChoice("single_choice"),
    MultipleChoice("multiple_choice"),
    ShortText("short_text"),
    Unknown(""),
    ;

    companion object {
        /** Puntos de la escala de caras. */
        const val FACES_POINTS = 5

        fun fromApi(value: String) = entries.firstOrNull { it.apiValue == value } ?: Unknown
    }
}

/** Valor de una respuesta, con el tipo que pide cada `kind`. */
sealed interface Answer {
    /** `faces` (1 a 5) y `scale` (1 a `scale_points`). */
    data class Number(val value: Int) : Answer
    data class Choice(val option: String) : Answer
    data class Choices(val options: List<String>) : Answer
    data class Text(val text: String) : Answer

    val isEmpty: Boolean
        get() = when (this) {
            is Number -> false
            is Choice -> option.isEmpty()
            is Choices -> options.isEmpty()
            is Text -> text.isBlank()
        }
}

data class Calibration(
    val state: CalibrationState,
    val progress: Float,
    /** Texto para repetir la calibración si no se logró. */
    val notice: String?,
)

enum class CalibrationState(val apiValue: String) {
    /** Falta `POST .../calibration`. */
    None("none"),
    Calibrating("calibrating"),
    Calibrated("calibrated"),
    ;

    companion object {
        fun fromApi(value: String) = entries.firstOrNull { it.apiValue == value } ?: None
    }
}

data class Stimulus(
    val stepIndex: Int,
    val kind: StimulusKind,
    val exposureSeconds: Double?,
    val remainingSeconds: Double?,
    /** Ruta relativa al servidor, firmada y válida 120 segundos. */
    val imageUrl: String?,
    val abstractNotice: String?,
    /** Lo que la persona dice ver lo registra quien conduce desde la web. */
    val recordsVerbalResponse: Boolean,
)

enum class StimulusKind(val apiValue: String) {
    Emotional("emotional"),
    Abstract("abstract"),
    Other(""),
    ;

    companion object {
        fun fromApi(value: String?) = entries.firstOrNull { it.apiValue == value } ?: Other
    }
}

/** Problemas de calidad del último cuadro. Nunca la expresión estimada. */
enum class CaptureIssue(val apiValue: String) {
    NoFace("no_face"),
    OffCenter("off_center"),
    TooSmall("too_small"),
    TooDark("too_dark"),
    TooBright("too_bright"),
    Tilted("tilted"),
    NoFrame("no_frame"),
    Other(""),
    ;

    companion object {
        fun fromApi(value: String) = entries.firstOrNull { it.apiValue == value } ?: Other
    }
}

/** Quién pidió interrumpir la sesión (`POST .../stop`). */
enum class StopReason(val apiValue: String) {
    /** La persona evaluada pidió parar (`participant` en la API). */
    Participant("participant"),
    Guardian("guardian"),
    Conductor("conductor"),
    ;

    companion object {
        fun fromApi(value: String?) = entries.firstOrNull { it.apiValue == value }
    }
}

/** Respuesta de `POST .../frames`. */
data class FrameResult(val phase: GuidedPhase?, val captureIssues: List<CaptureIssue>)
