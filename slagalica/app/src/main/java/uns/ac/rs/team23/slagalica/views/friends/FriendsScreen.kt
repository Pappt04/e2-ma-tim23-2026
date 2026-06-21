package uns.ac.rs.team23.slagalica.views.friends

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.models.Friend
import uns.ac.rs.team23.slagalica.models.LEAGUE_NAMES
import uns.ac.rs.team23.slagalica.viewmodels.FriendsViewModel
import uns.ac.rs.team23.slagalica.views.common.AvatarWithFrame

private const val FRIEND_QR_PREFIX = "slagalica-friend:"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onNavigateBack: () -> Unit,
    onPlayFriend: (String) -> Unit,
    viewModel: FriendsViewModel = koinViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents != null && contents.startsWith(FRIEND_QR_PREFIX)) {
            viewModel.addFriendByUsername(contents.removePrefix(FRIEND_QR_PREFIX))
        }
    }
    fun launchScanner() {
        scanLauncher.launch(
            ScanOptions()
                .setPrompt("Scan a friend's QR code")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }
    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchScanner()
        }
    fun onScanClick() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ui.info?.let { msg ->
        androidx.compose.runtime.LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearInfo()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = viewModel.searchQuery,
                        onValueChange = viewModel::onSearchChange,
                        label = { Text("Search users") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = ::onScanClick) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                    }
                }
            }

            if (ui.searchResults.isNotEmpty()) {
                item {
                    Text(
                        "Search results",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(ui.searchResults, key = { "search_${it.uid}" }) { user ->
                    SearchResultRow(user) { viewModel.addFriendByUid(user.uid) }
                }
            }

            item {
                Text(
                    "My friends (${ui.friends.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (ui.friends.isEmpty()) {
                item {
                    Text(
                        "You have no friends yet. Search for users or scan a QR code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(ui.friends, key = { it.uid }) { friend ->
                    FriendCard(
                        friend = friend,
                        frameRank = viewModel.frameRankFor(friend.region),
                        onPlay = { onPlayFriend(friend.uid) },
                        onRemove = { viewModel.removeFriend(friend.uid) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(user: Friend, onAdd: () -> Unit) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarWithFrame(avatarIndex = user.avatarIndex, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(user.username, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onAdd) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }
    }
}

@Composable
private fun FriendCard(
    friend: Friend,
    frameRank: Int,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarWithFrame(avatarIndex = friend.avatarIndex, size = 52.dp, frameRank = frameRank)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(friend.username, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (friend.isOnline) "● online" else "○ offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (friend.isOnline) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = LEAGUE_NAMES.getOrElse(friend.leagueLevel) { "League ${friend.leagueLevel}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${friend.stars}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (friend.monthlyRank > 0) "Monthly rank: #${friend.monthlyRank}" else "Unranked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = onPlay, enabled = friend.isPlayable) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (friend.inMatch) "In game" else if (!friend.sessionActive) "Offline" else "Play")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
