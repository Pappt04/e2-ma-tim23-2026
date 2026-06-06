package uns.ac.rs.team23.slagalica.repository

import kotlinx.coroutines.delay
import uns.ac.rs.team23.slagalica.data.UserProfileStore
import uns.ac.rs.team23.slagalica.models.UserProfile

/**
 * Local (SharedPreferences) implementation of [AuthRepository].
 * Used for offline testing; replaced by [RemoteAuthRepository] in production.
 */
class LocalAuthRepository(private val store: UserProfileStore) : AuthRepository {

    private fun hash(password: String): String = password.hashCode().toString()
    private var currentUsername: String? = null

    override suspend fun register(
        email: String,
        username: String,
        region: String,
        password: String,
    ): Result<Unit> {
        delay(400)
        val profile = UserProfile(username = username, email = email, region = region)
        return if (store.register(profile, hash(password))) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Korisničko ime ili email je već zauzet"))
        }
    }

    override suspend fun login(emailOrUsername: String, password: String): Result<UserProfile> {
        delay(400)
        val user = if (emailOrUsername.contains("@")) {
            store.getByEmail(emailOrUsername)
        } else {
            store.getByUsername(emailOrUsername)
        } ?: return Result.failure(Exception("Korisnik nije pronađen"))

        if (user.passwordHash != hash(password)) {
            return Result.failure(Exception("Pogrešna lozinka"))
        }
        if (!user.isEmailVerified) {
            return Result.failure(Exception("Potvrdite email adresu pre logovanja"))
        }
        currentUsername = user.username
        return Result.success(user)
    }

    override suspend fun loginAsGuest(): Result<UserProfile> {
        val profile = UserProfile(id = -1, username = "Guest", email = "", region = "")
        return Result.success(profile)
    }

    override suspend fun logout(): Result<Unit> {
        currentUsername = null
        return Result.success(Unit)
    }

    override suspend fun getProfile(): Result<UserProfile> {
        val username = currentUsername ?: return Result.failure(Exception("Not logged in"))
        return store.getByUsername(username)?.let { Result.success(it) }
            ?: Result.failure(Exception("User not found"))
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        delay(400)
        store.getByEmail(email)
            ?: return Result.failure(Exception("Nije pronađen nalog sa ovim emailom"))
        return Result.success(Unit)
    }

    override suspend fun changePassword(
        username: String,
        oldPassword: String,
        newPassword: String,
    ): Result<Unit> {
        delay(300)
        val user = store.getByUsername(username)
            ?: return Result.failure(Exception("Korisnik nije pronađen"))
        if (user.passwordHash != hash(oldPassword)) {
            return Result.failure(Exception("Trenutna lozinka nije ispravna"))
        }
        store.updatePasswordHash(username, hash(newPassword))
        return Result.success(Unit)
    }

    override suspend fun verifyEmailDev(username: String) {
        store.markEmailVerified(username)
    }
}
