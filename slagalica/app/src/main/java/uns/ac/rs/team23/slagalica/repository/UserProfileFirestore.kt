package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.firestore.DocumentSnapshot
import uns.ac.rs.team23.slagalica.models.UserProfile
import uns.ac.rs.team23.slagalica.models.leagueLevelForStars

/** Maps a Firestore `users/{uid}` document to [UserProfile]. */
fun DocumentSnapshot.toUserProfile(
    uid: String,
    isEmailVerified: Boolean = false,
): UserProfile {
    val stars = (getLong("stars") ?: 0L).toInt()
    return UserProfile(
        id = uid,
        username = getString("username") ?: "",
        email = getString("email") ?: "",
        region = getString("region") ?: "",
        tokens = (getLong("tokens") ?: 5L).toInt(),
        stars = stars,
        cycleStars = (getLong("cycleStars") ?: 0L).toInt(),
        // League is defined by total stars (spec §6); derive so stale Firestore values can't stick.
        leagueLevel = leagueLevelForStars(stars),
        avatarIndex = (getLong("avatarIndex") ?: 0L).toInt(),
        profilePictureUrl = getString("profilePictureUrl") ?: "",
        isEmailVerified = getBoolean("isGuest") == true || isEmailVerified,
    )
}
