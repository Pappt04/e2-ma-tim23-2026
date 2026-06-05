package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.models.UserProfile
import uns.ac.rs.team23.slagalica.network.ApiService
import uns.ac.rs.team23.slagalica.network.PersistentCookieJar
import uns.ac.rs.team23.slagalica.network.dto.ChangePasswordRequest
import uns.ac.rs.team23.slagalica.network.dto.LoginRequest
import uns.ac.rs.team23.slagalica.network.dto.RegisterRequest
import uns.ac.rs.team23.slagalica.network.parseErrorMessage

class RemoteAuthRepository(
    private val api: ApiService,
    private val cookieJar: PersistentCookieJar,
) : AuthRepository {

    override suspend fun register(
        email: String,
        username: String,
        region: String,
        password: String,
    ): Result<Unit> = runCatching {
        val response = api.register(RegisterRequest(email, username, region, password))
        if (!response.isSuccessful) {
            throw Exception(parseErrorMessage(response.errorBody()?.string()))
        }
    }

    override suspend fun login(emailOrUsername: String, password: String): Result<UserProfile> =
        runCatching {
            val response = api.login(LoginRequest(emailOrUsername, password))
            if (response.isSuccessful) {
                response.body()!!.toUserProfile()
            } else {
                throw Exception(parseErrorMessage(response.errorBody()?.string()))
            }
        }

    override suspend fun logout(): Result<Unit> = runCatching {
        runCatching { api.logout() }
        cookieJar.clear()
    }

    override suspend fun getProfile(): Result<UserProfile> = runCatching {
        val response = api.getMe()
        if (response.isSuccessful) {
            response.body()!!.toUserProfile()
        } else {
            throw Exception(parseErrorMessage(response.errorBody()?.string()))
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        Result.failure(Exception("Password reset via email is not supported yet"))

    override suspend fun changePassword(
        username: String,
        oldPassword: String,
        newPassword: String,
    ): Result<Unit> = runCatching {
        val response = api.changePassword(ChangePasswordRequest(oldPassword, newPassword))
        if (!response.isSuccessful) {
            throw Exception(parseErrorMessage(response.errorBody()?.string()))
        }
    }

    override suspend fun verifyEmailDev(username: String) = Unit
}
