package pe.unheval.emotv.domain.model

/**
 * Detalle de `GET /mobile/sessions/{id}`: línea de tiempo y métricas descriptivas, las mismas
 * de la web. Las expresiones son estimaciones de un modelo; el aplicativo no calcula ni
 * interpreta nada.
 */
data class SessionDetail(
    val session: SessionSummary,
    val durationSeconds: Double,
    /** Ordenados por tiempo; los que no tienen hora van al final. */
    val timeline: List<TimelineEvent>,
    val metrics: SessionMetrics,
)

sealed interface TimelineEvent {
    /** Segundos desde el inicio de la sesión. */
    val time: Double?

    data class Questionnaire(
        override val time: Double?,
        val moment: QuestionnaireMoment,
        val title: String,
        val answers: List<QuestionAnswer>,
    ) : TimelineEvent

    data class Stimulus(override val time: Double?, val title: String, val skipped: Boolean) : TimelineEvent

    /** [expression] es la expresión **estimada**; `null` si no se detectó un rostro. */
    data class Capture(override val time: Double?, val expression: String?, val goodQuality: Boolean) : TimelineEvent

    /** Lo que la persona dijo ver en una imagen abstracta. */
    data class Said(override val time: Double?, val title: String, val text: String) : TimelineEvent

    data class Marker(override val time: Double?, val kind: MarkerKind, val note: String?) : TimelineEvent
}

enum class QuestionnaireMoment { Pre, Post }

/** [answer] es el texto de la respuesta tal como se registró; `null` si no respondió. */
data class QuestionAnswer(val question: String, val answer: String?)

/** Marcas que quien condujo anotó durante la sesión. */
enum class MarkerKind(val apiValue: String) {
    Distracted("distracted"),
    Laughed("laughed"),
    AskedToStop("asked_to_stop"),
    Cried("cried"),
    Note("note"),
    Unknown(""),
    ;

    companion object {
        fun fromApi(value: String) = entries.firstOrNull { it.apiValue == value } ?: Unknown
    }
}

data class SessionMetrics(
    val emotional: List<EmotionalStimulusMetric>,
    val abstract: List<AbstractStimulusMetric>,
    /** Frase del servidor, lista para mostrar. */
    val sentence: String,
    /** Aviso de que una sesión breve no permite concluir; `null` sin imágenes abstractas. */
    val abstractNotice: String?,
)

data class EmotionalStimulusMetric(
    val title: String,
    val skipped: Boolean,
    val expectedExpression: String?,
    /** Tuvo una captura de buena calidad para comparar. */
    val counted: Boolean,
    val matchesExpected: Boolean?,
)

data class AbstractStimulusMetric(val title: String, val skipped: Boolean, val said: String?)
