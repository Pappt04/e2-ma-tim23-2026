package uns.ac.rs.team23.slagalica.views.game.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import uns.ac.rs.team23.slagalica.data.MatchStore
import uns.ac.rs.team23.slagalica.network.dto.MatchResponseDto
import uns.ac.rs.team23.slagalica.repository.MatchRepository

/** Fallback wait (client-side) before the host advances past a slow/absent opponent. */
private const val READY_FALLBACK_MILLIS = 30_000L

/**
 * Between-games ready gate shown on each game's game-over screen. Both players see the final
 * scores and a "Continue" button; the match only advances to the next game once BOTH confirm
 * (or after a fallback window so an absent opponent can't stall the match). This replaces the
 * per-game fixed `delay(...)` auto-advance that used to cut summaries short and skip the button.
 *
 * The host (player1) is the only one that calls [MatchRepository.advanceFromGameOver]; the other
 * player is popped out by [MatchGameAdvanceEffect] once the shared game index moves.
 */
@Composable
fun GameOverGate(
    gameType: String,
    player1Name: String,
    player2Name: String,
    player1Score: Int,
    player2Score: Int,
    modifier: Modifier = Modifier,
    matchRepository: MatchRepository = koinInject(),
) {
    val matchId = MatchStore.matchId
    val scope = rememberCoroutineScope()
    var readyTapped by remember(matchId) { mutableStateOf(false) }
    var match by remember(matchId) { mutableStateOf<MatchResponseDto?>(null) }

    LaunchedEffect(matchId) {
        if (matchId.isBlank()) return@LaunchedEffect
        matchRepository.observeMatch(matchId).collect { match = it }
    }

    val m = match
    val iAmPlayer1 = m?.player1Id == MatchStore.myUid
    val iAmReady = if (iAmPlayer1) m?.player1Ready == true else m?.player2Ready == true
    val opponentReady = if (iAmPlayer1) m?.player2Ready == true else m?.player1Ready == true

    // The ready flags are shared with the lobby's ready screen, so they may still read `true` from
    // there when this gate first mounts (before recordGameResult's reset propagates). Only arm the
    // gate once we've observed both flags reset to false — otherwise the host could auto-advance
    // instantly on stale lobby state and skip the button.
    var gateArmed by remember(matchId) { mutableStateOf(false) }
    LaunchedEffect(iAmReady, opponentReady) {
        if (!iAmReady && !opponentReady) gateArmed = true
    }

    // Host advances the moment both players confirm (after the gate is armed).
    LaunchedEffect(gateArmed, iAmReady, opponentReady, m?.status) {
        if (gateArmed && MatchStore.isHost && iAmReady && opponentReady && m?.status == "IN_PROGRESS") {
            matchRepository.advanceFromGameOver(matchId, gameType)
        }
    }

    // Fallback (host only): don't wait forever on a slow opponent; an already-absent opponent
    // advances almost immediately (spec: minimise waiting on a player who left).
    LaunchedEffect(matchId, gameType) {
        if (matchId.isBlank()) return@LaunchedEffect
        delay(if (MatchStore.opponentAbandoned) 1_000L else READY_FALLBACK_MILLIS)
        if (MatchStore.isHost) matchRepository.advanceFromGameOver(matchId, gameType)
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Game over",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text("$player1Name: $player1Score", style = MaterialTheme.typography.titleMedium)
        Text("$player2Name: $player2Score", style = MaterialTheme.typography.titleMedium)
        RoundReadyButton(
            myReady = iAmReady || readyTapped,
            opponentReady = opponentReady,
            onReady = {
                readyTapped = true
                scope.launch { matchRepository.markReady(matchId) }
            },
        )
    }
}
