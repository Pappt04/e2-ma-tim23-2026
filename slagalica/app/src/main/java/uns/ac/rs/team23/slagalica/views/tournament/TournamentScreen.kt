package uns.ac.rs.team23.slagalica.views.tournament

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.models.LEAGUE_NAMES
import uns.ac.rs.team23.slagalica.models.leagueIconFor
import uns.ac.rs.team23.slagalica.repository.TournamentMatchResult
import uns.ac.rs.team23.slagalica.viewmodels.TournamentBracketUi
import uns.ac.rs.team23.slagalica.viewmodels.TournamentPlayer
import uns.ac.rs.team23.slagalica.viewmodels.TournamentUiState
import uns.ac.rs.team23.slagalica.viewmodels.TournamentViewModel
import uns.ac.rs.team23.slagalica.views.common.AvatarWithFrame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentScreen(
    onEnterMatch: () -> Unit,
    onExitToHome: () -> Unit,
    viewModel: TournamentViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val bracket by viewModel.bracket.collectAsState()

    LaunchedEffect(Unit) { viewModel.enter() }
    LaunchedEffect(Unit) {
        viewModel.navigateToGame.collect { onEnterMatch() }
    }

    // Back behaviour depends on phase: cancel+refund while waiting, leave on terminal screens,
    // blocked mid-flow.
    when (state) {
        is TournamentUiState.Searching, is TournamentUiState.ReadyCheck ->
            BackHandler { viewModel.cancel(); onExitToHome() }
        is TournamentUiState.Eliminated, is TournamentUiState.Victory,
        is TournamentUiState.Defeat, is TournamentUiState.Error ->
            BackHandler { viewModel.leave(); onExitToHome() }
        else -> BackHandler(enabled = true) { /* blocked mid-tournament */ }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Tournament") }) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                bracket?.let { TournamentBracketPanel(it) }

                when (val s = state) {
                TournamentUiState.Joining -> Loading("Joining tournament…")
                TournamentUiState.Syncing -> Loading("Finishing up…")
                TournamentUiState.EnteringMatch -> Loading("Starting match…")

                is TournamentUiState.Searching -> Searching(s.players)

                is TournamentUiState.ReadyCheck -> ReadyCheckContent(
                    title = "All players found!",
                    subtitle = "Everyone must be ready to start the two semifinals.",
                    players = s.players,
                    iAmReady = s.iAmReady,
                    onReady = viewModel::clickReady,
                )

                is TournamentUiState.SemifinalWon -> SemifinalWonContent(
                    reward = s.reward,
                    onContinue = viewModel::continueFromSemifinal,
                )

                TournamentUiState.WaitingForOther -> WaitingForOther()

                is TournamentUiState.FinalReadyCheck -> ReadyCheckContent(
                    title = "Finalists!",
                    subtitle = "Both winners must be ready to start the final.",
                    players = s.finalists,
                    iAmReady = s.iAmReady,
                    onReady = viewModel::clickReady,
                )

                is TournamentUiState.Eliminated -> ResultContent(
                    emoji = "💔",
                    title = "Eliminated",
                    message = "You lost in the semifinals. No reward this time — better luck next tournament!",
                    color = MaterialTheme.colorScheme.error,
                    onExit = { viewModel.leave(); onExitToHome() },
                )

                is TournamentUiState.Victory -> ResultContent(
                    emoji = "🏆",
                    title = "Tournament champion!",
                    message = rewardSummary(s.reward, isWinner = true),
                    color = MaterialTheme.colorScheme.primary,
                    celebrate = true,
                    onExit = { viewModel.leave(); onExitToHome() },
                )

                is TournamentUiState.Defeat -> ResultContent(
                    emoji = "🥈",
                    title = "Runner-up",
                    message = rewardSummary(s.reward, isWinner = false),
                    color = MaterialTheme.colorScheme.tertiary,
                    onExit = { viewModel.leave(); onExitToHome() },
                )

                is TournamentUiState.Error -> ResultContent(
                    emoji = "⚠️",
                    title = "Tournament ended",
                    message = s.message,
                    color = MaterialTheme.colorScheme.error,
                    onExit = { viewModel.leave(); onExitToHome() },
                )
                }
            }
        }
    }
}

@Composable
private fun Loading(label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Searching(players: List<TournamentPlayer>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Looking for players…",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${players.size} / 4 joined",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
        PlayerGrid(players = players, slots = 4)
        Text(
            text = "A tournament needs 4 players. Entry costs 3 tokens (refunded if it doesn't start).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReadyCheckContent(
    title: String,
    subtitle: String,
    players: List<TournamentPlayer>,
    iAmReady: Boolean,
    onReady: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        PlayerGrid(players = players, slots = players.size)
        if (iAmReady) {
            Text(
                text = "Ready! Waiting for the others…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Button(onClick = onReady, modifier = Modifier.fillMaxWidth(0.7f)) {
                Text("Ready!", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SemifinalWonContent(reward: TournamentMatchResult?, onContinue: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("🎉", fontSize = 72.sp)
        Text(
            text = "You won this match!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (reward != null) {
                    "You advance to the final!\n+${reward.tokensAwarded} 🎟 · +${reward.starsAwarded} ⭐"
                } else {
                    "You advance to the final!"
                },
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth(0.7f)) {
            Text("Go to finals", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WaitingForOther() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("🎉", fontSize = 56.sp)
        Text(
            text = "You won your match!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        CircularProgressIndicator()
        Text(
            text = "Waiting for the other match to finish…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultContent(
    emoji: String,
    title: String,
    message: String,
    color: androidx.compose.ui.graphics.Color,
    celebrate: Boolean = false,
    onExit: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val scale = if (celebrate) {
            val transition = rememberInfiniteTransition(label = "celebrate")
            transition.animateFloat(
                initialValue = 0.9f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "pulse",
            ).value
        } else 1f

        AnimatedContent(
            targetState = emoji,
            transitionSpec = { (scaleIn(tween(400)) + fadeIn()) togetherWith fadeOut() },
            label = "result-emoji",
        ) { e ->
            Text(e, fontSize = 72.sp, modifier = Modifier.scale(scale))
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = message,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onExit, modifier = Modifier.fillMaxWidth(0.7f)) {
            Text("Back to app")
        }
    }
}

@Composable
private fun PlayerGrid(players: List<TournamentPlayer>, slots: Int) {
    val rows = (maxOf(slots, players.size) + 1) / 2
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (c in 0 until 2) {
                    val idx = r * 2 + c
                    if (idx < players.size) {
                        PlayerCard(players[idx])
                    } else if (idx < slots) {
                        EmptySlotCard()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerCard(player: TournamentPlayer) {
    Card(
        modifier = Modifier.size(width = 150.dp, height = 150.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (player.ready) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AvatarWithFrame(avatarIndex = player.avatarIndex, size = 52.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = player.username,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = "${leagueIconFor(player.leagueLevel)} ${LEAGUE_NAMES.getOrElse(player.leagueLevel) { "League ${player.leagueLevel}" }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (player.ready) "✓ Ready" else "Waiting…",
                style = MaterialTheme.typography.labelMedium,
                color = if (player.ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (player.ready) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun EmptySlotCard() {
    Card(
        modifier = Modifier.size(width = 150.dp, height = 150.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Waiting…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun rewardSummary(reward: TournamentMatchResult?, isWinner: Boolean): String = when {
    reward == null && isWinner -> "You won the tournament!"
    reward == null -> "You reached the final."
    isWinner -> "You won the tournament!\n+${reward.tokensAwarded} tokens · +${reward.starsAwarded} stars (incl. 10 bonus)."
    reward.starsAwarded >= 0 -> "You reached the final and earned +${reward.starsAwarded} stars."
    else -> "You reached the final. Stars: ${reward.starsAwarded}."
}

@Composable
private fun TournamentBracketPanel(bracket: TournamentBracketUi) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Bracket",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (!bracket.pairingsKnown) {
                Text(
                    text = "Semifinal pairings are drawn randomly once all 4 players are ready.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BracketSemifinalColumn(
                    label = "Semifinal 1",
                    players = bracket.semi1,
                    winner = bracket.semi1Winner,
                    modifier = Modifier.weight(1f),
                )
                BracketSemifinalColumn(
                    label = "Semifinal 2",
                    players = bracket.semi2,
                    winner = bracket.semi2Winner,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "↓",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BracketFinalRow(
                semi1Winner = bracket.semi1Winner,
                semi2Winner = bracket.semi2Winner,
                champion = bracket.finalWinner,
            )
        }
    }
}

@Composable
private fun BracketSemifinalColumn(
    label: String,
    players: List<TournamentPlayer?>,
    winner: TournamentPlayer?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        BracketPlayerSlot(player = players.getOrNull(0), isWinner = winner?.uid == players.getOrNull(0)?.uid)
        Text("vs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BracketPlayerSlot(player = players.getOrNull(1), isWinner = winner?.uid == players.getOrNull(1)?.uid)
    }
}

@Composable
private fun BracketFinalRow(
    semi1Winner: TournamentPlayer?,
    semi2Winner: TournamentPlayer?,
    champion: TournamentPlayer?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Final",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BracketPlayerSlot(
                player = semi1Winner,
                isWinner = champion?.uid == semi1Winner?.uid,
                placeholder = "Winner SF1",
            )
            Text("vs", style = MaterialTheme.typography.labelSmall)
            BracketPlayerSlot(
                player = semi2Winner,
                isWinner = champion?.uid == semi2Winner?.uid,
                placeholder = "Winner SF2",
            )
        }
        if (champion != null) {
            Text(
                text = "🏆 ${champion.username}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun BracketPlayerSlot(
    player: TournamentPlayer?,
    isWinner: Boolean,
    placeholder: String = "Waiting…",
) {
    val bg = when {
        isWinner -> MaterialTheme.colorScheme.primaryContainer
        player != null -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(min = 120.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isWinner) 6.dp else 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (player != null) {
                AvatarWithFrame(avatarIndex = player.avatarIndex, size = 36.dp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = player.username,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                if (isWinner) {
                    Text(
                        text = "Winner",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
