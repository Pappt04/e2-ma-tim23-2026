package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.models.UserProfile
import uns.ac.rs.team23.slagalica.models.dailyTokensForLeague
import uns.ac.rs.team23.slagalica.models.leagueLevelForStars
import java.time.LocalDate

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {

    override suspend fun register(
        email: String,
        username: String,
        region: String,
        password: String,
    ): Result<Unit> = runCatching {
        // Username uniqueness check — usernames collection is publicly readable
        val usernameDoc = firestore.collection("usernames").document(username).get().await()
        if (usernameDoc.exists()) throw Exception("Username is already taken")

        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user ?: throw Exception("Registration failed")

        user.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(username).build()
        ).await()

        val uid = user.uid
        val today = java.time.LocalDate.now().toString()

        firestore.runBatch { batch ->
            batch.set(
                firestore.collection("users").document(uid),
                mapOf(
                    "uid" to uid,
                    "username" to username,
                    "email" to email,
                    "region" to region,
                    "tokens" to 5,
                    "stars" to 0,
                    "cycleStars" to 0,
                    "weeklyCycleStars" to 0,
                    "totalStarsEarned" to 0,
                    "leagueLevel" to 0,
                    "avatarIndex" to 0,
                    "isGuest" to false,
                    "createdAt" to Timestamp.now(),
                    "lastTokenGranted" to today,
                    "fcmToken" to "",
                    "onlineAt" to System.currentTimeMillis(),
                    "sessionActive" to true,
                )
            )
            // Store email here so login-by-username doesn't need a second pre-auth read
            batch.set(
                firestore.collection("usernames").document(username),
                mapOf("uid" to uid, "email" to email)
            )
        }.await()

        user.sendEmailVerification().await()
    }

    override suspend fun login(emailOrUsername: String, password: String): Result<UserProfile> =
        runCatching {
            val email = if (emailOrUsername.contains("@")) {
                emailOrUsername
            } else {
                // usernames is publicly readable — single pre-auth read to get the email
                val doc = firestore.collection("usernames").document(emailOrUsername).get().await()
                doc.getString("email") ?: throw Exception("User not found")
            }

            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("Login failed")

            if (!user.isEmailVerified) {
                auth.signOut()
                throw Exception("Email address is not verified. Check your inbox and click the link.")
            }

            grantDailyTokensIfNeeded(user.uid)
            firestore.collection("users").document(user.uid)
                .update("sessionActive", true)
                .await()
            readProfile(user.uid)
        }

    override suspend fun loginAsGuest(): Result<UserProfile> = runCatching {
        val result = auth.signInAnonymously().await()
        val user = result.user ?: throw Exception("Guest login failed")
        val uid = user.uid

        firestore.collection("users").document(uid).set(
            mapOf(
                "uid" to uid,
                "username" to "Guest_${uid.take(6)}",
                "email" to "",
                "region" to "",
                "tokens" to 0,
                "stars" to 0,
                "cycleStars" to 0,
                "weeklyCycleStars" to 0,
                "totalStarsEarned" to 0,
                "leagueLevel" to 0,
                "avatarIndex" to 0,
                "isGuest" to true,
                "createdAt" to Timestamp.now(),
                "lastTokenGranted" to java.time.LocalDate.now().toString(),
                "fcmToken" to "",
            )
        ).await()

        readProfile(uid)
    }

    override suspend fun logout(): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            firestore.collection("users").document(uid)
                .update("sessionActive", false)
                .await()
        }
        auth.signOut()
    }

    override suspend fun getProfile(): Result<UserProfile> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")
        if (auth.currentUser?.isAnonymous != true) {
            grantDailyTokensIfNeeded(uid)
        }
        readProfile(uid)
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).await()
    }

    override suspend fun changePassword(
        username: String,
        oldPassword: String,
        newPassword: String,
    ): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw Exception("Not logged in")
        val email = user.email ?: throw Exception("Not logged in")
        val credential = EmailAuthProvider.getCredential(email, oldPassword)
        user.reauthenticate(credential).await()
        user.updatePassword(newPassword).await()
    }

    override suspend fun updateAvatar(avatarIndex: Int): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")
        firestore.collection("users").document(uid)
            .update("avatarIndex", avatarIndex)
            .await()
    }

    override suspend fun updatePresence(): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        // inMatch is derived from the active-match holder, which is cleared on every
        // match exit (finish/forfeit), so presence self-heals each heartbeat.
        firestore.collection("users").document(uid)
            .update(
                mapOf(
                    "onlineAt" to System.currentTimeMillis(),
                    "inMatch" to uns.ac.rs.team23.slagalica.data.MatchStore.matchId.isNotBlank(),
                )
            )
            .await()
    }

    override suspend fun refreshDailyTokensIfNeeded(): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        if (auth.currentUser?.isAnonymous == true) return@runCatching
        grantDailyTokensIfNeeded(uid)
    }

    private suspend fun grantDailyTokensIfNeeded(uid: String) {
        val ref = firestore.collection("users").document(uid)
        val today = LocalDate.now().toString()
        firestore.runTransaction { tx ->
            val doc = tx.get(ref)
            if (doc.getBoolean("isGuest") == true) return@runTransaction
            val last = doc.getString("lastTokenGranted") ?: ""
            if (last >= today) return@runTransaction
            val stars = (doc.getLong("stars") ?: 0L).toInt()
            val league = leagueLevelForStars(stars)
            val storedLeague = (doc.getLong("leagueLevel") ?: 0L).toInt()
            val grant = dailyTokensForLeague(league)
            val tokens = (doc.getLong("tokens") ?: 0L).toInt() + grant
            val updates = mutableMapOf<String, Any>(
                "tokens" to tokens,
                "lastTokenGranted" to today,
            )
            if (storedLeague != league) updates["leagueLevel"] = league
            tx.update(ref, updates)
        }.await()
    }

    private suspend fun readProfile(uid: String): UserProfile {
        val ref = firestore.collection("users").document(uid)
        val doc = ref.get().await()
        val stars = (doc.getLong("stars") ?: 0L).toInt()
        val computedLeague = leagueLevelForStars(stars)
        val storedLeague = (doc.getLong("leagueLevel") ?: 0L).toInt()
        if (storedLeague != computedLeague) {
            ref.update("leagueLevel", computedLeague).await()
        }
        return doc.toUserProfile(uid, auth.currentUser?.isEmailVerified == true)
    }
}
