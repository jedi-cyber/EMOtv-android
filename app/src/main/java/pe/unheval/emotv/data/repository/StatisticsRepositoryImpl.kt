package pe.unheval.emotv.data.repository

import pe.unheval.emotv.data.remote.EmotvApi
import pe.unheval.emotv.data.remote.StatisticsDto
import pe.unheval.emotv.domain.model.ExpressionShare
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

private fun StatisticsDto.toDomain(scope: StatisticsScope) = SessionStatistics(
    scope = scope,
    notice = notice,
    fewSessionsNotice = fewSessionsNotice,
    total = sessions.total,
    completed = sessions.completed,
    interrupted = sessions.interrupted,
    averageDurationSeconds = sessions.averageDurationSeconds,
    countedCaptures = expressions.countedCaptures,
    excludedCaptures = expressions.excludedCaptures,
    expressions = expressions.rows.map { ExpressionShare(it.expression, it.captures, it.percent, it.sessions) },
    compared = coincidence.compared,
    matched = coincidence.matched,
)
