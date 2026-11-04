package pe.unheval.emotv.domain.model

/**
 * Pasos «¿Qué emoción es?» (revisión 4.2): se muestra una imagen y la persona elige qué emoción
 * es; la cámara no se analiza. Describen las respuestas; no valoran a nadie. Los textos vienen del
 * servidor y se muestran tal cual.
 */
data class SessionRecognition(
    val totals: RecognitionTotals,
    val byTarget: List<RecognitionTargetResult>,
    /** Aviso fijo; se muestra siempre junto a los pasos. */
    val notice: String,
)

/** Reconocimientos agregados de varias sesiones; no se desglosan por usuario ni por persona. */
data class StatisticsRecognition(
    /** Sesiones con reconocimientos. */
    val sessions: Int,
    val totals: RecognitionTotals,
    val byTarget: List<RecognitionTargetResult>,
    val notice: String,
)

data class RecognitionTotals(
    /** Pasos que llegaron a su final (respondidos o con el tiempo vencido). */
    val applied: Int,
    /** En cuántos se eligió la emoción de la imagen. */
    val matched: Int,
    /** De los `applied`, sin respuesta. */
    val timedOut: Int,
    val skipped: Int,
    val stopped: Int,
    /** Solo de los respondidos. */
    val averageSecondsToAnswer: Double?,
    val sentence: String,
)

/** Por emoción de la imagen: cuántas veces se eligió y qué se eligió en su lugar. */
data class RecognitionTargetResult(
    val targetExpression: String,
    val applied: Int,
    val matched: Int,
    val timedOut: Int,
    val skipped: Int,
    val stopped: Int,
    val otherChoices: List<RecognitionChoice>,
)

data class RecognitionChoice(val expression: String, val count: Int)

/** Cómo terminó un paso; `null` en el modelo si sigue en curso o la sesión no llegó a él. */
enum class RecognitionOutcome(val apiValue: String) {
    Answered("answered"),
    TimedOut("timed_out"),
    Skipped("skipped"),
    Stopped("stopped"),
    Unknown(""),
    ;

    companion object {
        fun fromApi(value: String?) = value?.let { v -> entries.firstOrNull { it.apiValue == v } ?: Unknown }
    }
}
