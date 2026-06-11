package uns.ac.rs.team23.slagalica.views.game.korakpokorak

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.viewmodels.KorakPhase
import uns.ac.rs.team23.slagalica.viewmodels.KorakPoKorakViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KorakPoKorakScreen(
    player1Name: String,
    player2Name: String,
    onFinish: () -> Unit,
    viewModel: KorakPoKorakViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val activeName = if (state.currentRound == 1) player1Name else player2Name
    val opponentName = if (state.currentRound == 1) player2Name else player1Name

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Korak po korak", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Round ${state.currentRound}/2 - $player1Name: ${state.player1Points} $player2Name: ${state.player2Points}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onFinish) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.phase == KorakPhase.PlayerTurn || state.phase == KorakPhase.OpponentChance) {
                        Text(
                            text = "${state.timeLeft}s",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = timerColor(state.timeLeft),
                            modifier = Modifier.padding(end = 16.dp),
                        )
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
            if (state.phase == KorakPhase.PlayerTurn || state.phase == KorakPhase.OpponentChance) {
                LinearProgressIndicator(
                    progress = { state.timeLeft / 10f },
                    modifier = Modifier.fillMaxWidth(),
                    color = timerColor(state.timeLeft),
                )
            }

            when (state.phase) {
                KorakPhase.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                KorakPhase.RoundIntro -> RoundIntroContent(
                    round = state.currentRound,
                    activeName = activeName,
                    maxPoints = 20,
                    onStart = viewModel::beginRound,
                )
                KorakPhase.PlayerTurn -> ActiveTurnContent(
                    state = state,
                    turnLabel = "$activeName's turn",
                    submitLabel = "Submit",
                    onAnswerChange = viewModel::onAnswerChange,
                    onSubmit = viewModel::submitAnswer,
                )
                KorakPhase.OpponentChance -> ActiveTurnContent(
                    state = state,
                    turnLabel = "$opponentName can steal! (5 pts)",
                    submitLabel = "Steal",
                    onAnswerChange = viewModel::onAnswerChange,
                    onSubmit = viewModel::submitAnswer,
                )
                KorakPhase.RoundEnd -> RoundEndContent(
                    state = state,
                    player1Name = player1Name,
                    player2Name = player2Name,
                    onNext = viewModel::prepareNextRound,
                )
                KorakPhase.GameOver -> GameOverContent(
                    state = state,
                    player1Name = player1Name,
                    player2Name = player2Name,
                    onFinish = onFinish,
                )
            }
        }
    }
}

@Composable
private fun timerColor(timeLeft: Int): Color = when {
    timeLeft >= 6 -> MaterialTheme.colorScheme.primary
    timeLeft >= 3 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}
