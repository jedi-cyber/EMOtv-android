package pe.unheval.emotv.data.repository

import pe.unheval.emotv.data.remote.EmotvApi
import pe.unheval.emotv.data.remote.apiCall
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.ServerStatus
import pe.unheval.emotv.domain.repository.StatusRepository

class StatusRepositoryImpl(private val api: EmotvApi) : StatusRepository {
    override suspend fun fetchStatus(): Outcome<ServerStatus> = apiCall {
        val dto = api.status()
        ServerStatus(
            apiVersion = dto.apiVersion,
            minAppVersion = dto.minAppVersion,
            loginAvailable = dto.services.login,
            participantSessionsAvailable = dto.services.participantSessions,
        )
    }
}
