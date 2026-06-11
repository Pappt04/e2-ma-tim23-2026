package uns.ac.rs.team23.server.dto.auth

data class LoginRequest(
    val emailOrUsername: String,
    val password: String,
)
