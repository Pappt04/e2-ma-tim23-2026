package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.models.UserProfile

interface AuthRepository {
    suspend fun register(email: String, username: String, region: String, password: String): Result<Unit>
    suspend fun login(emailOrUsername: String, password: String): Result<UserProfile>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun changePassword(username: String, oldPassword: String, newPassword: String): Result<Unit>
    /** Dev shortcut — simulates clicking the email verification link. */
    suspend fun verifyEmailDev(username: String)
}
