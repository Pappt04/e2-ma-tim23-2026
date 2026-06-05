package uns.ac.rs.team23.slagalica.data

import android.content.Context
import androidx.core.content.edit
import uns.ac.rs.team23.slagalica.models.UserProfile

class UserProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("slagalica_users_db", Context.MODE_PRIVATE)

    private fun k(username: String, field: String) = "u_${username}_$field"

    fun register(profile: UserProfile, passwordHash: String): Boolean {
        val all = registeredUsernames()
        if (all.any { it.equals(profile.username, ignoreCase = true) }) return false
        if (all.any { emailOf(it) == profile.email }) return false
        prefs.edit {
            putString(k(profile.username, "email"), profile.email)
            putString(k(profile.username, "region"), profile.region)
            putString(k(profile.username, "passwordHash"), passwordHash)
            putInt(k(profile.username, "tokens"), 5)
            putInt(k(profile.username, "stars"), 0)
            putInt(k(profile.username, "leagueLevel"), 0)
            putInt(k(profile.username, "avatarIndex"), 0)
            putBoolean(k(profile.username, "isEmailVerified"), false)
            putLong(k(profile.username, "lastDailyTime"), 0L)
            putString("emailmap_${profile.email}", profile.username)
            val updated = (all + profile.username).joinToString(",")
            putString("registered_users", updated)
        }
        return true
    }

    fun getByUsername(username: String): UserProfile? {
        if (!registeredUsernames().contains(username)) return null
        return buildProfile(username)
    }

    fun getByEmail(email: String): UserProfile? {
        val username = prefs.getString("emailmap_$email", null) ?: return null
        return buildProfile(username)
    }

    fun markEmailVerified(username: String) {
        prefs.edit { putBoolean(k(username, "isEmailVerified"), true) }
    }

    fun updatePasswordHash(username: String, hash: String) {
        prefs.edit { putString(k(username, "passwordHash"), hash) }
    }

    fun updateProfile(profile: UserProfile) {
        prefs.edit {
            putInt(k(profile.username, "tokens"), profile.tokens)
            putInt(k(profile.username, "stars"), profile.stars)
            putInt(k(profile.username, "leagueLevel"), profile.leagueLevel)
            putInt(k(profile.username, "avatarIndex"), profile.avatarIndex)
        }
    }

    fun tryClaimDailyTokens(username: String, bonusTokens: Int): Boolean {
        val last = prefs.getLong(k(username, "lastDailyTime"), 0L)
        if (System.currentTimeMillis() - last < 24 * 60 * 60 * 1000L) return false
        val cur = prefs.getInt(k(username, "tokens"), 0)
        prefs.edit {
            putInt(k(username, "tokens"), cur + bonusTokens)
            putLong(k(username, "lastDailyTime"), System.currentTimeMillis())
        }
        return true
    }

    private fun registeredUsernames(): List<String> {
        val s = prefs.getString("registered_users", "") ?: ""
        return if (s.isEmpty()) emptyList() else s.split(",").filter { it.isNotEmpty() }
    }

    private fun emailOf(username: String): String? = prefs.getString(k(username, "email"), null)

    private fun buildProfile(username: String) = UserProfile(
        username = username,
        email = prefs.getString(k(username, "email"), "") ?: "",
        region = prefs.getString(k(username, "region"), "") ?: "",
        tokens = prefs.getInt(k(username, "tokens"), 5),
        stars = prefs.getInt(k(username, "stars"), 0),
        leagueLevel = prefs.getInt(k(username, "leagueLevel"), 0),
        avatarIndex = prefs.getInt(k(username, "avatarIndex"), 0),
        isEmailVerified = prefs.getBoolean(k(username, "isEmailVerified"), false),
        passwordHash = prefs.getString(k(username, "passwordHash"), "") ?: "",
    )
}
