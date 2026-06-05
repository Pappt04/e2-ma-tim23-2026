package uns.ac.rs.team23.server.dto.auth

import uns.ac.rs.team23.server.model.User

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
    companion object {
        fun from(user: User) = UserResponse(
            id = user.id,
            username = user.username,
            email = user.email,
            region = user.region,
            tokens = user.tokens,
            stars = user.stars,
            leagueLevel = user.leagueLevel,
            avatarIndex = user.avatarIndex,
            isEmailVerified = user.isEmailVerified,
        )
    }
}
