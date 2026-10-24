package pe.unheval.emotv.domain.model

data class ServerStatus(
    val apiVersion: String,
    val minAppVersion: String,
    val loginAvailable: Boolean,
    val participantSessionsAvailable: Boolean,
)
