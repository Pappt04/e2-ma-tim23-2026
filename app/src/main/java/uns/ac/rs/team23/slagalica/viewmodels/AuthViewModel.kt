package uns.ac.rs.team23.slagalica.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uns.ac.rs.team23.slagalica.data.SessionStore

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class UserSession {
    data object NotLoggedIn : UserSession()
    data object Guest : UserSession()
    data class LoggedIn(val username: String, val email: String) : UserSession()
}

class AuthViewModel(private val sessionStore: SessionStore) : ViewModel() {

    private val _userSession = MutableStateFlow<UserSession>(UserSession.NotLoggedIn)
    val userSession: StateFlow<UserSession> = _userSession.asStateFlow()

    init {
        _userSession.value = sessionStore.restore()
    }

    var loginEmailOrUsername by mutableStateOf("")
        private set
    var loginPassword by mutableStateOf("")
        private set
    var registerEmail by mutableStateOf("")
        private set
    var registerUsername by mutableStateOf("")
        private set
    var registerRegion by mutableStateOf("")
        private set
    var registerPassword by mutableStateOf("")
        private set
    var registerConfirmPassword by mutableStateOf("")
        private set

    private val _loginState = MutableStateFlow<AuthState>(AuthState.Idle)
    val loginState: StateFlow<AuthState> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow<AuthState>(AuthState.Idle)
    val registerState: StateFlow<AuthState> = _registerState.asStateFlow()

    fun onLoginEmailOrUsernameChange(v: String) { loginEmailOrUsername = v }
    fun onLoginPasswordChange(v: String) { loginPassword = v }
    fun onRegisterEmailChange(v: String) { registerEmail = v }
    fun onRegisterUsernameChange(v: String) { registerUsername = v }
    fun onRegisterRegionChange(v: String) { registerRegion = v }
    fun onRegisterPasswordChange(v: String) { registerPassword = v }
    fun onRegisterConfirmPasswordChange(v: String) { registerConfirmPassword = v }

    fun loginAsGuest() {
        val session = UserSession.Guest
        sessionStore.save(session)
        _userSession.value = session
    }

    fun logout() {
        sessionStore.save(UserSession.NotLoggedIn)
        _userSession.value = UserSession.NotLoggedIn
    }

    /** Dev-only: bypass auth for local testing. */
    fun devLogin() {
        val session = UserSession.LoggedIn(username = "DevUser", email = "dev@test.com")
        sessionStore.save(session)
        _userSession.value = session
    }

    fun login() {
        if (loginEmailOrUsername.isBlank() || loginPassword.isBlank()) {
            _loginState.value = AuthState.Error("Please fill all fields")
            return
        }
        // TODO: connect to AuthService / Firebase
        val session = UserSession.LoggedIn(
            username = loginEmailOrUsername,
            email = if (loginEmailOrUsername.contains("@")) loginEmailOrUsername else "",
        )
        sessionStore.save(session)
        _userSession.value = session
        _loginState.value = AuthState.Success
    }

    fun register() {
        if (registerEmail.isBlank() || registerUsername.isBlank() ||
            registerRegion.isBlank() || registerPassword.isBlank()
        ) {
            _registerState.value = AuthState.Error("Please fill all fields")
            return
        }
        if (registerPassword != registerConfirmPassword) {
            _registerState.value = AuthState.Error("Passwords do not match")
            return
        }
        // TODO: connect to AuthService / Firebase (send email verification)
        _registerState.value = AuthState.Success
    }

    fun clearLoginState() { _loginState.value = AuthState.Idle }
    fun clearRegisterState() { _registerState.value = AuthState.Idle }
}
