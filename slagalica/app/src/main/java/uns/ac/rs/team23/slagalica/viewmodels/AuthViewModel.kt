package uns.ac.rs.team23.slagalica.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uns.ac.rs.team23.slagalica.data.CycleManager
import uns.ac.rs.team23.slagalica.models.LEAGUE_NAMES
import uns.ac.rs.team23.slagalica.models.Notification
import uns.ac.rs.team23.slagalica.models.NotificationType
import uns.ac.rs.team23.slagalica.models.UserProfile
import uns.ac.rs.team23.slagalica.repository.AuthRepository
import uns.ac.rs.team23.slagalica.repository.ProfileRepository
import uns.ac.rs.team23.slagalica.repository.NotificationRepository
import uns.ac.rs.team23.slagalica.services.ClientDbListeners
import uns.ac.rs.team23.slagalica.services.PendingRewardEvent

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

/** Emitted when the user's league level changes (promotion or demotion). */
data class LeagueChangeEvent(val oldLevel: Int, val newLevel: Int) {
    val promoted: Boolean get() = newLevel > oldLevel
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val notificationRepository: NotificationRepository,
    private val cycleManager: CycleManager,
    private val clientDbListeners: ClientDbListeners,
) : ViewModel() {

    private val _userSession = MutableStateFlow<UserSession>(UserSession.NotLoggedIn)
    val userSession: StateFlow<UserSession> = _userSession.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _leagueChange = MutableSharedFlow<LeagueChangeEvent>(extraBufferCapacity = 4)
    val leagueChange: SharedFlow<LeagueChangeEvent> = _leagueChange.asSharedFlow()

    private val _profilePictureMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val profilePictureMessage: SharedFlow<String> = _profilePictureMessage.asSharedFlow()

    /** Cycle-end token rewards (client-side, replaces Cloud Function). */
    val pendingReward: SharedFlow<PendingRewardEvent> = clientDbListeners.pendingReward

    init {
        viewModelScope.launch {
            clientDbListeners.liveProfile.collect { profile ->
                if (profile == null) return@collect
                if (_userSession.value !is UserSession.LoggedIn) return@collect
                applyProfileUpdate(profile)
            }
        }
        // Firebase Auth persists state across restarts — restore from current user
        val current = FirebaseAuth.getInstance().currentUser
        if (current != null) {
            if (current.isAnonymous) {
                _userSession.value = UserSession.Guest
            } else {
                val username = current.displayName ?: ""
                val email = current.email ?: ""
                _userSession.value = UserSession.LoggedIn(username, email)
                clientDbListeners.start(current.uid)
                refreshProfile()
            }
        }
        startPresenceHeartbeat()
        runMonthlyRollover()
    }

    /** Automatic monthly-cycle rollover (no Cloud Functions / billing required). */
    private fun runMonthlyRollover() {
        viewModelScope.launch {
            cycleManager.maybeRollover()
                .onSuccess { rolled -> if (rolled) refreshProfile() }
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            authRepository.getProfile().onSuccess { applyProfileUpdate(it) }
        }
    }

    private fun applyProfileUpdate(newProfile: UserProfile) {
        val old = _userProfile.value
        _userProfile.value = newProfile
        detectLeagueChange(old, newProfile)
    }

    /** Persist the user's chosen avatar variant. */
    fun updateAvatar(index: Int) {
        viewModelScope.launch {
            authRepository.updateAvatar(index).onSuccess { refreshProfile() }
        }
    }

    fun uploadProfilePicture(uri: Uri) {
        viewModelScope.launch {
            profileRepository.uploadProfilePicture(uri)
                .onSuccess { url ->
                    _userProfile.value = _userProfile.value?.copy(profilePictureUrl = url)
                    refreshProfile()
                    _profilePictureMessage.emit("Profile photo updated")
                }
                .onFailure {
                    _profilePictureMessage.emit(it.message ?: "Failed to upload photo")
                }
        }
    }

    fun clearProfilePicture() {
        viewModelScope.launch {
            profileRepository.clearProfilePicture()
                .onSuccess {
                    _userProfile.value = _userProfile.value?.copy(profilePictureUrl = "")
                    refreshProfile()
                    _profilePictureMessage.emit("Profile photo removed")
                }
                .onFailure {
                    _profilePictureMessage.emit(it.message ?: "Failed to remove photo")
                }
        }
    }

    private fun startPresenceHeartbeat() {
        viewModelScope.launch {
            while (true) {
                authRepository.updatePresence()
                delay(30_000)
            }
        }
    }

    private fun detectLeagueChange(old: UserProfile?, updated: UserProfile) {
        val oldLevel = old?.leagueLevel ?: return
        if (oldLevel == updated.leagueLevel) return
        val event = LeagueChangeEvent(oldLevel, updated.leagueLevel)
        val leagueName = LEAGUE_NAMES.getOrElse(updated.leagueLevel) { "League ${updated.leagueLevel}" }
        viewModelScope.launch {
            notificationRepository.saveNotification(
                Notification(
                    id = "league_${updated.leagueLevel}_${System.currentTimeMillis()}",
                    title = if (event.promoted) "You were promoted!" else "You were demoted",
                    message = "You are now in: $leagueName",
                    type = NotificationType.RANKING,
                )
            )
            _leagueChange.emit(event)
        }
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
            _loginState.value = AuthState.Error("Fill in all fields")
            return
        }
        _loginState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.login(loginEmailOrUsername, loginPassword)
                .onSuccess { profile ->
                    val session = UserSession.LoggedIn(profile.username, profile.email)
                    _userSession.value = session
                    applyProfileUpdate(profile)
                    FirebaseAuth.getInstance().currentUser?.uid?.let { clientDbListeners.start(it) }
                    _loginState.value = AuthState.Success
                }
                .onFailure { _loginState.value = AuthState.Error(it.message ?: "Login error") }
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
            _registerState.value = AuthState.Error("Fill in all fields")
            return
        }
        if (registerPassword != registerConfirmPassword) {
            _registerState.value = AuthState.Error("Passwords do not match")
            return
        }
        if (registerPassword.length < 6) {
            _registerState.value = AuthState.Error("Password must be at least 6 characters")
            return
        }
        _registerState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.register(registerEmail.trim(), registerUsername.trim(), registerRegion, registerPassword)
                .onSuccess { _registerState.value = AuthState.Success }
                .onFailure { _registerState.value = AuthState.Error(it.message ?: "Registration error") }
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
            _forgotState.value = AuthState.Error("Enter your email address")
            return
        }
        _forgotState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.sendPasswordResetEmail(forgotEmail.trim())
                .onSuccess { _forgotState.value = AuthState.Success }
                .onFailure { _forgotState.value = AuthState.Error(it.message ?: "Error") }
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
            _changePasswordState.value = AuthState.Error("Fill in all fields")
            return
        }
        if (changeNewPassword != changeConfirmNewPassword) {
            _changePasswordState.value = AuthState.Error("New passwords do not match")
            return
        }
        if (changeNewPassword.length < 6) {
            _changePasswordState.value = AuthState.Error("New password must be at least 6 characters")
            return
        }
        _changePasswordState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.changePassword(session.username, changeOldPassword, changeNewPassword)
                .onSuccess { _changePasswordState.value = AuthState.Success }
                .onFailure { _changePasswordState.value = AuthState.Error(it.message ?: "Error") }
        }
    }

    fun clearChangePasswordState() {
        _changePasswordState.value = AuthState.Idle
        changeOldPassword = ""
        changeNewPassword = ""
        changeConfirmNewPassword = ""
    }

    // ── Guest ─────────────────────────────────────────────────────────────────

    fun loginAsGuest() {
        viewModelScope.launch {
            authRepository.loginAsGuest()
                .onSuccess { profile ->
                    _userSession.value = UserSession.Guest
                    _userProfile.value = profile
                }
                .onFailure {
                    _userSession.value = UserSession.Guest
                }
        }
    }

    fun logout() {
        clientDbListeners.stop()
        viewModelScope.launch {
            authRepository.logout()
        }
        _userSession.value = UserSession.NotLoggedIn
        _userProfile.value = null
    }

    override fun onCleared() {
        clientDbListeners.stop()
        super.onCleared()
    }
}
