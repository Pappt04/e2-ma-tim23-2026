package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.models.UserProfile

interface AuthRepository {
    suspend fun register(email: String, username: String, region: String, password: String): Result<Unit>
    suspend fun login(emailOrUsername: String, password: String): Result<UserProfile>
    suspend fun loginAsGuest(): Result<UserProfile>
    suspend fun logout(): Result<Unit>
    suspend fun getProfile(): Result<UserProfile>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun changePassword(username: String, oldPassword: String, newPassword: String): Result<Unit>

    /** Persist the user's chosen avatar variant. */
    suspend fun updateAvatar(avatarIndex: Int): Result<Unit>

    /** Write a presence heartbeat (onlineAt) for the current user. */
    suspend fun updatePresence(): Result<Unit>
}
