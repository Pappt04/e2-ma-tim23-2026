package uns.ac.rs.team23.slagalica.views

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.ui.theme.SlagalicaTheme
import uns.ac.rs.team23.slagalica.viewmodels.AuthViewModel
import uns.ac.rs.team23.slagalica.viewmodels.UserSession
import uns.ac.rs.team23.slagalica.views.welcome.WelcomePage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SlagalicaTheme {
                val authViewModel: AuthViewModel = koinViewModel()
                val userSession by authViewModel.userSession.collectAsState()

                when (userSession) {
                    is UserSession.NotLoggedIn -> WelcomePage(viewModel = authViewModel)
                    is UserSession.Guest -> HomeScreen(
                        username = "Guest",
                        onLogout = authViewModel::logout,
                    )
                    is UserSession.LoggedIn -> HomeScreen(
                        username = (userSession as UserSession.LoggedIn).username,
                        onLogout = authViewModel::logout,
                    )
                }
            }
        }
    }
}
