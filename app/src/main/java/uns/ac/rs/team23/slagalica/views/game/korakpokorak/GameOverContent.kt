package uns.ac.rs.team23.slagalica.views.game.korakpokorak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uns.ac.rs.team23.slagalica.viewmodels.KorakPoKorakState


@Composable
fun GameOverContent(
    state: KorakPoKorakState,
    player1Name: String,
    player2Name: String,
    onFinish: () -> Unit,
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
                text = "Game Over",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Round 2 answer:", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = state.roundCorrectAnswer,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ScoreRow(player1Name, state.player1Points, player2Name, state.player2Points)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    val winner = when {
                        state.player1Points > state.player2Points -> "$player1Name wins!"
                        state.player2Points > state.player1Points -> "$player2Name wins!"
                        else -> "It's a draw!"
                    }
                    Text(
                        text = winner,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(0.7f),
            ) { Text("Back to Game") }
        }
    }
}