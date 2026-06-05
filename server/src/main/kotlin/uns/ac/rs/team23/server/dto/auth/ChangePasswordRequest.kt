package uns.ac.rs.team23.server.dto.auth

data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String,
)
