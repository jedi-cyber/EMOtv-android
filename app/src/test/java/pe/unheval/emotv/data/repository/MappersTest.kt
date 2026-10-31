package pe.unheval.emotv.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pe.unheval.emotv.data.remote.ApiJson
import pe.unheval.emotv.data.remote.SessionDetailDto
import pe.unheval.emotv.data.remote.StatisticsDto
import pe.unheval.emotv.domain.model.PracticeOutcome
import pe.unheval.emotv.domain.model.StatisticsScope
import pe.unheval.emotv.domain.model.TimelineEvent

/** Respuestas con la forma de docs/mobile-api.md, revisiones 4 y 4.1. */
class MappersTest {

    private val session = """{"id": "s-1", "code": "S-4F7K2Q", "started_at": "2026-10-30T15:00:00+00:00",
        "state": "completed", "audience": "infantil", "mine": true}"""

    private val notice = "Los modelos de expresión se entrenaron principalmente con rostros de adultos."

    private fun detail(metricsExtra: String, stimuli: String = "[]") = ApiJson.decodeFromString(
        SessionDetailDto.serializer(),
        """{"session": $session,
            "timeline": {"duration_seconds": 30.0, "lanes": {"stimuli": $stimuli, "heart_rate": []}},
            "metrics": {"emotional": [], "abstract": [], "abstract_notice": null,
                        "totals": {"sentence": "En 1 de 1 estímulo la expresión estimada coincidió con la esperada."}
                        $metricsExtra}}""",
    ).toDomain()

    private fun statistics(extra: String) = ApiJson.decodeFromString(
        StatisticsDto.serializer(),
        """{"scope": "mine", "notice": "Cuentan sesiones, no personas.", "few_sessions_notice": null,
            "sessions": {"total": 2, "completed": 2, "interrupted": 0, "average_duration_seconds": 60.0},
            "expressions": {"counted_captures": 0, "excluded_captures": 0, "rows": []},
            "coincidence": {"compared": 4, "matched": 3} $extra}""",
    ).toDomain(StatisticsScope.Mine)

    @Test
    fun `un servidor de la revisión 4 no envía práctica y el detalle sigue igual`() {
        val detail = detail(metricsExtra = "")

        assertNull(detail.metrics.practice)
        assertEquals("En 1 de 1 estímulo la expresión estimada coincidió con la esperada.", detail.metrics.sentence)
    }

    @Test
    fun `práctica nula o sin ejercicios no crea la sección`() {
        assertNull(detail(""", "practice": null""").metrics.practice)
        assertNull(
            detail(
                """, "practice": {"exercises": [], "by_target": [], "notice": "$notice",
                    "totals": {"applied": 0, "achieved": 0, "skipped": 0, "stopped": 0, "feedback_visible": 0,
                               "sentence": ""}}""",
            ).metrics.practice,
        )
    }

    @Test
    fun `la práctica de la sesión trae las frases, el desglose y el aviso`() {
        val practice = checkNotNull(
            detail(
                """, "practice": {
                    "exercises": [{"step_index": 1, "target_expression": "happiness", "hold_seconds": 2,
                                   "max_seconds": 15, "outcome": "achieved", "seconds_to_achieve": 4.5,
                                   "peak_level": 0.86, "relative_to_calibration": true, "feedback_visible": true}],
                    "totals": {"exercises": 1, "applied": 1, "achieved": 1, "skipped": 0, "stopped": 0,
                               "not_reached": 0, "average_seconds_to_achieve": 4.5, "feedback_visible": 1,
                               "sentence": "Logró la expresión pedida en 1 de 1 ejercicio.",
                               "average_sentence": "Tiempo promedio hasta lograrla: 4,5 s (solo los ejercicios logrados).",
                               "feedback_sentence": null},
                    "by_target": [{"target_expression": "happiness", "applied": 1, "achieved": 1, "skipped": 0,
                                   "stopped": 0, "average_seconds_to_achieve": 4.5}],
                    "notice": "$notice"}""",
            ).metrics.practice,
        )

        assertEquals("Logró la expresión pedida en 1 de 1 ejercicio.", practice.totals.sentence)
        assertEquals("Tiempo promedio hasta lograrla: 4,5 s (solo los ejercicios logrados).", practice.totals.averageSentence)
        assertNull(practice.totals.feedbackSentence)
        val row = practice.byTarget.single()
        assertEquals("happiness", row.targetExpression)
        assertEquals(1, row.counts.achieved)
        assertEquals(4.5, row.counts.averageSecondsToAchieve!!, 0.0)
        assertEquals(notice, practice.notice)
    }

    @Test
    fun `los ejercicios de la línea de tiempo se distinguen de los estímulos`() {
        val timeline = detail(
            metricsExtra = "",
            stimuli = """[{"step_index": 0, "title": "Paisaje", "kind": "emotional", "start": 8.1, "end": 14.1,
                           "skipped": false},
                          {"step_index": 1, "title": "", "kind": "practice", "start": 14.1, "end": 21.3,
                           "skipped": false, "target_expression": "happiness", "outcome": "achieved",
                           "seconds_to_achieve": 4.5},
                          {"step_index": 2, "title": "", "kind": "practice", "start": 22.0, "skipped": false,
                           "target_expression": "surprise", "outcome": null}]""",
        ).timeline

        assertEquals(TimelineEvent.Stimulus(8.1, "Paisaje", skipped = false), timeline[0])
        assertEquals(TimelineEvent.Practice(14.1, "happiness", PracticeOutcome.Achieved, 4.5), timeline[1])
        assertEquals(TimelineEvent.Practice(22.0, "surprise", outcome = null, secondsToAchieve = null), timeline[2])
    }

    @Test
    fun `un resultado desconocido no rompe el detalle`() {
        assertEquals(PracticeOutcome.Unknown, PracticeOutcome.fromApi("nuevo_valor"))
        assertNull(PracticeOutcome.fromApi(null))
    }

    @Test
    fun `estadísticas sin práctica conservan la coincidencia`() {
        val missing = statistics(extra = "")
        val empty = statistics(""", "practice": null""")

        assertNull(missing.practice)
        assertNull(empty.practice)
        assertEquals(3, missing.matched)
        assertEquals(4, missing.compared)
    }

    @Test
    fun `estadísticas de práctica agregadas, aparte de la coincidencia`() {
        val statistics = statistics(
            """, "practice": {"sessions": 2,
                "totals": {"applied": 3, "achieved": 2, "skipped": 1, "stopped": 0, "average_seconds_to_achieve": 3.0,
                           "feedback_visible": 2, "sentence": "Logró la expresión pedida en 2 de 3 ejercicios.",
                           "average_sentence": "Tiempo promedio hasta lograrla: 3 s (solo los ejercicios logrados).",
                           "feedback_sentence": "La señal visual estuvo activa en 2 de 3 ejercicios."},
                "by_protocol": [{"family_id": "f-1", "name": "Alegría y sorpresa", "sessions": 2, "applied": 3,
                                 "achieved": 2, "skipped": 1, "stopped": 0, "average_seconds_to_achieve": 3.0}],
                "by_target": [{"target_expression": "happiness", "applied": 2, "achieved": 2, "skipped": 0,
                               "stopped": 0, "average_seconds_to_achieve": 3.0},
                              {"target_expression": "surprise", "applied": 1, "achieved": 0, "skipped": 1,
                               "stopped": 0, "average_seconds_to_achieve": null}],
                "notice": "$notice"}""",
        )

        val practice = checkNotNull(statistics.practice)
        assertEquals(2, practice.sessions)
        assertEquals("Logró la expresión pedida en 2 de 3 ejercicios.", practice.totals.sentence)
        assertEquals("La señal visual estuvo activa en 2 de 3 ejercicios.", practice.totals.feedbackSentence)
        assertEquals("Alegría y sorpresa", practice.byProtocol.single().name)
        assertEquals(2, practice.byProtocol.single().sessions)
        assertEquals(listOf("happiness", "surprise"), practice.byTarget.map { it.targetExpression })
        assertNull(practice.byTarget[1].counts.averageSecondsToAchieve)
        assertEquals(notice, practice.notice)
        // La coincidencia con estímulos no se mezcla con la práctica.
        assertEquals(3, statistics.matched)
    }
}
