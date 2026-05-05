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
import uns.ac.rs.team23.slagalica.views.welcome.RegisterPage
import uns.ac.rs.team23.slagalica.views.welcome.WelcomePage
import uns.ac.rs.team23.slagalica.views.NotificationsScreen

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object Home : Screen("home")
    data object Notifications : Screen("notifications")
}

@Composable
fun AppNavHost(authViewModel: AuthViewModel = koinViewModel()) {
    val navController = rememberNavController()
    val userSession by authViewModel.userSession.collectAsState()

    LaunchedEffect(userSession) {
        val current = navController.currentDestination?.route
        when (userSession) {
            is UserSession.LoggedIn, UserSession.Guest -> {
                if (current != Screen.Home.route) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            UserSession.NotLoggedIn -> {
                if (current != Screen.Login.route && current != Screen.Register.route) {
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
        enterTransition = { fadeIn(animationSpec = tween(500)) },
        exitTransition = { fadeOut(animationSpec = tween(500)) }
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
    }
}
