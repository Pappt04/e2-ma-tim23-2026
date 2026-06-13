package uns.ac.rs.team23.slagalica.views.game.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject
import uns.ac.rs.team23.slagalica.data.MatchStore
import uns.ac.rs.team23.slagalica.repository.MatchRepository

/**
 * When the host calls [advanceMatch], Firebase [currentGameIndex] moves ahead (or the match
 * becomes COMPLETED after the last game). The other player may still be on the finished game
 * screen — this pops back to [GameScreen] and syncs the local index.
 */
@Composable
fun MatchGameAdvanceEffect(
    thisGameIndex: Int,
    onLeaveGame: () -> Unit,
    matchRepository: MatchRepository = koinInject(),
) {
    val matchId = MatchStore.matchId
    var left by remember(matchId, thisGameIndex) { mutableStateOf(false) }
    LaunchedEffect(matchId, thisGameIndex) {
        if (matchId.isBlank()) return@LaunchedEffect
        matchRepository.observeMatch(matchId).collect { match ->
            if (left) return@collect
            val shouldLeave = match.status == "COMPLETED" ||
                match.currentGameIndex > thisGameIndex
            if (shouldLeave) {
                left = true
                MatchStore.currentGameIndex = match.currentGameIndex
                onLeaveGame()
            }
        }
    }
}
