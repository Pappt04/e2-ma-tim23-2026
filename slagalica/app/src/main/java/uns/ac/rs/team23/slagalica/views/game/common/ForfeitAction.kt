package uns.ac.rs.team23.slagalica.views.game.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import uns.ac.rs.team23.slagalica.data.MatchStore
import uns.ac.rs.team23.slagalica.repository.MatchRepository

@Composable
fun ForfeitAction(onForfeit: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val matchRepository = koinInject<MatchRepository>()

    IconButton(onClick = { showDialog = true }) {
        Icon(Icons.Default.Close, contentDescription = "Forfeit")
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Forfeit match?") },
            text = { Text("You'll lose this match and your opponent advances right away.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog = false
                        val matchId = MatchStore.matchId
                        scope.launch {
                            if (matchId.isNotBlank()) {
                                // Completes the match (opponent wins). Run non-cancellable so it
                                // finishes even though this screen leaves composition right after —
                                // both players then flow to the end screen via the match observer.
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
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        )
    }
}
