package pe.unheval.emotv.domain.model

/** Cuenta que conduce la sesión. [roleName] es «Usuario» o «Docente». */
data class AuthUser(
    val id: String,
    val email: String,
    val role: String,
    val roleName: String,
)
