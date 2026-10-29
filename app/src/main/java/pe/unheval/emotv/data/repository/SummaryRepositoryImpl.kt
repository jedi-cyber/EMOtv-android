package pe.unheval.emotv.data.repository

import pe.unheval.emotv.data.remote.EmotvApi
import pe.unheval.emotv.data.remote.SummaryDto
import pe.unheval.emotv.domain.model.AuthUser
import pe.unheval.emotv.domain.model.DashboardSummary
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.repository.AuthRepository
import pe.unheval.emotv.domain.repository.SummaryRepository
import java.time.LocalDate

class SummaryRepositoryImpl(
    private val api: EmotvApi,
    private val authRepository: AuthRepository,
) : SummaryRepository {

    override suspend fun fetchSummary(date: LocalDate, utcOffsetMinutes: Int): Outcome<DashboardSummary> =
        authRepository.authorizedCall { api.summary(date.toString(), utcOffsetMinutes).toDomain() }
}

private fun SummaryDto.toDomain() = DashboardSummary(
    user = AuthUser(id = user.id, email = user.email, role = user.role, roleName = user.roleName),
    date = parseDate(date),
    todaySessions = todaySessions.map { it.toDomain() },
    recentSessions = recentSessions.map { it.toDomain() },
    pendingReviews = pendingReviews,
)
