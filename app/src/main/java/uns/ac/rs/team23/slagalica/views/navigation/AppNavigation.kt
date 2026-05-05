package uns.ac.rs.team23.slagalica.views.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.viewmodels.AuthViewModel
import uns.ac.rs.team23.slagalica.viewmodels.UserSession
import uns.ac.rs.team23.slagalica.views.HomeScreen
import uns.ac.rs.team23.slagalica.views.game.GameScreen
import uns.ac.rs.team23.slagalica.views.game.korakpokorak.KorakPoKorakScreen
import uns.ac.rs.team23.slagalica.views.game.mojbroj.MojBrojScreen
import uns.ac.rs.team23.slagalica.views.lobby.LobbyScreen
import uns.ac.rs.team23.slagalica.views.welcome.RegisterPage
import uns.ac.rs.team23.slagalica.views.welcome.WelcomePage
import uns.ac.rs.team23.slagalica.views.NotificationsScreen

sealed class Screen(
    val route: String,
) {
    data object Login : Screen("login")

    data object Register : Screen("register")

    data object Home : Screen("home")
    data object Notifications : Screen("notifications")

    data object Lobby : Screen("lobby")

    data object Game : Screen("game")

    data object KorakPoKorak : Screen("korak_po_korak")

    data object MojBroj : Screen("moj_broj")
}

private val AUTH_ROUTES = setOf(Screen.Login.route, Screen.Register.route)

@Composable
fun AppNavHost(authViewModel: AuthViewModel = koinViewModel()) {
    val navController = rememberNavController()
    val userSession by authViewModel.userSession.collectAsState()

    // Navigate between auth and app only when crossing the auth boundary.
    LaunchedEffect(userSession) {
        val current = navController.currentDestination?.route
        when (userSession) {
            is UserSession.LoggedIn, UserSession.Guest -> {
                if (current in AUTH_ROUTES) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            UserSession.NotLoggedIn -> {
                if (current !in AUTH_ROUTES) {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) },
    ) {
        composable(Screen.Login.route) {
            WelcomePage(
                viewModel = authViewModel,
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
            )
        }
        composable(Screen.Register.route) {
            RegisterPage(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Home.route) {
            val session = userSession
            HomeScreen(
                username = if (session is UserSession.LoggedIn) session.username else "Guest",
                onNavigateToPlay = { navController.navigate(Screen.Lobby.route) },
                onLogout = authViewModel::logout,
                onNavigateToNotifications = {
                    navController.navigate(Screen.Notifications.route)
                },
            )
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Lobby.route) {
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Guest"
            LobbyScreen(
                currentUsername = username,
                onNavigateBack = { navController.popBackStack() },
                onGameStart = {
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.Lobby.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Game.route) {
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Guest"
            GameScreen(
                playerName = username,
                opponentName = "Opponent",
                onForfeit = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onNavigateToKorakPoKorak = {
                    navController.navigate(Screen.KorakPoKorak.route)
                },
                onNavigateToMojBroj = {
                    navController.navigate(Screen.MojBroj.route)
                },
            )
        }
        composable(Screen.KorakPoKorak.route) {
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Guest"
            KorakPoKorakScreen(
                player1Name = username,
                player2Name = "Opponent",
                onFinish = { navController.popBackStack() },
            )
        }
        composable(Screen.MojBroj.route) {
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Guest"
            MojBrojScreen(
                player1Name = username,
                player2Name = "Opponent",
                onFinish = { navController.popBackStack() },
            )
        }
    }
}
