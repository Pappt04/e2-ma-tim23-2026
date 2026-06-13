package uns.ac.rs.team23.slagalica.views.game.koznazna

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.viewmodels.KoZnaZnaPhase
import uns.ac.rs.team23.slagalica.viewmodels.KoZnaZnaQuestion
import uns.ac.rs.team23.slagalica.viewmodels.KoZnaZnaState
import uns.ac.rs.team23.slagalica.viewmodels.KoZnaZnaViewModel
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KoZnaZnaScreen(
    player1Name: String,
    player2Name: String,
    onFinish: () -> Unit,
    viewModel: KoZnaZnaViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.enter() }

    // Both clients reach ROUND_END together (driven by the shared doc); auto-advance to the next game.
    LaunchedEffect(state.phase) {
        if (state.phase == KoZnaZnaPhase.ROUND_END) {
            delay(4_000)
            onFinish()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ko zna zna", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "$player1Name: ${state.player1Points}   $player2Name: ${state.player2Points}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            when (state.phase) {
                KoZnaZnaPhase.ROUND_INTRO -> WaitingContent()
                KoZnaZnaPhase.PLAYING ->
                    if (state.questions.isEmpty()) {
                        WaitingContent()
                    } else {
                        PlayingContent(
                            state = state,
                            player2Name = player2Name,
                            onAnswer = viewModel::submitPlayer1Answer,
                        )
                    }
                KoZnaZnaPhase.ROUND_END -> RoundEndContent(
                    state = state,
                    player1Name = player1Name,
                    player2Name = player2Name,
                )
            }
        }
    }
}

@Composable
private fun WaitingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = "Syncing with your opponent...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlayingContent(
    state: KoZnaZnaState,
    player2Name: String,
    onAnswer: (Int) -> Unit,
) {
    val question = state.questions[state.currentQuestionIndex]
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LinearProgressIndicator(
            progress = { state.roundSecondsLeft / 25f },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Round: ${state.roundSecondsLeft}s", fontWeight = FontWeight.SemiBold)
            Text("Question: ${state.questionSecondsLeft}s", fontWeight = FontWeight.SemiBold)
        }

        QuestionHeader(
            index = state.currentQuestionIndex + 1,
            total = state.questions.size,
            question = question,
        )

        question.options.forEachIndexed { index, option ->
            val selected = state.player1SelectedIndex == index
            OutlinedButton(
                onClick = { onAnswer(index) },
                enabled = state.player1SelectedIndex == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (selected) "Selected: $option" else option,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (state.infoMessage.isNotBlank()) {
            Text(
                text = state.infoMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = if (state.player2SelectedIndex != null)
                "$player2Name has answered."
            else
                "Answer within 5 seconds. Waiting for $player2Name...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuestionHeader(index: Int, total: Int, question: KoZnaZnaQuestion) {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Question $index / $total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(question.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RoundEndContent(
    state: KoZnaZnaState,
    player1Name: String,
    player2Name: String,
) {
    val result = when {
        state.player1Points > state.player2Points -> "$player1Name wins"
        state.player2Points > state.player1Points -> "$player2Name wins"
        else -> "Draw"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Round Finished",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Your score", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = "${state.player1Points} pts",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text("$player2Name", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = "${state.player2Points} pts",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                HorizontalDivider()
                Text(
                    text = "Result: $result",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Score limits: max 50, min -25",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Next game starting...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
