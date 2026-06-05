package uns.ac.rs.team23.slagalica.network.dto

import uns.ac.rs.team23.slagalica.models.UserProfile

data class RegisterRequest(
    val email: String,
    val username: String,
    val region: String,
    val password: String,
)

data class LoginRequest(
    val emailOrUsername: String,
    val password: String,
)

data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String,
)

data class UserResponse(
    val id: Long,
    val username: String,
    val email: String,
    val region: String,
    val tokens: Int,
    val stars: Int,
    val leagueLevel: Int,
    val avatarIndex: Int,
    val isEmailVerified: Boolean,
) {
    fun toUserProfile() = UserProfile(
        username = username,
        email = email,
        region = region,
        tokens = tokens,
        stars = stars,
        leagueLevel = leagueLevel,
        avatarIndex = avatarIndex,
        isEmailVerified = isEmailVerified,
    )
}
