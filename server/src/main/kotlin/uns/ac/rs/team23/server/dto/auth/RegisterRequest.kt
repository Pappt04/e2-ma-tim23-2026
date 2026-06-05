package uns.ac.rs.team23.server.dto.auth

data class RegisterRequest(
    val email: String,
    val username: String,
    val region: String,
    val password: String,
)
