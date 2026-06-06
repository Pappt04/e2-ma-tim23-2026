package uns.ac.rs.team23.slagalica.views.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import uns.ac.rs.team23.slagalica.data.MatchStore
import uns.ac.rs.team23.slagalica.repository.MatchRepository
import uns.ac.rs.team23.slagalica.services.NtfyNotificationService
import uns.ac.rs.team23.slagalica.viewmodels.AuthViewModel
import uns.ac.rs.team23.slagalica.viewmodels.UserSession
import uns.ac.rs.team23.slagalica.views.HomeScreen
import uns.ac.rs.team23.slagalica.views.game.GameScreen
import uns.ac.rs.team23.slagalica.views.game.asocijacije.AsocijacijeScreen
import uns.ac.rs.team23.slagalica.views.game.korakpokorak.KorakPoKorakScreen
import uns.ac.rs.team23.slagalica.views.game.koznazna.KoZnaZnaScreen
import uns.ac.rs.team23.slagalica.views.game.mojbroj.MojBrojScreen
import uns.ac.rs.team23.slagalica.views.game.skocko.SkockoScreen
import uns.ac.rs.team23.slagalica.views.game.spojnice.SpojniceScreen
import uns.ac.rs.team23.slagalica.views.chat.ChatScreen
import uns.ac.rs.team23.slagalica.views.challenge.ChallengeScreen
import uns.ac.rs.team23.slagalica.views.lobby.LobbyScreen
import uns.ac.rs.team23.slagalica.views.NotificationsScreen
import uns.ac.rs.team23.slagalica.views.profile.ChangePasswordScreen
import uns.ac.rs.team23.slagalica.views.profile.ProfileScreen
import uns.ac.rs.team23.slagalica.views.profile.ProfileStatisticsScreen
import uns.ac.rs.team23.slagalica.views.welcome.ForgotPasswordScreen
import uns.ac.rs.team23.slagalica.views.welcome.RegisterPage
import uns.ac.rs.team23.slagalica.views.welcome.WelcomePage

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object ForgotPassword : Screen("forgot_password")
    data object Home : Screen("home")
    data object Notifications : Screen("notifications")
    data object Profile : Screen("profile")
    data object ProfileStatistics : Screen("profile_statistics")
    data object ChangePassword : Screen("change_password")
    data object Lobby : Screen("lobby")
    data object Game : Screen("game")
    data object KoZnaZna : Screen("ko_zna_zna")
    data object Spojnice : Screen("spojnice")
    data object KorakPoKorak : Screen("korak_po_korak")
    data object MojBroj : Screen("moj_broj")
    data object Skocko : Screen("skocko")
    data object Asocijacije : Screen("asocijacije")
    data object Chat : Screen("chat/{region}") {
        fun route(region: String) = "chat/$region"
    }
    data object Challenge : Screen("challenge/{region}") {
        fun route(region: String) = "challenge/$region"
    }
}

private val AUTH_ROUTES = setOf(
    Screen.Login.route,
    Screen.Register.route,
    Screen.ForgotPassword.route,
)

@Composable
fun AppNavHost(authViewModel: AuthViewModel = koinViewModel()) {
    val navController = rememberNavController()
    val userSession by authViewModel.userSession.collectAsState()
    val userProfile by authViewModel.userProfile.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(userProfile?.id) {
        val id = userProfile?.id ?: 0L
        if (id > 0L) {
            NtfyNotificationService.start(context, id)
        } else {
            NtfyNotificationService.stop(context)
        }
    }

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
                onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) },
            )
        }
        composable(Screen.Register.route) {
            RegisterPage(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Home.route) {
            val session = userSession
            val region = userProfile?.region ?: ""
            HomeScreen(
                username = if (session is UserSession.LoggedIn) session.username else "Gost",
                isRegistered = session is UserSession.LoggedIn,
                onNavigateToPlay = { navController.navigate(Screen.Lobby.route) },
                onLogout = authViewModel::logout,
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToChat = {
                    if (region.isNotBlank()) navController.navigate(Screen.Chat.route(region))
                },
                onNavigateToChallenge = {
                    if (region.isNotBlank()) navController.navigate(Screen.Challenge.route(region))
                },
            )
        }
        composable(Screen.Profile.route) {
            val session = userSession
            LaunchedEffect(session) {
                if (session !is UserSession.LoggedIn) {
                    val popped = navController.popBackStack(Screen.Home.route, inclusive = false)
                    if (!popped) navController.navigate(Screen.Home.route) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
                }
            }
            if (session is UserSession.LoggedIn) {
                ProfileScreen(
                    username = session.username,
                    email = session.email,
                    tokens = userProfile?.tokens ?: 0,
                    stars = userProfile?.stars ?: 0,
                    leagueLevel = userProfile?.leagueLevel ?: 0,
                    region = userProfile?.region ?: "",
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToStatistics = { navController.navigate(Screen.ProfileStatistics.route) },
                    onNavigateToChangePassword = { navController.navigate(Screen.ChangePassword.route) },
                    onLogout = authViewModel::logout,
                )
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
        composable(Screen.ChangePassword.route) {
            val session = userSession
            LaunchedEffect(session) {
                if (session !is UserSession.LoggedIn) navController.popBackStack()
            }
            ChangePasswordScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Screen.ProfileStatistics.route) {
            val session = userSession
            LaunchedEffect(session) {
                if (session !is UserSession.LoggedIn) {
                    val popped = navController.popBackStack(Screen.Home.route, inclusive = false)
                    if (!popped) navController.navigate(Screen.Home.route) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
                }
            }
            if (session is UserSession.LoggedIn) {
                ProfileStatisticsScreen(onNavigateBack = { navController.popBackStack() })
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
        composable(Screen.Notifications.route) {
            val session = userSession
            LaunchedEffect(session) {
                if (session !is UserSession.LoggedIn) {
                    val popped = navController.popBackStack(Screen.Home.route, inclusive = false)
                    if (!popped) navController.navigate(Screen.Home.route) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
                }
            }
            if (session is UserSession.LoggedIn) {
                NotificationsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onMatchStarted = {
                        navController.navigate(Screen.Game.route) {
                            popUpTo(Screen.Home.route)
                        }
                    },
                )
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
        composable(Screen.Lobby.route) {
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Gost"
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
            val username = if (session is UserSession.LoggedIn) session.username else "Gost"
            val matchRepo: MatchRepository = koinInject()
            GameScreen(
                playerName = username,
                opponentName = MatchStore.opponentUsername.ifBlank { "Protivnik" },
                matchId = MatchStore.matchId,
                matchRepository = matchRepo,
                onForfeit = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onNavigateToKorakPoKorak = { navController.navigate(Screen.KorakPoKorak.route) },
                onNavigateToKoZnaZna = { navController.navigate(Screen.KoZnaZna.route) },
                onNavigateToSpojnice = { navController.navigate(Screen.Spojnice.route) },
                onNavigateToMojBroj = { navController.navigate(Screen.MojBroj.route) },
                onNavigateToSkocko = { navController.navigate(Screen.Skocko.route) },
                onNavigateToAsocijacije = { navController.navigate(Screen.Asocijacije.route) },
            )
        }
        composable(Screen.KorakPoKorak.route) {
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Gost"
            KorakPoKorakScreen(
                player1Name = username,
                player2Name = "Protivnik",
                onFinish = { navController.popBackStack() },
            )
        }
        composable(Screen.KoZnaZna.route) {
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Gost"
            KoZnaZnaScreen(
                player1Name = username,
                player2Name = "Protivnik",
                onFinish = { navController.popBackStack() },
            )
        }
        composable(Screen.Spojnice.route) {
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Gost"
            SpojniceScreen(
                player1Name = username,
                player2Name = "Protivnik",
                onFinish = { navController.popBackStack() },
            )
        }
        composable(Screen.MojBroj.route) {
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Gost"
            MojBrojScreen(
                player1Name = username,
                player2Name = "Protivnik",
                onFinish = { navController.popBackStack() },
            )
        }
        composable(Screen.Skocko.route) {
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Gost"
            SkockoScreen(
                player1Name = username,
                player2Name = "Protivnik",
                onFinish = { navController.popBackStack() },
            )
        }
        composable(Screen.Asocijacije.route) {
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Gost"
            AsocijacijeScreen(
                player1Name = username,
                player2Name = "Protivnik",
                onFinish = { navController.popBackStack() },
            )
        }
        composable(Screen.Chat.route) { backStack ->
            val region = backStack.arguments?.getString("region") ?: ""
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Gost"
            ChatScreen(
                region = region,
                username = username,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Challenge.route) { backStack ->
            val region = backStack.arguments?.getString("region") ?: ""
            ChallengeScreen(
                region = region,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
