package uns.ac.rs.team23.slagalica.services

class AuthService {
    fun isUsernameUnique(username: String): Boolean = true

    fun getPasswordStrength(passwrod: String): Int = 10
}

