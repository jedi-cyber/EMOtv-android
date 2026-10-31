package pe.unheval.emotv.domain.model

/**
 * Ejercicios de práctica de producción de expresiones (revisión 4.1): se pide una expresión y se
 * registra si la persona la sostuvo. Describen el ejercicio; no valoran a nadie ni indican su
 * estado emocional. Los textos vienen del servidor y se muestran tal cual.
 */
data class SessionPractice(
    val totals: PracticeTotals,
    val byTarget: List<PracticeTargetResult>,
    /** Aviso fijo sobre la precisión con niños; se muestra siempre junto a los ejercicios. */
    val notice: String,
)

/** Prácticas agregadas de varias sesiones; no se desglosan por usuario ni por persona. */
data class StatisticsPractice(
    /** Sesiones con ejercicios. */
    val sessions: Int,
    val totals: PracticeTotals,
    val byProtocol: List<PracticeProtocolResult>,
    val byTarget: List<PracticeTargetResult>,
    val notice: String,
)

data class PracticeTotals(
    /** Ejercicios que llegaron a su final (logrados o con el tiempo vencido). */
    val applied: Int,
    val achieved: Int,
    val skipped: Int,
    val stopped: Int,
    /** Solo de los logrados. */
    val averageSecondsToAchieve: Double?,
    val sentence: String,
    val averageSentence: String?,
    val feedbackSentence: String?,
)

data class PracticeCounts(
    val applied: Int,
    val achieved: Int,
    val skipped: Int,
    val stopped: Int,
    val averageSecondsToAchieve: Double?,
)

data class PracticeTargetResult(val targetExpression: String, val counts: PracticeCounts)

data class PracticeProtocolResult(val name: String, val sessions: Int, val counts: PracticeCounts)

/** Resultado de un ejercicio; `null` en el modelo si sigue en curso o la sesión no llegó a él. */
enum class PracticeOutcome(val apiValue: String) {
    Achieved("achieved"),
    TimedOut("timed_out"),
    Skipped("skipped"),
    Stopped("stopped"),
    Unknown(""),
    ;

    companion object {
        fun fromApi(value: String?) = value?.let { v -> entries.firstOrNull { it.apiValue == v } ?: Unknown }
    }
}
