package uns.ac.rs.team23.slagalica.viewmodels

class AuthViewModel {
    var usernameInput: String = ""
    var emailInput: String = ""
    var regionInput: String = ""
    var passwordInput: String = ""
    var secondPasswordInput: String = ""

    fun arePasswordsEqual() = passwordInput == secondPasswordInput
}
