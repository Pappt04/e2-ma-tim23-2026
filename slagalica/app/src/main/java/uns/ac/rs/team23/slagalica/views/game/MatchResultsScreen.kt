package uns.ac.rs.team23.slagalica.views.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.data.MatchGameOrder
import uns.ac.rs.team23.slagalica.data.MatchStore
import uns.ac.rs.team23.slagalica.network.dto.GameResultDto
import uns.ac.rs.team23.slagalica.network.dto.MatchResponseDto
import uns.ac.rs.team23.slagalica.repository.MatchRepository
import uns.ac.rs.team23.slagalica.viewmodels.DailyMissionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchResultsScreen(
    player1Name: String,
    player2Name: String,
    onDone: () -> Unit,
    matchRepository: MatchRepository = koinInject(),
    missionViewModel: DailyMissionViewModel = koinViewModel(),
) {
    var match by remember { mutableStateOf<MatchResponseDto?>(null) }
    var gameResults by remember { mutableStateOf<List<GameResultDto>>(emptyList()) }

    // Credit the "win a match" / "play a friendly match" daily missions (spec 12).
    LaunchedEffect(MatchStore.matchId) {
        val id = MatchStore.matchId
        if (id.isNotBlank()) missionViewModel.onMatchFinished(id)
    }

    LaunchedEffect(MatchStore.matchId) {
        val id = MatchStore.matchId
        if (id.isBlank()) return@LaunchedEffect
        matchRepository.observeMatch(id).collect { match = it }
    }
    LaunchedEffect(MatchStore.matchId) {
        val id = MatchStore.matchId
        if (id.isBlank()) return@LaunchedEffect
        matchRepository.observeGameResults(id).collect { gameResults = it }
    }

    val m = match
    // winnerId is authoritative (set on normal completion and on forfeit); only a true tie is a draw.
    val winnerText = when {
        m == null -> ""
        m.winnerId == m.player1Id -> "$player1Name wins!"
        m.winnerId == m.player2Id -> "$player2Name wins!"
        else -> "It's a draw!"
    }
    val forfeitText = when (m?.abandonedById) {
        null -> ""
        m?.player1Id -> "$player1Name forfeited the match."
        m?.player2Id -> "$player2Name forfeited the match."
        else -> ""
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Match statistics") })
        },
    ) { innerPadding ->
        if (m == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Match complete",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Total score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        ScoreRow(player1Name, m.player1TotalScore, player2Name, m.player2TotalScore)
                        HorizontalDivider()
                        Text(
                            text = winnerText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (forfeitText.isNotEmpty()) {
                            Text(
                                text = forfeitText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                Text(
                    text = "Per game",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (gameResults.isEmpty()) {
                    Text(
                        text = "Loading game breakdown…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    gameResults.forEach { result ->
                        GameResultCard(
                            gameName = MatchGameOrder.displayName(result.gameIndex),
                            player1Name = player1Name,
                            player2Name = player2Name,
                            p1Score = result.player1Score,
                            p2Score = result.player2Score,
                        )
                    }
                }

                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Back to home")
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(
    player1Name: String,
    p1Score: Int,
    player2Name: String,
    p2Score: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(player1Name, style = MaterialTheme.typography.labelMedium)
            Text(
                text = "$p1Score",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text("vs", modifier = Modifier.align(Alignment.CenterVertically))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(player2Name, style = MaterialTheme.typography.labelMedium)
            Text(
                text = "$p2Score",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun GameResultCard(
    gameName: String,
    player1Name: String,
    player2Name: String,
    p1Score: Int,
    p2Score: Int,
) {
    val gameWinner = when {
        p1Score > p2Score -> player1Name
        p2Score > p1Score -> player2Name
        else -> "Draw"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(gameName, fontWeight = FontWeight.SemiBold)
            Text("$player1Name: $p1Score   ·   $player2Name: $p2Score", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (gameWinner == "Draw") "Draw" else "$gameWinner won this game",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
