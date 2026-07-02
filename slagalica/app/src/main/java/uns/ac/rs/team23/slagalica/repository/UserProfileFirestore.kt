package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.firestore.DocumentSnapshot
import uns.ac.rs.team23.slagalica.models.UserProfile

/** Maps a Firestore `users/{uid}` document to [UserProfile]. */
fun DocumentSnapshot.toUserProfile(
    uid: String,
    isEmailVerified: Boolean = false,
): UserProfile = UserProfile(
    id = uid,
    username = getString("username") ?: "",
    email = getString("email") ?: "",
    region = getString("region") ?: "",
    tokens = (getLong("tokens") ?: 5L).toInt(),
    stars = (getLong("stars") ?: 0L).toInt(),
    cycleStars = (getLong("cycleStars") ?: 0L).toInt(),
    leagueLevel = (getLong("leagueLevel") ?: 0L).toInt(),
    avatarIndex = (getLong("avatarIndex") ?: 0L).toInt(),
    profilePictureUrl = getString("profilePictureUrl") ?: "",
    isEmailVerified = getBoolean("isGuest") == true || isEmailVerified,
)
