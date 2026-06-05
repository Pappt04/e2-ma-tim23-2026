package uns.ac.rs.team23.slagalica.views.game.korakpokorak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@Composable
fun ScoreRow(
    p1Name: String,
    p1Pts: Int,
    p2Name: String,
    p2Pts: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(p1Name, style = MaterialTheme.typography.labelMedium)
            Text("$p1Pts pts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Text("vs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterVertically))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(p2Name, style = MaterialTheme.typography.labelMedium)
            Text("$p2Pts pts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

