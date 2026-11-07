package pe.unheval.emotv.domain.repository

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
}
