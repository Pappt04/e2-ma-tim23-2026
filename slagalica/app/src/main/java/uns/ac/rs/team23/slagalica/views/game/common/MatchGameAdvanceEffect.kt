package uns.ac.rs.team23.slagalica.views.game.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.koin.compose.koinInject
import uns.ac.rs.team23.slagalica.data.MatchStore
import uns.ac.rs.team23.slagalica.repository.MatchRepository

/**
 * When the host calls [advanceMatch], Firebase [currentGameIndex] moves ahead.
 * The guest may still be on the finished game screen — this pops back to [GameScreen]
 * and syncs the local index so the next game can start on both devices.
 */
@Composable
fun MatchGameAdvanceEffect(
    thisGameIndex: Int,
    onLeaveGame: () -> Unit,
    matchRepository: MatchRepository = koinInject(),
) {
    val matchId = MatchStore.matchId
    LaunchedEffect(matchId, thisGameIndex) {
        if (matchId.isBlank()) return@LaunchedEffect
        matchRepository.observeMatch(matchId).collect { match ->
            if (match.currentGameIndex > thisGameIndex) {
                MatchStore.currentGameIndex = match.currentGameIndex
                onLeaveGame()
            }
        }
    }
}
