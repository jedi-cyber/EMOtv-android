package pe.unheval.emotv.data.repository

import pe.unheval.emotv.data.remote.EmotvApi
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.SessionDetail
import pe.unheval.emotv.domain.model.SessionSummary
import pe.unheval.emotv.domain.repository.AuthRepository
import pe.unheval.emotv.domain.repository.SessionRepository

class SessionRepositoryImpl(
    private val api: EmotvApi,
    private val authRepository: AuthRepository,
) : SessionRepository {

    override suspend fun list(): Outcome<List<SessionSummary>> =
        authRepository.authorizedCall { api.sessions().map { it.toDomain() } }

    override suspend fun detail(sessionId: String): Outcome<SessionDetail> =
        authRepository.authorizedCall { api.sessionDetail(sessionId).toDomain() }
}
