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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ko zna zna", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "$player1Name: ${state.player1Points}   $player2Name (sim): ${state.player2Points}",
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
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (state.phase) {
                KoZnaZnaPhase.ROUND_INTRO -> IntroContent(
                    onStart = viewModel::startRound,
                )
                KoZnaZnaPhase.PLAYING -> PlayingContent(
                    state = state,
                    onAnswer = viewModel::submitPlayer1Answer,
                )
                KoZnaZnaPhase.ROUND_END -> RoundEndContent(
                    state = state,
                    player1Name = player1Name,
                    player2Name = player2Name,
                    onPlayAgain = viewModel::resetToIntro,
                    onFinish = onFinish,
                )
            }
        }
    }
}

@Composable
private fun IntroContent(onStart: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Round 1", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Ko zna zna", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                HorizontalDivider()
                Text(
                    text = "5 questions, 4 choices each.\nRound time: 25 seconds.\nEach question: 5 seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Correct: +10, Wrong: -5.\nIf both are correct, faster player gets points.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text("Start Round")
                }
            }
        }
    }
}

@Composable
private fun PlayingContent(
    state: KoZnaZnaState,
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
            text = "Answer within 5 seconds per question. Opponent score in the title bar is simulated (you never see their picks).",
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
    onPlayAgain: () -> Unit,
    onFinish: () -> Unit,
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
        Text("Round Finished", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Your score: ${state.player1Points} pts", style = MaterialTheme.typography.titleMedium)
                Text("Opponent (simulated total): ${state.player2Points} pts", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                Text("Result: $result", fontWeight = FontWeight.Bold)
                Text(
                    text = "Score limits: max 50, min -25",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth()) {
            Text("Play Again")
        }
        OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Games")
        }
    }
}
