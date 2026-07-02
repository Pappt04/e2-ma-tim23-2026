package uns.ac.rs.team23.slagalica.views.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import uns.ac.rs.team23.slagalica.data.ChallengeStore
import uns.ac.rs.team23.slagalica.data.MatchStore
import uns.ac.rs.team23.slagalica.data.TournamentStore
import uns.ac.rs.team23.slagalica.models.LEAGUE_NAMES
import uns.ac.rs.team23.slagalica.repository.MatchRepository
import uns.ac.rs.team23.slagalica.repository.RegionRepository
import uns.ac.rs.team23.slagalica.services.PendingRewardEvent
import uns.ac.rs.team23.slagalica.viewmodels.AuthViewModel
import uns.ac.rs.team23.slagalica.viewmodels.LeagueChangeEvent
import uns.ac.rs.team23.slagalica.viewmodels.ChallengeViewModel
import uns.ac.rs.team23.slagalica.viewmodels.LobbyViewModel
import uns.ac.rs.team23.slagalica.viewmodels.UserSession
import uns.ac.rs.team23.slagalica.views.HomeScreen
import uns.ac.rs.team23.slagalica.views.friends.FriendsScreen
import uns.ac.rs.team23.slagalica.views.league.LeagueScreen
import uns.ac.rs.team23.slagalica.views.region.RegionMapScreen
import uns.ac.rs.team23.slagalica.views.game.GameScreen
import uns.ac.rs.team23.slagalica.views.game.MatchResultsScreen
import uns.ac.rs.team23.slagalica.views.game.asocijacije.AsocijacijeScreen
import uns.ac.rs.team23.slagalica.views.game.korakpokorak.KorakPoKorakScreen
import uns.ac.rs.team23.slagalica.views.game.koznazna.KoZnaZnaScreen
import uns.ac.rs.team23.slagalica.views.game.mojbroj.MojBrojScreen
import uns.ac.rs.team23.slagalica.views.game.skocko.SkockoScreen
import uns.ac.rs.team23.slagalica.views.game.spojnice.SpojniceScreen
import uns.ac.rs.team23.slagalica.views.chat.ChatScreen
import uns.ac.rs.team23.slagalica.views.challenge.ChallengeScreen
import uns.ac.rs.team23.slagalica.views.leaderboard.LeaderboardScreen
import uns.ac.rs.team23.slagalica.views.lobby.LobbyScreen
import uns.ac.rs.team23.slagalica.views.tournament.TournamentScreen
import uns.ac.rs.team23.slagalica.views.missions.DailyTasksScreen
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
    data object Tournament : Screen("tournament")
    data object Game : Screen("game")
    data object KoZnaZna : Screen("ko_zna_zna")
    data object Spojnice : Screen("spojnice")
    data object KorakPoKorak : Screen("korak_po_korak")
    data object MojBroj : Screen("moj_broj")
    data object Skocko : Screen("skocko")
    data object Asocijacije : Screen("asocijacije")
    data object MatchResults : Screen("match_results")
    data object Chat : Screen("chat/{region}") {
        fun route(region: String) = "chat/$region"
    }
    data object Challenge : Screen("challenge/{region}") {
        fun route(region: String) = "challenge/$region"
    }
    data object Friends : Screen("friends")
    data object Region : Screen("region")
    data object League : Screen("league")
    data object Leaderboard : Screen("leaderboard")
    data object DailyTasks : Screen("daily_tasks")
    data object LobbyFriend : Screen("lobby_friend/{friendId}") {
        fun route(friendId: String) = "lobby_friend/$friendId"
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
            RefreshProfileOnEnter(authViewModel)
            val session = userSession
            val region = userProfile?.region ?: ""
            HomeScreen(
                username = if (session is UserSession.LoggedIn) session.username else "Guest",
                isRegistered = session is UserSession.LoggedIn,
                onNavigateToPlay = { navController.navigate(Screen.Lobby.route) },
                onNavigateToTournament = { navController.navigate(Screen.Tournament.route) },
                onNavigateToDailyTasks = { navController.navigate(Screen.DailyTasks.route) },
                onLogout = authViewModel::logout,
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToChat = {
                    if (region.isNotBlank()) navController.navigate(Screen.Chat.route(region))
                },
                onNavigateToChallenge = {
                    if (region.isNotBlank()) navController.navigate(Screen.Challenge.route(region))
                },
                onNavigateToFriends = { navController.navigate(Screen.Friends.route) },
                onNavigateToRegion = { navController.navigate(Screen.Region.route) },
                onNavigateToLeague = { navController.navigate(Screen.League.route) },
                onNavigateToLeaderboard = { navController.navigate(Screen.Leaderboard.route) },
            )
        }
        composable(Screen.Profile.route) {
            RefreshProfileOnEnter(authViewModel)
            val session = userSession
            LaunchedEffect(session) {
                if (session !is UserSession.LoggedIn) {
                    val popped = navController.popBackStack(Screen.Home.route, inclusive = false)
                    if (!popped) navController.navigate(Screen.Home.route) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
                }
            }
            if (session is UserSession.LoggedIn) {
                val profile = userProfile
                if (profile == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                val regionRepo: RegionRepository = koinInject()
                val myRegion = profile.region
                val frameRank by produceState(0, myRegion) {
                    val tops = regionRepo.loadPreviousTopRegions().getOrDefault(emptyList())
                    val idx = tops.indexOf(myRegion)
                    value = if (idx in 0..2) idx + 1 else 0
                }
                ProfileScreen(
                    username = session.username,
                    email = session.email,
                    tokens = profile.tokens,
                    stars = profile.stars,
                    leagueLevel = profile.leagueLevel,
                    region = profile.region,
                    avatarIndex = profile.avatarIndex,
                    profilePictureUrl = profile.profilePictureUrl,
                    frameRank = frameRank,
                    onAvatarChange = authViewModel::updateAvatar,
                    onProfilePicturePicked = authViewModel::uploadProfilePicture,
                    onClearProfilePicture = authViewModel::clearProfilePicture,
                    profilePictureMessages = authViewModel.profilePictureMessage,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToStatistics = { navController.navigate(Screen.ProfileStatistics.route) },
                    onNavigateToChangePassword = { navController.navigate(Screen.ChangePassword.route) },
                    onNavigateToLeague = { navController.navigate(Screen.League.route) },
                    onLogout = authViewModel::logout,
                )
                }
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
            RefreshProfileOnEnter(authViewModel)
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
            RefreshProfileOnEnter(authViewModel)
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
        composable(Screen.Tournament.route) {
            RefreshProfileOnEnter(authViewModel)
            val session = userSession
            LaunchedEffect(session) {
                if (session !is UserSession.LoggedIn) {
                    val popped = navController.popBackStack(Screen.Home.route, inclusive = false)
                    if (!popped) navController.navigate(Screen.Home.route) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
                }
            }
            if (session is UserSession.LoggedIn) {
                TournamentScreen(
                    onEnterMatch = { navController.navigate(Screen.Game.route) },
                    onExitToHome = {
                        authViewModel.refreshProfile()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                )
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
        composable(Screen.Game.route) {
            RefreshProfileOnEnter(authViewModel)
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Guest"
            val matchRepo: MatchRepository = koinInject()
            val challengeViewModel: ChallengeViewModel = koinViewModel()
            val scope = rememberCoroutineScope()
            GameScreen(
                playerName = username,
                opponentName = MatchStore.opponentUsername.ifBlank { "Opponent" },
                matchId = MatchStore.matchId,
                matchRepository = matchRepo,
                onForfeit = {
                    MatchStore.clear()
                    ChallengeStore.clear()
                    TournamentStore.clear()
                    authViewModel.refreshProfile()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onAllGamesFinished = {
                    MatchStore.clear()
                    ChallengeStore.clear()
                    TournamentStore.clear()
                    authViewModel.refreshProfile()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onMatchCompleted = { iWon ->
                    if (TournamentStore.isActive) {
                        val isFinal = TournamentStore.isFinalRound
                        if (!iWon && !isFinal) {
                            // Lost (or forfeited) a semifinal — the tournament is over for this player.
                            // Return to the main menu; they're no longer in a match or the tournament.
                            // NOTE: do NOT clear MatchStore here — blanking matchId makes the Game route
                            // recompose (via refreshProfile) into a blank-match GameScreen that bails to
                            // Home. The tournament VM's leave()/cancel() clears MatchStore.
                            TournamentStore.clear()
                            authViewModel.refreshProfile()
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                            }
                        } else {
                            // Winner advances (or it's the final — both see the result on the bracket
                            // screen). Re-affirm which match just finished so the bracket screen can
                            // record the winner reliably and never re-enter the completed game.
                            TournamentStore.setCurrentMatch(
                                MatchStore.matchId,
                                if (isFinal) TournamentStore.ROUND_FINAL else TournamentStore.ROUND_SEMIFINAL,
                            )
                            authViewModel.refreshProfile()
                            navController.navigate(Screen.Tournament.route) {
                                popUpTo(Screen.Tournament.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    } else if (ChallengeStore.isActive) {
                        val challengeId = ChallengeStore.activeChallengeId
                        val matchId = MatchStore.matchId
                        val region = ChallengeStore.region
                        scope.launch {
                            challengeViewModel.submitAttempt(challengeId, matchId) {
                                ChallengeStore.clear()
                                MatchStore.clear()
                                authViewModel.refreshProfile()
                                navController.navigate(Screen.Challenge.route(region)) {
                                    popUpTo(Screen.Home.route) { inclusive = false }
                                }
                            }
                        }
                    } else {
                        authViewModel.refreshProfile()
                        navController.navigate(Screen.MatchResults.route) {
                            popUpTo(Screen.Game.route)
                            launchSingleTop = true
                        }
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
            val username = if (session is UserSession.LoggedIn) session.username else "Guest"
            KorakPoKorakScreen(
                player1Name = MatchStore.player1Username.ifBlank { username },
                player2Name = MatchStore.player2Username.ifBlank { MatchStore.opponentUsername.ifBlank { "Opponent" } },
                onFinish = { navController.popBackStack() },
                onForfeit = {
                    MatchStore.clear()
                    TournamentStore.clear()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.KoZnaZna.route) {
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Guest"
            KoZnaZnaScreen(
                player1Name = MatchStore.player1Username.ifBlank { username },
                player2Name = MatchStore.player2Username.ifBlank { MatchStore.opponentUsername.ifBlank { "Opponent" } },
                onFinish = { navController.popBackStack() },
                onForfeit = {
                    MatchStore.clear()
                    TournamentStore.clear()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Spojnice.route) {
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Guest"
            SpojniceScreen(
                player1Name = MatchStore.player1Username.ifBlank { username },
                player2Name = MatchStore.player2Username.ifBlank { MatchStore.opponentUsername.ifBlank { "Opponent" } },
                onFinish = { navController.popBackStack() },
                onForfeit = {
                    MatchStore.clear()
                    TournamentStore.clear()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.MojBroj.route) {
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Guest"
            MojBrojScreen(
                player1Name = MatchStore.player1Username.ifBlank { username },
                player2Name = MatchStore.player2Username.ifBlank { MatchStore.opponentUsername.ifBlank { "Opponent" } },
                onFinish = { navController.popBackStack() },
                onForfeit = {
                    MatchStore.clear()
                    TournamentStore.clear()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Skocko.route) {
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Guest"
            SkockoScreen(
                player1Name = MatchStore.player1Username.ifBlank { username },
                player2Name = MatchStore.player2Username.ifBlank { MatchStore.opponentUsername.ifBlank { "Opponent" } },
                onFinish = { navController.popBackStack() },
                onForfeit = {
                    MatchStore.clear()
                    TournamentStore.clear()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Asocijacije.route) {
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Guest"
            AsocijacijeScreen(
                player1Name = MatchStore.player1Username.ifBlank { username },
                player2Name = MatchStore.player2Username.ifBlank { MatchStore.opponentUsername.ifBlank { "Opponent" } },
                onFinish = { navController.popBackStack() },
                onForfeit = {
                    MatchStore.clear()
                    TournamentStore.clear()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.MatchResults.route) {
            RefreshProfileOnEnter(authViewModel)
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Guest"
            MatchResultsScreen(
                player1Name = MatchStore.player1Username.ifBlank { username },
                player2Name = MatchStore.player2Username.ifBlank { MatchStore.opponentUsername.ifBlank { "Opponent" } },
                onDone = {
                    MatchStore.clear()
                    authViewModel.refreshProfile()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Chat.route) { backStack ->
            RefreshProfileOnEnter(authViewModel)
            val region = backStack.arguments?.getString("region") ?: ""
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Guest"
            ChatScreen(
                region = region,
                username = username,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Challenge.route) { backStack ->
            RefreshProfileOnEnter(authViewModel)
            val region = backStack.arguments?.getString("region") ?: ""
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else ""
            ChallengeScreen(
                region = region,
                username = username,
                onNavigateBack = { navController.popBackStack() },
                onPlayAttempt = {
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.Challenge.route)
                    }
                },
            )
        }
        composable(Screen.Friends.route) {
            RefreshProfileOnEnter(authViewModel)
            val session = userSession
            LaunchedEffect(session) {
                if (session !is UserSession.LoggedIn) navController.popBackStack(Screen.Home.route, inclusive = false)
            }
            if (session is UserSession.LoggedIn) {
                FriendsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPlayFriend = { friendId -> navController.navigate(Screen.LobbyFriend.route(friendId)) },
                )
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
        composable(Screen.Region.route) {
            RefreshProfileOnEnter(authViewModel)
            val session = userSession
            LaunchedEffect(session) {
                if (session !is UserSession.LoggedIn) navController.popBackStack(Screen.Home.route, inclusive = false)
            }
            if (session is UserSession.LoggedIn) {
                val region = userProfile?.region ?: ""
                RegionMapScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToChallenge = {
                        if (region.isNotBlank()) {
                            navController.navigate(Screen.Challenge.route(region))
                        }
                    },
                )
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
        composable(Screen.League.route) {
            RefreshProfileOnEnter(authViewModel)
            val session = userSession
            LaunchedEffect(session) {
                if (session !is UserSession.LoggedIn) navController.popBackStack(Screen.Home.route, inclusive = false)
            }
            if (session is UserSession.LoggedIn) {
                LeagueScreen(
                    leagueLevel = userProfile?.leagueLevel ?: 0,
                    stars = userProfile?.stars ?: 0,
                    onNavigateBack = { navController.popBackStack() },
                )
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
        composable(Screen.Leaderboard.route) {
            RefreshProfileOnEnter(authViewModel)
            val session = userSession
            LaunchedEffect(session) {
                if (session !is UserSession.LoggedIn) navController.popBackStack(Screen.Home.route, inclusive = false)
            }
            if (session is UserSession.LoggedIn) {
                LeaderboardScreen(onNavigateBack = { navController.popBackStack() })
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
        composable(Screen.DailyTasks.route) {
            RefreshProfileOnEnter(authViewModel)
            val session = userSession
            LaunchedEffect(session) {
                if (session !is UserSession.LoggedIn) navController.popBackStack(Screen.Home.route, inclusive = false)
            }
            if (session is UserSession.LoggedIn) {
                DailyTasksScreen(onNavigateBack = { navController.popBackStack() })
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
        composable(Screen.LobbyFriend.route) { backStack ->
            RefreshProfileOnEnter(authViewModel)
            val friendId = backStack.arguments?.getString("friendId") ?: ""
            val session = userSession
            val username = if (session is UserSession.LoggedIn) session.username else "Guest"
            LobbyScreen(
                currentUsername = username,
                friendId = friendId,
                onNavigateBack = { navController.popBackStack() },
                onGameStart = {
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.LobbyFriend.route) { inclusive = true }
                    }
                },
            )
        }
    }

    // League promotion / demotion dialog (req. 6 — "dijalogom ili notifikacijom").
    var leagueEvent by remember { mutableStateOf<LeagueChangeEvent?>(null) }
    LaunchedEffect(Unit) {
        authViewModel.leagueChange.collect { leagueEvent = it }
    }
    leagueEvent?.let { event ->
        val leagueName = LEAGUE_NAMES.getOrElse(event.newLevel) { "League ${event.newLevel}" }
        AlertDialog(
            onDismissRequest = { leagueEvent = null },
            confirmButton = { TextButton(onClick = { leagueEvent = null }) { Text("OK") } },
            title = { Text(if (event.promoted) "🎉 You were promoted!" else "📉 You were demoted") },
            text = { Text("You are now in: $leagueName") },
        )
    }

    var rewardEvent by remember { mutableStateOf<PendingRewardEvent?>(null) }
    LaunchedEffect(Unit) {
        authViewModel.pendingReward.collect { rewardEvent = it }
    }
    rewardEvent?.let { event ->
        val period = if (event.weekly) "weekly" else "monthly"
        AlertDialog(
            onDismissRequest = {
                rewardEvent = null
                authViewModel.refreshProfile()
            },
            confirmButton = {
                TextButton(onClick = {
                    rewardEvent = null
                    authViewModel.refreshProfile()
                }) { Text("Super!") }
            },
            title = { Text("🎉🎟 Reward earned!") },
            text = {
                Text(
                    "Rank #${event.rank} on the $period leaderboard earns you " +
                        "${event.tokens} tokens!\n\n⭐🎊🎁",
                )
            },
        )
    }
}

@Composable
private fun RefreshProfileOnEnter(authViewModel: AuthViewModel) {
    val session by authViewModel.userSession.collectAsState()
    LaunchedEffect(session) {
        if (session is UserSession.LoggedIn) {
            authViewModel.refreshProfile()
        }
    }
}
