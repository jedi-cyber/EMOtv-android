package pe.unheval.emotv.domain.model

/** Lo que muestra la pantalla de la persona en este momento. Vive solo en memoria. */
data class ParticipantView(
    val ended: Boolean,
    val message: String?,
    val activity: ParticipantActivity?,
    val drawing: DrawingStep?,
    val game: MatchingGame? = null,
    /** Cambia con cada imagen: en un juego, con cada ronda. Nulo si no hay imagen. */
    val exposure: String? = null,
    /** Apoyo del protocolo: pictogramas junto a cada emoción. */
    val pictograms: Boolean = false,
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

/** Juegos del servidor; solo [Matching] se juega en Android (4.11). */
enum class GameType {
    Matching, Similar, Other;

    companion object {
        fun from(value: String): GameType = when (value) {
            "matching" -> Matching
            "similar" -> Similar
            else -> Other
        }
    }
}

/** Lo que pasó en la ronda. La lista del servidor es abierta: un valor desconocido es [Other]. */
enum class GameState {
    Playing, Retry, Matched, Shown, Finished, Ended, Other;

    val isPlaying: Boolean get() = this == Playing || this == Retry
    val isRevealed: Boolean get() = this == Matched || this == Shown

    companion object {
        fun from(value: String): GameState = when (value) {
            "playing" -> Playing
            "retry" -> Retry
            "matched" -> Matched
            "shown" -> Shown
            "finished" -> Finished
            "ended" -> Ended
            else -> Other
        }
    }
}

/**
 * Una ronda del juego de emparejar, como la muestra la web: la pregunta, las
 * emociones para tocar y, al terminar la ronda, cuál era con su explicación.
 * Sin puntaje. [answer] solo llega cuando la ronda terminó.
 */
data class MatchingGame(
    val round: String,
    val number: Int,
    val total: Int,
    val type: GameType,
    val question: String,
    val state: GameState,
    val options: List<GameOption>,
    val answer: String?,
    val message: String?,
    val explanation: String?,
    val canHelp: Boolean,
    val lastRound: Boolean,
)

/** [available] es falso si ya la eligió en esta ronda o la quitó la ayuda. */
data class GameOption(val key: String, val label: String, val available: Boolean)

/** Lo que la persona puede hacer en el juego. */
enum class GameAction(val wire: String) { Answer("answer"), Help("help"), Next("next") }
