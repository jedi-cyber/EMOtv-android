package pe.unheval.emotv.domain.usecase

import pe.unheval.emotv.domain.model.DashboardSummary
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.repository.SummaryRepository
import java.time.Clock
import java.time.ZonedDateTime

/** Pide el resumen del día de hoy según la fecha y el desfase horario del dispositivo. */
class LoadDashboardUseCase(
    private val summaryRepository: SummaryRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Outcome<DashboardSummary> {
        val now = ZonedDateTime.now(clock)
        val offsetMinutes = (now.offset.totalSeconds / 60).coerceIn(MIN_OFFSET, MAX_OFFSET)
        return summaryRepository.fetchSummary(now.toLocalDate(), offsetMinutes)
    }

    private companion object {
        // Rango que acepta `utc_offset_minutes`.
        const val MIN_OFFSET = -720
        const val MAX_OFFSET = 840
    }
}
