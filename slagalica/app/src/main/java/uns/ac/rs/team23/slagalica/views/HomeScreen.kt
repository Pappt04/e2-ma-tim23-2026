package uns.ac.rs.team23.slagalica.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Notifications
import uns.ac.rs.team23.slagalica.views.game.common.MatchPlayerHud

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    username: String,
    isRegistered: Boolean,
    onNavigateToPlay: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToChat: () -> Unit = {},
    onNavigateToChallenge: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToRegion: () -> Unit = {},
    onNavigateToLeague: () -> Unit = {},
    onNavigateToLeaderboard: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SLAGALICA",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!isRegistered) {
                        Text(
                            text = "Guest — play only. Register for profile, stats, and rankings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                HorizontalDivider()
                val drawerItemColors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(0.dp),
                    colors = drawerItemColors,
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    label = { Text("Play") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToPlay()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(0.dp),
                )
                if (isRegistered) {
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Person, contentDescription = null) },
                        label = { Text("Profile") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToProfile()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(0.dp),
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        label = { Text("Notifications") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToNotifications()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(0.dp),
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Group, contentDescription = null) },
                        label = { Text("Friends") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToFriends()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(0.dp),
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Map, contentDescription = null) },
                        label = { Text("Regions") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToRegion()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(0.dp),
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null) },
                        label = { Text("Leagues") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToLeague()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(0.dp),
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Forum, contentDescription = null) },
                        label = { Text("Regional Chat") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToChat()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(0.dp),
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.MilitaryTech, contentDescription = null) },
                        label = { Text("Challenges") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToChallenge()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(0.dp),
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Star, contentDescription = null) },
                        label = { Text("Leaderboard") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToLeaderboard()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(0.dp),
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            // TODO: navigate to settings
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(0.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider()
                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                    label = { Text(if (isRegistered) "Log out" else "Exit") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(0.dp),
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Slagalica") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Home, contentDescription = "Open menu")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (isRegistered) {
                    MatchPlayerHud(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                Text(
                    text = "Welcome, $username!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isRegistered) {
                        "Ready to play?"
                    } else {
                        "You can start a match from Play. Create an account for profile, statistics, competitions, and leaderboard."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onNavigateToPlay,
                    modifier = Modifier.fillMaxWidth(0.7f),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("Play")
                }
                }
            }
        }
    }
}
