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
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uns.ac.rs.team23.slagalica.data.MatchStore
import uns.ac.rs.team23.slagalica.repository.MatchRepository

private val GAME_ORDER = listOf(
    "Ko zna zna",
    "Spojnice",
    "Asocijacije",
    "Skočko",
    "Korak po korak",
    "Moj broj",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    playerName: String,
    opponentName: String,
    matchId: String = "",
    matchRepository: MatchRepository? = null,
    onForfeit: () -> Unit,
    onAllGamesFinished: () -> Unit = {},
    onNavigateToKoZnaZna: () -> Unit = {},
    onNavigateToSpojnice: () -> Unit = {},
    onNavigateToKorakPoKorak: () -> Unit = {},
    onNavigateToMojBroj: () -> Unit = {},
    onNavigateToSkocko: () -> Unit = {},
    onNavigateToAsocijacije: () -> Unit = {},
) {
    var showForfeitDialog by remember { mutableStateOf(false) }
    var showOpponentLeftDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val gameIndex = MatchStore.currentGameIndex

    val navigators = listOf(
        onNavigateToKoZnaZna,
        onNavigateToSpojnice,
        onNavigateToAsocijacije,
        onNavigateToSkocko,
        onNavigateToKorakPoKorak,
        onNavigateToMojBroj,
    )

    LaunchedEffect(gameIndex) {
        if (gameIndex < navigators.size) {
            navigators[gameIndex]()
        } else {
            onAllGamesFinished()
        }
    }

    // Instant, listener-based abandonment detection (replaces the old 3s poll).
    LaunchedEffect(matchId, matchRepository) {
        if (matchId.isNotBlank() && matchRepository != null) {
            matchRepository.observeMatch(matchId).collect { match ->
                if (match.status == "ABANDONED") {
                    showOpponentLeftDialog = true
                }
            }
        }
    }

    if (showOpponentLeftDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Opponent left") },
            text = { Text("Your opponent abandoned the match. You win!") },
            confirmButton = {
                Button(onClick = {
                    showOpponentLeftDialog = false
                    onForfeit()
                }) { Text("OK") }
            },
        )
    }

    if (showForfeitDialog) {
        AlertDialog(
            onDismissRequest = { showForfeitDialog = false },
            title = { Text("Forfeit game?") },
            text = { Text("You will lose the match and all stars earned in this game.") },
            confirmButton = {
                Button(
                    onClick = onForfeit,
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
            Text(
                text = "Match progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            GAME_ORDER.forEachIndexed { index, name ->
                val isDone = index < gameIndex
                val isCurrent = index == gameIndex
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
