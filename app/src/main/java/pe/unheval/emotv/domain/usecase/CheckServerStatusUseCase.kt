package pe.unheval.emotv.domain.usecase

import pe.unheval.emotv.domain.model.AppError
import pe.unheval.emotv.domain.model.AppVersion
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.ServerStatus
import pe.unheval.emotv.domain.repository.StatusRepository

sealed interface StatusCheck {
    data class Ready(val status: ServerStatus) : StatusCheck
    data class UpdateRequired(val minVersion: String) : StatusCheck
    data object LoginUnavailable : StatusCheck
    data class Failed(val error: AppError) : StatusCheck
}

class CheckServerStatusUseCase(
    private val repository: StatusRepository,
    private val appVersionName: String,
) {
    suspend operator fun invoke(): StatusCheck =
        when (val result = repository.fetchStatus()) {
            is Outcome.Failure -> StatusCheck.Failed(result.error)
            is Outcome.Success -> evaluate(result.value)
        }

    private fun evaluate(status: ServerStatus): StatusCheck {
        val current = AppVersion.parse(appVersionName)
        val minimum = AppVersion.parse(status.minAppVersion)
        return when {
            current != null && minimum != null && current < minimum ->
                StatusCheck.UpdateRequired(status.minAppVersion)
            !status.loginAvailable -> StatusCheck.LoginUnavailable
            else -> StatusCheck.Ready(status)
        }
    }
}
