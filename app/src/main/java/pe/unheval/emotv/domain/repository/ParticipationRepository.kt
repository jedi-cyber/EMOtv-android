package pe.unheval.emotv.domain.repository

import pe.unheval.emotv.domain.model.GameAction
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.ParticipantView

/** Pantalla de la persona: no usa la cuenta ni guarda nada en el dispositivo. */
interface ParticipationRepository {
    /** El enlace de la pantalla de la persona, a cambio del código de un solo uso. */
    suspend fun claim(code: String): Outcome<String>
    suspend fun view(token: String): Outcome<ParticipantView>
    /** Envía solo la emoción elegida. */
    suspend fun answer(token: String, expression: String): Outcome<Unit>
    suspend fun stop(token: String): Outcome<Unit>
    /** Una acción del juego; [expression] es la clave de la emoción al elegir. */
    suspend fun game(token: String, action: GameAction, expression: String? = null): Outcome<Unit>
    /** La imagen de la ronda en curso, en memoria. */
    suspend fun stimulus(token: String): Outcome<ByteArray>
    /** Al buscar la misma emoción: la imagen de una cara de la ronda en curso, en memoria. */
    suspend fun gameOption(token: String, key: String): Outcome<ByteArray>
}
