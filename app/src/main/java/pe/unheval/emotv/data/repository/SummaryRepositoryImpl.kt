package pe.unheval.emotv.data.repository

import pe.unheval.emotv.data.remote.EmotvApi
import pe.unheval.emotv.data.remote.SummaryDto
import pe.unheval.emotv.domain.model.AuthUser
import pe.unheval.emotv.domain.model.DashboardSummary
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.repository.AuthRepository
import pe.unheval.emotv.domain.repository.SummaryRepository
import pe.unheval.emotv.domain.usecase.isDocente
import java.time.LocalDate

class SummaryRepositoryImpl(
    private val api: EmotvApi,
    private val authRepository: AuthRepository,
) : SummaryRepository {

    override suspend fun fetchSummary(date: LocalDate, utcOffsetMinutes: Int): Outcome<DashboardSummary> =
        authRepository.authorizedCall { api.summary(date.toString(), utcOffsetMinutes).toDomain() }
}

private fun SummaryDto.toDomain(): DashboardSummary {
    val account = AuthUser(id = user.id, email = user.email, role = user.role, roleName = user.roleName)
    val docente = account.isDocente()
    return DashboardSummary(
        user = account,
        date = parseDate(date),
        todaySessions = todaySessions.map { it.toDomain(docente) },
        recentSessions = recentSessions.map { it.toDomain(docente) },
        pendingReviews = pendingReviews,
    )
}
