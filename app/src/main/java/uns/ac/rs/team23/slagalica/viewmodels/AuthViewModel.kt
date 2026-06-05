package uns.ac.rs.team23.slagalica.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uns.ac.rs.team23.slagalica.data.SessionStore
import uns.ac.rs.team23.slagalica.repository.AuthRepository

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

class AuthViewModel(
    private val sessionStore: SessionStore,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _userSession = MutableStateFlow<UserSession>(UserSession.NotLoggedIn)
    val userSession: StateFlow<UserSession> = _userSession.asStateFlow()

    init {
        _userSession.value = sessionStore.restore()
    }

    // ── Login fields ──────────────────────────────────────────────────────────

    var loginEmailOrUsername by mutableStateOf("")
        private set
    var loginPassword by mutableStateOf("")
        private set

    private val _loginState = MutableStateFlow<AuthState>(AuthState.Idle)
    val loginState: StateFlow<AuthState> = _loginState.asStateFlow()

    fun onLoginEmailOrUsernameChange(v: String) { loginEmailOrUsername = v }
    fun onLoginPasswordChange(v: String) { loginPassword = v }

    fun login() {
        if (loginEmailOrUsername.isBlank() || loginPassword.isBlank()) {
            _loginState.value = AuthState.Error("Popunite sva polja")
            return
        }
        _loginState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.login(loginEmailOrUsername, loginPassword)
                .onSuccess { profile ->
                    val session = UserSession.LoggedIn(profile.username, profile.email)
                    sessionStore.save(session)
                    _userSession.value = session
                    _loginState.value = AuthState.Success
                }
                .onFailure { _loginState.value = AuthState.Error(it.message ?: "Greška pri logovanju") }
        }
    }

    fun clearLoginState() { _loginState.value = AuthState.Idle }

    // ── Register fields ───────────────────────────────────────────────────────

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

    private val _registerState = MutableStateFlow<AuthState>(AuthState.Idle)
    val registerState: StateFlow<AuthState> = _registerState.asStateFlow()

    fun onRegisterEmailChange(v: String) { registerEmail = v }
    fun onRegisterUsernameChange(v: String) { registerUsername = v }
    fun onRegisterRegionChange(v: String) { registerRegion = v }
    fun onRegisterPasswordChange(v: String) { registerPassword = v }
    fun onRegisterConfirmPasswordChange(v: String) { registerConfirmPassword = v }

    fun register() {
        if (registerEmail.isBlank() || registerUsername.isBlank() ||
            registerRegion.isBlank() || registerPassword.isBlank()
        ) {
            _registerState.value = AuthState.Error("Popunite sva polja")
            return
        }
        if (registerPassword != registerConfirmPassword) {
            _registerState.value = AuthState.Error("Lozinke se ne podudaraju")
            return
        }
        if (registerPassword.length < 6) {
            _registerState.value = AuthState.Error("Lozinka mora imati najmanje 6 znakova")
            return
        }
        _registerState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.register(registerEmail.trim(), registerUsername.trim(), registerRegion, registerPassword)
                .onSuccess { _registerState.value = AuthState.Success }
                .onFailure { _registerState.value = AuthState.Error(it.message ?: "Greška pri registraciji") }
        }
    }

    fun clearRegisterState() { _registerState.value = AuthState.Idle }

    // ── Forgot password ───────────────────────────────────────────────────────

    var forgotEmail by mutableStateOf("")
        private set

    private val _forgotState = MutableStateFlow<AuthState>(AuthState.Idle)
    val forgotState: StateFlow<AuthState> = _forgotState.asStateFlow()

    fun onForgotEmailChange(v: String) { forgotEmail = v }

    fun sendPasswordReset() {
        if (forgotEmail.isBlank()) {
            _forgotState.value = AuthState.Error("Unesite email adresu")
            return
        }
        _forgotState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.sendPasswordResetEmail(forgotEmail.trim())
                .onSuccess { _forgotState.value = AuthState.Success }
                .onFailure { _forgotState.value = AuthState.Error(it.message ?: "Greška") }
        }
    }

    fun clearForgotState() {
        _forgotState.value = AuthState.Idle
        forgotEmail = ""
    }

    // ── Change password ───────────────────────────────────────────────────────

    var changeOldPassword by mutableStateOf("")
        private set
    var changeNewPassword by mutableStateOf("")
        private set
    var changeConfirmNewPassword by mutableStateOf("")
        private set

    private val _changePasswordState = MutableStateFlow<AuthState>(AuthState.Idle)
    val changePasswordState: StateFlow<AuthState> = _changePasswordState.asStateFlow()

    fun onChangeOldPasswordChange(v: String) { changeOldPassword = v }
    fun onChangeNewPasswordChange(v: String) { changeNewPassword = v }
    fun onChangeConfirmNewPasswordChange(v: String) { changeConfirmNewPassword = v }

    fun changePassword() {
        val session = _userSession.value as? UserSession.LoggedIn ?: return
        if (changeOldPassword.isBlank() || changeNewPassword.isBlank()) {
            _changePasswordState.value = AuthState.Error("Popunite sva polja")
            return
        }
        if (changeNewPassword != changeConfirmNewPassword) {
            _changePasswordState.value = AuthState.Error("Nove lozinke se ne podudaraju")
            return
        }
        if (changeNewPassword.length < 6) {
            _changePasswordState.value = AuthState.Error("Nova lozinka mora imati najmanje 6 znakova")
            return
        }
        _changePasswordState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.changePassword(session.username, changeOldPassword, changeNewPassword)
                .onSuccess { _changePasswordState.value = AuthState.Success }
                .onFailure { _changePasswordState.value = AuthState.Error(it.message ?: "Greška") }
        }
    }

    fun clearChangePasswordState() {
        _changePasswordState.value = AuthState.Idle
        changeOldPassword = ""
        changeNewPassword = ""
        changeConfirmNewPassword = ""
    }

    // ── Dev / Guest helpers ───────────────────────────────────────────────────

    fun loginAsGuest() {
        sessionStore.save(UserSession.Guest)
        _userSession.value = UserSession.Guest
    }

    fun logout() {
        sessionStore.save(UserSession.NotLoggedIn)
        _userSession.value = UserSession.NotLoggedIn
    }

    /** Dev-only: simulate clicking the email verification link. */
    fun devVerifyEmail() {
        val username = registerUsername.ifBlank {
            (sessionStore.restore() as? UserSession.LoggedIn)?.username ?: return
        }
        viewModelScope.launch { authRepository.verifyEmailDev(username) }
    }
}
