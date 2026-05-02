package uns.ac.rs.team23.slagalica.views.game.korakpokorak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uns.ac.rs.team23.slagalica.viewmodels.KorakPoKorakState


@Composable
fun RoundEndContent(
    state: KorakPoKorakState,
    player1Name: String,
    player2Name: String,
    onNext: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Round 1 Complete",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Correct answer:", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = state.roundCorrectAnswer,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ScoreRow(player1Name, state.player1Points, player2Name, state.player2Points)
                }
            }

            // Show all clues
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.revealedClues.forEachIndexed { i, clue ->
                    item {
                        ClueCard(stepNumber = i + 1, clue = clue, highlight = false)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(0.7f),
            ) { Text("Start Round 2") }
        }
    }
}