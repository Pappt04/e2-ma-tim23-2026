package uns.ac.rs.team23.slagalica.data

import android.content.Context
import uns.ac.rs.team23.slagalica.viewmodels.UserSession
import androidx.core.content.edit

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("slagalica_session", Context.MODE_PRIVATE)

    fun save(session: UserSession) {
        prefs.edit {
            when (session) {
                is UserSession.LoggedIn -> {
                    putString("type", "LOGGED_IN")
                    putString("username", session.username)
                    putString("email", session.email)
                }

                UserSession.Guest -> putString("type", "GUEST")
                UserSession.NotLoggedIn -> clear()
            }
        }
    }

    fun restore(): UserSession =
        when (prefs.getString("type", null)) {
            "LOGGED_IN" -> UserSession.LoggedIn(
                username = prefs.getString("username", "") ?: "",
                email = prefs.getString("email", "") ?: "",
            )
            "GUEST" -> UserSession.Guest
            else -> UserSession.NotLoggedIn
        }
}
