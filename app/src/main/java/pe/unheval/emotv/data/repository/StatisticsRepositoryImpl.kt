package pe.unheval.emotv.data.repository

import pe.unheval.emotv.data.remote.EmotvApi
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.SessionStatistics
import pe.unheval.emotv.domain.model.StatisticsScope
import pe.unheval.emotv.domain.repository.AuthRepository
import pe.unheval.emotv.domain.repository.StatisticsRepository

class StatisticsRepositoryImpl(
    private val api: EmotvApi,
    private val authRepository: AuthRepository,
) : StatisticsRepository {

    override suspend fun fetch(scope: StatisticsScope): Outcome<SessionStatistics> =
        authRepository.authorizedCall { api.statistics(scope.apiValue).toDomain(scope) }
}
