package uns.ac.rs.team23.slagalica.views.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uns.ac.rs.team23.slagalica.data.MatchGameOrder
import uns.ac.rs.team23.slagalica.data.MatchStore
import uns.ac.rs.team23.slagalica.views.game.common.BlockGameBackNavigation
import uns.ac.rs.team23.slagalica.views.game.common.MatchPlayerHud
import uns.ac.rs.team23.slagalica.repository.MatchRepository

private val GAME_ORDER = MatchGameOrder.displayNames

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    playerName: String,
    opponentName: String,
    matchId: String = "",
    matchRepository: MatchRepository? = null,
    onForfeit: () -> Unit,
    onAllGamesFinished: () -> Unit = {},
    onMatchCompleted: (iWon: Boolean) -> Unit = {},
    onNavigateToKoZnaZna: () -> Unit = {},
    onNavigateToSpojnice: () -> Unit = {},
    onNavigateToKorakPoKorak: () -> Unit = {},
    onNavigateToMojBroj: () -> Unit = {},
    onNavigateToSkocko: () -> Unit = {},
    onNavigateToAsocijacije: () -> Unit = {},
) {
    var showForfeitDialog by remember { mutableStateOf(false) }
    var opponentAbandoned by remember(matchId) { mutableStateOf(false) }
    var navigatedToResults by remember(matchId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val navigators = listOf(
        onNavigateToKoZnaZna,
        onNavigateToSpojnice,
        onNavigateToAsocijacije,
        onNavigateToSkocko,
        onNavigateToKorakPoKorak,
        onNavigateToMojBroj,
    )

    // Drive navigation from Firebase (source of truth after advanceMatch).
    LaunchedEffect(matchId, matchRepository) {
        if (matchRepository == null) {
            // Preview only (no repository): play locally.
            val idx = MatchStore.currentGameIndex
            if (idx < navigators.size) navigators[idx]() else onAllGamesFinished()
            return@LaunchedEffect
        }
        if (matchId.isBlank()) {
            // No active match (e.g., stores were cleared mid-flow) — bail out instead of starting a
            // phantom local game (the "blank Opponent / random questions" bug after a forfeit).
            onAllGamesFinished()
            return@LaunchedEffect
        }
        matchRepository.observeMatch(matchId).collect { match ->
            MatchStore.currentGameIndex = match.currentGameIndex
            when {
                match.status == "CANCELLED" -> {
                    if (!navigatedToResults) {
                        navigatedToResults = true
                        onAllGamesFinished()
                    }
                }
                match.status == "COMPLETED" -> {
                    if (!navigatedToResults) {
                        navigatedToResults = true
                        // On a tie the match has no winnerId; player1 advances (tournament tiebreak).
                        val effectiveWinner = match.winnerId ?: match.player1Id
                        onMatchCompleted(effectiveWinner == MatchStore.myUid)
                    }
                }
                match.currentGameIndex < navigators.size -> navigators[match.currentGameIndex]()
                else -> onAllGamesFinished()
            }
        }
    }

    // Sync abandon state — remaining player continues the match (spec §3.f).
    LaunchedEffect(matchId, matchRepository) {
        if (matchId.isNotBlank() && matchRepository != null) {
            matchRepository.observeMatch(matchId).collect { match ->
                MatchStore.abandonedById = match.abandonedById.orEmpty()
                opponentAbandoned = MatchStore.opponentAbandoned
            }
        }
    }

    BlockGameBackNavigation()

    if (showForfeitDialog) {
        AlertDialog(
            onDismissRequest = { showForfeitDialog = false },
            title = { Text("Forfeit game?") },
            text = { Text("You will lose the match and all stars earned in this game.") },
            confirmButton = {
                Button(
                    onClick = {
                        showForfeitDialog = false
                        scope.launch {
                            if (matchId.isNotBlank() && matchRepository != null) {
                                // Completes the match (opponent wins). Non-cancellable so it finishes
                                // even as this screen leaves composition; the match-completed observer
                                // then routes both players to the same end screen as a normal finish.
                                withContext(NonCancellable) {
                                    matchRepository.abandonMatch(matchId)
                                }
                            } else {
                                onForfeit()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Forfeit") }
            },
            dismissButton = {
                TextButton(onClick = { showForfeitDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$playerName  vs  $opponentName") },
                actions = {
                    IconButton(onClick = { showForfeitDialog = true }) {
                        Icon(Icons.Default.Close, contentDescription = "Forfeit")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MatchPlayerHud()
            if (opponentAbandoned) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        text = "Your opponent left the match — you continue playing.",
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(
                text = "Match progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            GAME_ORDER.forEachIndexed { index, name ->
                val isDone = index < MatchStore.currentGameIndex
                val isCurrent = index == MatchStore.currentGameIndex
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isCurrent -> MaterialTheme.colorScheme.primaryContainer
                            isDone -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.surface
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = name,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            text = when {
                                isDone -> "Done"
                                isCurrent -> "Playing..."
                                else -> "Waiting"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
