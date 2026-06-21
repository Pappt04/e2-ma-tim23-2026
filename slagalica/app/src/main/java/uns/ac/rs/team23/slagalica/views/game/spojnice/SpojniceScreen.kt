package uns.ac.rs.team23.slagalica.views.game.spojnice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.data.MatchGameOrder
import uns.ac.rs.team23.slagalica.data.MatchStore
import uns.ac.rs.team23.slagalica.views.game.common.ForfeitAction
import uns.ac.rs.team23.slagalica.views.game.common.BlockGameBackNavigation
import uns.ac.rs.team23.slagalica.views.game.common.MatchPlayerHud
import uns.ac.rs.team23.slagalica.viewmodels.SpojnicePhase
import uns.ac.rs.team23.slagalica.viewmodels.SpojniceState
import uns.ac.rs.team23.slagalica.viewmodels.SpojniceViewModel
import uns.ac.rs.team23.slagalica.views.game.common.MatchGameAdvanceEffect
import uns.ac.rs.team23.slagalica.views.game.common.RoundReadyButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpojniceScreen(
    player1Name: String,
    player2Name: String,
    onFinish: () -> Unit,
    onForfeit: () -> Unit = {},
    viewModel: SpojniceViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.enter() }

    MatchGameAdvanceEffect(thisGameIndex = MatchGameOrder.SPOJNICE, onLeaveGame = onFinish)
    BlockGameBackNavigation()

    LaunchedEffect(state.phase) {
        if (state.phase == SpojnicePhase.GAME_OVER) {
            delay(3_000)
            onFinish()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Spojnice", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Round ${state.currentRound}/2  ·  $player1Name: ${state.player1Points}  $player2Name: ${state.player2Points}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = { ForfeitAction(onForfeit) },
                )
                MatchPlayerHud(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        },
    ) { innerPadding ->
        when (state.phase) {
            SpojnicePhase.ROUND_INTRO -> RoundIntro(
                state = state,
                player1Name = player1Name,
                player2Name = player2Name,
                onStart = viewModel::startRound,
                modifier = Modifier.padding(innerPadding),
            )
            SpojnicePhase.PLAYING_STARTER -> PlayingPhase(
                state = state,
                player1Name = player1Name,
                player2Name = player2Name,
                isStarterPhase = true,
                onSelectLeft = viewModel::selectLeft,
                onSelectRight = viewModel::selectRight,
                modifier = Modifier.padding(innerPadding),
            )
            SpojnicePhase.PLAYING_OPPONENT -> PlayingPhase(
                state = state,
                player1Name = player1Name,
                player2Name = player2Name,
                isStarterPhase = false,
                onSelectLeft = viewModel::selectLeft,
                onSelectRight = viewModel::selectRight,
                modifier = Modifier.padding(innerPadding),
            )
            SpojnicePhase.ROUND_END -> RoundEnd(
                state = state,
                player1Name = player1Name,
                player2Name = player2Name,
                myReady = if (MatchStore.isHost) state.p1Ready else state.p2Ready,
                opponentReady = if (MatchStore.isHost) state.p2Ready else state.p1Ready,
                onReady = viewModel::markReady,
                modifier = Modifier.padding(innerPadding),
            )
            SpojnicePhase.GAME_OVER -> GameOver(
                state = state,
                player1Name = player1Name,
                player2Name = player2Name,
                onFinish = onFinish,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun RoundIntro(
    state: SpojniceState,
    player1Name: String,
    player2Name: String,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val starterName = if (state.starterIsPlayer1) player1Name else player2Name
    Box(
        modifier = modifier
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
                Text("Round ${state.currentRound}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("$starterName starts", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Connect 5 left terms with 5 right terms.\n" +
                        "Starter: try each left term once. Only correct pairs stay visible.\n" +
                        "Then the other player gets 30 seconds to fix the rest (+2 per correct pair).",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Local test: you play both roles on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start Round") }
            }
        }
    }
}

@Composable
private fun PlayingPhase(
    state: SpojniceState,
    player1Name: String,
    player2Name: String,
    isStarterPhase: Boolean,
    onSelectLeft: (Int) -> Unit,
    onSelectRight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val starterName = if (state.starterIsPlayer1) player1Name else player2Name
    val opponentName = if (state.starterIsPlayer1) player2Name else player1Name
    val phaseTitle = if (isStarterPhase) {
        "Turn -> $starterName"
    } else {
        "Turn -> $opponentName"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = phaseTitle,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        if (!isStarterPhase) {
            LinearProgressIndicator(
                progress = { state.secondsLeft / 30f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Time left: ${state.secondsLeft}s", fontWeight = FontWeight.SemiBold)
        } else {
            LinearProgressIndicator(
                progress = { state.secondsLeft / 30f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Starter time left: ${state.secondsLeft}s", fontWeight = FontWeight.SemiBold)
            Text(
                text = "Starter progress: ${state.starterAttemptsUsedLeft.size} / ${state.pairs.size} left terms tried",
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(state.infoMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Left", fontWeight = FontWeight.Bold)
                state.pairs.forEachIndexed { index, pair ->
                    val isCorrect = state.correctLeftToRightIndex.containsKey(index)
                    val starterUsed = index in state.starterAttemptsUsedLeft
                    val lockedWrongStarter = isStarterPhase && starterUsed && !isCorrect
                    val clickable = when {
                        isCorrect -> false
                        isStarterPhase && starterUsed -> false
                        !isStarterPhase -> true
                        else -> true
                    }
                    MatchItem(
                        text = pair.left,
                        style = when {
                            isCorrect -> MatchStyle.Correct
                            lockedWrongStarter -> MatchStyle.StarterWrongHidden
                            state.selectedLeftIndex == index -> MatchStyle.Selected
                            else -> MatchStyle.Normal
                        },
                        onClick = { onSelectLeft(index) },
                        enabled = clickable,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Right", fontWeight = FontWeight.Bold)
                state.rightOptions.forEachIndexed { index, right ->
                    val usedInCorrect = state.correctLeftToRightIndex.values.contains(index)
                    val clickable = !usedInCorrect
                    MatchItem(
                        text = right,
                        style = when {
                            usedInCorrect -> MatchStyle.Correct
                            state.selectedRightIndex == index -> MatchStyle.Selected
                            else -> MatchStyle.Normal
                        },
                        onClick = { onSelectRight(index) },
                        enabled = clickable,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Local test: play starter, then play opponent on the same phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class MatchStyle {
    Normal,
    Selected,
    Correct,
    StarterWrongHidden,
}

@Composable
private fun MatchItem(
    text: String,
    style: MatchStyle,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val bg = when (style) {
        MatchStyle.Correct -> Color(0xFF2E7D32)
        MatchStyle.Selected -> Color(0xFF1976D2)
        MatchStyle.StarterWrongHidden -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        MatchStyle.Normal -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when (style) {
        MatchStyle.Correct, MatchStyle.Selected -> Color.White
        MatchStyle.StarterWrongHidden -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        MatchStyle.Normal -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .background(bg, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = fg)
    }
}

@Composable
private fun RoundEnd(
    state: SpojniceState,
    player1Name: String,
    player2Name: String,
    myReady: Boolean,
    opponentReady: Boolean,
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Round ${state.currentRound} finished", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        ScoreCard(player1Name, state.player1Points, player2Name, state.player2Points)
        Spacer(modifier = Modifier.weight(1f))
        RoundReadyButton(myReady = myReady, opponentReady = opponentReady, onReady = onReady)
    }
}

@Composable
private fun GameOver(
    state: SpojniceState,
    player1Name: String,
    player2Name: String,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val winner = when {
        state.player1Points > state.player2Points -> "$player1Name wins"
        state.player2Points > state.player1Points -> "$player2Name wins"
        else -> "Draw"
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Game Over",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        ScoreCard(player1Name, state.player1Points, player2Name, state.player2Points)
        Text(
            text = "Result: $winner",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text("Max 20 points, min 0 points", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Back to Games") }
    }
}

@Composable
private fun ScoreCard(p1Name: String, p1Pts: Int, p2Name: String, p2Pts: Int) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(p1Name, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = "$p1Pts pts",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "vs",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(p2Name, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = "$p2Pts pts",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
