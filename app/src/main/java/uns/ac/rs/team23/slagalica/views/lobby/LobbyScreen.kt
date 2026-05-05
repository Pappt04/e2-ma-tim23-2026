package uns.ac.rs.team23.slagalica.views.lobby

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.viewmodels.LobbyState
import uns.ac.rs.team23.slagalica.viewmodels.LobbyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    currentUsername: String,
    onNavigateBack: () -> Unit,
    onGameStart: () -> Unit,
    viewModel: LobbyViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is LobbyState.Starting) onGameStart()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Match") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                is LobbyState.Searching -> {
                    SearchingContent()
                }

                is LobbyState.OpponentFound -> {
                    ReadyContent(
                        currentUsername = currentUsername,
                        opponentName = s.opponentName,
                        playerReady = false,
                        opponentReady = false,
                        onReady = viewModel::clickReady,
                    )
                }

                is LobbyState.YouAreReady -> {
                    ReadyContent(
                        currentUsername = currentUsername,
                        opponentName = s.opponentName,
                        playerReady = true,
                        opponentReady = false,
                        onReady = {},
                    )
                }

                is LobbyState.Countdown -> {
                    CountdownContent(
                        currentUsername = currentUsername,
                        opponentName = s.opponentName,
                        seconds = s.seconds,
                    )
                }

                LobbyState.Starting -> {
                    CountdownContent(
                        currentUsername = currentUsername,
                        opponentName = "",
                        seconds = 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Searching for an opponent...",
            style = MaterialTheme.typography.titleMedium,
        )
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
    }
}

@Composable
private fun ReadyContent(
    currentUsername: String,
    opponentName: String,
    playerReady: Boolean,
    opponentReady: Boolean,
    onReady: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerCard(
                name = currentUsername,
                isReady = playerReady,
                avatarColor = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "VS",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlayerCard(
                name = opponentName,
                isReady = opponentReady,
                avatarColor = MaterialTheme.colorScheme.tertiary,
            )
        }

        if (!playerReady) {
            Button(
                onClick = onReady,
                modifier = Modifier.fillMaxWidth(0.6f),
            ) {
                Text("Ready!", fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                text = "Waiting for opponent...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CountdownContent(
    currentUsername: String,
    opponentName: String,
    seconds: Int,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerCard(name = currentUsername, isReady = true, avatarColor = MaterialTheme.colorScheme.primary)
            Text("VS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            PlayerCard(name = opponentName, isReady = true, avatarColor = MaterialTheme.colorScheme.tertiary)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Game starts in",
            style = MaterialTheme.typography.titleMedium,
        )

        AnimatedContent(
            targetState = seconds,
            transitionSpec = {
                scaleIn(animationSpec = tween(300)) togetherWith scaleOut(animationSpec = tween(300))
            },
            label = "countdown",
        ) { count ->
            Text(
                text = if (count > 0) "$count" else "GO!",
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlayerCard(
    name: String,
    isReady: Boolean,
    avatarColor: androidx.compose.ui.graphics.Color,
) {
    Card(
        modifier = Modifier.size(width = 130.dp, height = 160.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isReady) "Ready" else "Waiting...",
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (isReady) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}
