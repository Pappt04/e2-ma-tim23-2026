package uns.ac.rs.team23.slagalica.services

import android.util.Patterns
import uns.ac.rs.team23.slagalica.dtos.user.UserDTO

class AuthService {
    fun isUsernameUnique(username: String): Boolean = true

    fun getPasswordStrength(password: String): Int = 10

    // TODO: Implement real registration for user
    fun registerUser(user: UserDTO): Boolean = user.email.isValidEmail()

    // TODO: Implement real login for user
    fun loginUser(user: UserDTO): Boolean = true

    fun String.isValidEmail(): Boolean = Patterns.EMAIL_ADDRESS.matcher(this).matches()
}
