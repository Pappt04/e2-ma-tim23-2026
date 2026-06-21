package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.models.UserProfile
import uns.ac.rs.team23.slagalica.models.dailyTokensForLeague
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
        if (usernameDoc.exists()) throw Exception("Korisničko ime je već zauzeto")

        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user ?: throw Exception("Registracija nije uspela")

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
                doc.getString("email") ?: throw Exception("Korisnik nije pronađen")
            }

            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("Prijavljivanje nije uspelo")

            if (!user.isEmailVerified) {
                auth.signOut()
                throw Exception("Email adresa nije potvrđena. Proverite poštu i kliknite na link.")
            }

            grantDailyTokensIfNeeded(user.uid)
            readProfile(user.uid)
        }

    override suspend fun loginAsGuest(): Result<UserProfile> = runCatching {
        val result = auth.signInAnonymously().await()
        val user = result.user ?: throw Exception("Gost prijava nije uspela")
        val uid = user.uid

        firestore.collection("users").document(uid).set(
            mapOf(
                "uid" to uid,
                "username" to "Gost_${uid.take(6)}",
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
        auth.signOut()
    }

    override suspend fun getProfile(): Result<UserProfile> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Nije prijavljen")
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
        val user = auth.currentUser ?: throw Exception("Nije prijavljen")
        val email = user.email ?: throw Exception("Nije prijavljen")
        val credential = EmailAuthProvider.getCredential(email, oldPassword)
        user.reauthenticate(credential).await()
        user.updatePassword(newPassword).await()
    }

    override suspend fun updateAvatar(avatarIndex: Int): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Nije prijavljen")
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

    private suspend fun grantDailyTokensIfNeeded(uid: String) {
        val ref = firestore.collection("users").document(uid)
        val today = LocalDate.now().toString()
        firestore.runTransaction { tx ->
            val doc = tx.get(ref)
            if (doc.getBoolean("isGuest") == true) return@runTransaction
            val last = doc.getString("lastTokenGranted") ?: ""
            if (last >= today) return@runTransaction
            val league = (doc.getLong("leagueLevel") ?: 0L).toInt()
            val grant = dailyTokensForLeague(league)
            val tokens = (doc.getLong("tokens") ?: 0L).toInt() + grant
            tx.update(ref, mapOf("tokens" to tokens, "lastTokenGranted" to today))
        }.await()
    }

    private suspend fun readProfile(uid: String): UserProfile {
        val doc = firestore.collection("users").document(uid).get().await()
        return UserProfile(
            id = uid,
            username = doc.getString("username") ?: "",
            email = doc.getString("email") ?: "",
            region = doc.getString("region") ?: "",
            tokens = (doc.getLong("tokens") ?: 5L).toInt(),
            stars = (doc.getLong("stars") ?: 0L).toInt(),
            cycleStars = (doc.getLong("cycleStars") ?: 0L).toInt(),
            leagueLevel = (doc.getLong("leagueLevel") ?: 0L).toInt(),
            avatarIndex = (doc.getLong("avatarIndex") ?: 0L).toInt(),
            isEmailVerified = doc.getBoolean("isGuest") == true || auth.currentUser?.isEmailVerified == true,
        )
    }
}
