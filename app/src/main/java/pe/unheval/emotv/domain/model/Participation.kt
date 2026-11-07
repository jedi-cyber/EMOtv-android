package pe.unheval.emotv.domain.model

/** Lo que muestra la pantalla de la persona en este momento. Vive solo en memoria. */
data class ParticipantView(
    val ended: Boolean,
    val message: String?,
    val activity: ParticipantActivity?,
    val drawing: DrawingStep?,
)

data class ParticipantActivity(
    val kind: ActivityKind,
    val name: String,
    val number: Int?,
    val total: Int?,
)

/** La lista del servidor es abierta: un valor desconocido es [Other]. */
enum class ActivityKind {
    Waiting, Questionnaire, Stimulus, Practice, Recognition, Drawing, Game, Live, Finished, Other;

    companion object {
        fun from(value: String): ActivityKind = when (value) {
            "waiting" -> Waiting
            "questionnaire" -> Questionnaire
            "stimulus" -> Stimulus
            "practice" -> Practice
            "recognition" -> Recognition
            "drawing" -> Drawing
            "game" -> Game
            "live" -> Live
            "finished" -> Finished
            else -> Other
        }
    }
}

/** Un paso de dibujo: la consigna, la pregunta y las emociones para señalar. */
data class DrawingStep(
    val instruction: String,
    val question: String,
    val options: List<EmotionOption>,
    val round: String,
    val finished: Boolean,
    val chosen: String?,
    val message: String?,
)

data class EmotionOption(val key: String, val label: String)
