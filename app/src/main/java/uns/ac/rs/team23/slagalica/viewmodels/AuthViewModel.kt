package uns.ac.rs.team23.slagalica.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AuthState {
    data object Idle : AuthState()

    data object Loading : AuthState()

    data object Success : AuthState()

    data class Error(
        val message: String,
    ) : AuthState()
}

class AuthViewModel : ViewModel() {
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

    fun onLoginEmailOrUsernameChange(v: String) {
        loginEmailOrUsername = v
    }

    fun onLoginPasswordChange(v: String) {
        loginPassword = v
    }

    fun onRegisterEmailChange(v: String) {
        registerEmail = v
    }

    fun onRegisterUsernameChange(v: String) {
        registerUsername = v
    }

    fun onRegisterRegionChange(v: String) {
        registerRegion = v
    }

    fun onRegisterPasswordChange(v: String) {
        registerPassword = v
    }

    fun onRegisterConfirmPasswordChange(v: String) {
        registerConfirmPassword = v
    }

    fun login() {
        if (loginEmailOrUsername.isBlank() || loginPassword.isBlank()) {
            _loginState.value = AuthState.Error("Please fill all fields")
            return
        }
        // TODO: connect to AuthService / Firebase
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
        // TODO: connect to AuthService / Firebase
        _registerState.value = AuthState.Success
    }

    fun clearLoginState() {
        _loginState.value = AuthState.Idle
    }

    fun clearRegisterState() {
        _registerState.value = AuthState.Idle
    }
}
