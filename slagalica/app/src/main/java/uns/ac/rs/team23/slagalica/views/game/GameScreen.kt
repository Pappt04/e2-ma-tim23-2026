package uns.ac.rs.team23.slagalica.views.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uns.ac.rs.team23.slagalica.repository.MatchRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    playerName: String,
    opponentName: String,
    matchId: String = "",
    matchRepository: MatchRepository? = null,
    onForfeit: () -> Unit,
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

    // Poll for opponent abandon while this screen is active
    DisposableEffect(matchId, matchRepository) {
        var pollingJob: Job? = null
        if (matchId.isNotBlank() && matchRepository != null) {
            pollingJob = scope.launch {
                while (true) {
                    delay(3_000)
                    matchRepository.getCurrentMatch().onSuccess { match ->
                        if (match != null && match.status == "ABANDONED") {
                            showOpponentLeftDialog = true
                            return@launch
                        }
                    }
                }
            }
        }
        onDispose { pollingJob?.cancel() }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Games", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            gameList(
                onNavigateToKoZnaZna,
                onNavigateToSpojnice,
                onNavigateToKorakPoKorak,
                onNavigateToMojBroj,
                onNavigateToSkocko,
                onNavigateToAsocijacije,
            ).forEach { game ->
                OutlinedButton(
                    onClick = { if (game.route != null) game.route.invoke() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = game.route != null,
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(game.name, fontWeight = FontWeight.SemiBold)
                        Text(game.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private data class GameInfo(
    val name: String,
    val description: String,
    val route: (() -> Unit)? = null,
)

@Composable
private fun gameList(
    onNavigateToKoZnaZna: () -> Unit,
    onNavigateToSpojnice: () -> Unit,
    onNavigateToKorakPoKorak: () -> Unit,
    onNavigateToMojBroj: () -> Unit,
    onNavigateToSkocko: () -> Unit,
    onNavigateToAsocijacije: () -> Unit,
) = listOf(
    GameInfo("Ko zna zna", "1 round · 25s · up to 50 pts", onNavigateToKoZnaZna),
    GameInfo("Spojnice", "2 rounds · 60s · up to 20 pts", onNavigateToSpojnice),
    GameInfo("Asocijacije", "2 rounds · 4min · up to 60 pts", onNavigateToAsocijacije),
    GameInfo("Skočko", "2 rounds · 60s · up to 40 pts", onNavigateToSkocko),
    GameInfo("Korak po korak", "2 rounds · 140s · up to 40 pts", onNavigateToKorakPoKorak),
    GameInfo("Moj broj", "2 rounds · 2min · up to 20 pts", onNavigateToMojBroj),
)
