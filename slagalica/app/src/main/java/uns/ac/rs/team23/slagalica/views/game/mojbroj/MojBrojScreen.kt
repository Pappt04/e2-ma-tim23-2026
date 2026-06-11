package uns.ac.rs.team23.slagalica.views.game.mojbroj

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.viewmodels.ExprToken
import uns.ac.rs.team23.slagalica.viewmodels.MojBrojPhase
import uns.ac.rs.team23.slagalica.viewmodels.MojBrojState
import uns.ac.rs.team23.slagalica.viewmodels.MojBrojViewModel
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MojBrojScreen(
    player1Name: String,
    player2Name: String,
    onFinish: () -> Unit,
    viewModel: MojBrojViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastShake = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
                val force = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
                val now = System.currentTimeMillis()
                if (force > 12f && now - lastShake > 800) {
                    lastShake = now
                    viewModel.onStopPressed()
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (accel != null) sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }

    val activeName = if (state.currentRound == 1) player1Name else player2Name

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Moj broj", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Round ${state.currentRound}/2  ·  $player1Name: ${state.player1Points}  $player2Name: ${state.player2Points}",
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
                actions = {
                    val timerText = when (state.phase) {
                        MojBrojPhase.TargetCountdown, MojBrojPhase.NumbersCountdown -> "${state.setupSecondsLeft}s"
                        MojBrojPhase.Player1Input, MojBrojPhase.Player2Input -> "${state.playSecondsLeft}s"
                        else -> null
                    }
                    if (timerText != null) {
                        Text(
                            text = timerText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = playTimerColor(state.playSecondsLeft, state.phase),
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.phase == MojBrojPhase.Player1Input || state.phase == MojBrojPhase.Player2Input) {
                LinearProgressIndicator(
                    progress = { state.playSecondsLeft / 60f },
                    modifier = Modifier.fillMaxWidth(),
                    color = playTimerColor(state.playSecondsLeft, state.phase),
                )
            }

            when (state.phase) {
                MojBrojPhase.RoundIntro -> RoundIntroContent(
                    round = state.currentRound,
                    activeName = activeName,
                    onStart = viewModel::startRound,
                )
                MojBrojPhase.TargetCountdown -> SetupContent(
                    heading = "Generating target number...",
                    subtext = "Tap STOP or shake to reveal!",
                    secondsLeft = state.setupSecondsLeft,
                    onStop = viewModel::onStopPressed,
                )
                MojBrojPhase.NumbersCountdown -> SetupContent(
                    heading = "Target: ${state.targetNumber}",
                    subtext = "Tap STOP or shake to draw your 6 numbers!",
                    secondsLeft = state.setupSecondsLeft,
                    onStop = viewModel::onStopPressed,
                )
                MojBrojPhase.Player1Input -> InputContent(
                    state = state,
                    turnLabel = "$player1Name's turn",
                    onAppendToken = viewModel::appendToken,
                    onDeleteLast = viewModel::deleteLast,
                    onClear = viewModel::clearExpression,
                    onSubmit = viewModel::submitExpression,
                )
                MojBrojPhase.Player2Input -> InputContent(
                    state = state,
                    turnLabel = "$player2Name's turn",
                    onAppendToken = viewModel::appendToken,
                    onDeleteLast = viewModel::deleteLast,
                    onClear = viewModel::clearExpression,
                    onSubmit = viewModel::submitExpression,
                )
                MojBrojPhase.RoundEnd -> RoundEndContent(
                    state = state,
                    player1Name = player1Name,
                    player2Name = player2Name,
                    onNext = viewModel::prepareNextRound,
                )
                MojBrojPhase.GameOver -> GameOverContent(
                    state = state,
                    player1Name = player1Name,
                    player2Name = player2Name,
                    onFinish = onFinish,
                )
            }
        }
    }
}

@Composable
private fun RoundIntroContent(round: Int, activeName: String, onStart: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Round $round of 2",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(text = "$activeName starts the round", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Build an expression with 6 numbers and (, ), +, −, *, /\nto reach the target number.\nMax 10 pts per round.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth(0.6f)) {
                Text("Start Round", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SetupContent(
    heading: String,
    subtext: String,
    secondsLeft: Int,
    onStop: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = heading,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "${secondsLeft}s",
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(0.6f).height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("STOP", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "or shake the phone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InputContent(
    state: MojBrojState,
    turnLabel: String,
    onAppendToken: (ExprToken) -> Unit,
    onDeleteLast: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top: target + expression display
        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(turnLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text("Target: ${state.targetNumber}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                Text(
                    text = if (state.tokens.isEmpty()) "Tap numbers and operators below..."
                           else state.tokens.joinToString(" ") { it.display() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (state.tokens.isEmpty()) FontWeight.Normal else FontWeight.Bold,
                    color = if (state.tokens.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Number chips
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(state.drawnNumbers) { i, num ->
                val used = i in state.usedIndices
                FilterChip(
                    selected = used,
                    enabled = !used,
                    onClick = { onAppendToken(ExprToken.Num(num, i)) },
                    label = {
                        Text(num.toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()

        // Operator + control buttons
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("+", "-", "*", "/").forEach { op ->
                    OutlinedButton(
                        onClick = { onAppendToken(ExprToken.Op(op)) },
                        modifier = Modifier.weight(1f),
                    ) { Text(op, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onAppendToken(ExprToken.OpenParen) },
                    modifier = Modifier.weight(1f),
                ) { Text("(", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = { onAppendToken(ExprToken.CloseParen) },
                    modifier = Modifier.weight(1f),
                ) { Text(")", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = onDeleteLast,
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Delete") }
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
            }
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    text = if (state.tokens.isEmpty()) "Pass (0 pts)" else "Submit",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun RoundEndContent(
    state: MojBrojState,
    player1Name: String,
    player2Name: String,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Round 1 Complete",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        ResultsCard(state = state, player1Name = player1Name, player2Name = player2Name)
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth(0.7f)) {
            Text("Start Round 2", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GameOverContent(
    state: MojBrojState,
    player1Name: String,
    player2Name: String,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Game Over",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        ResultsCard(state = state, player1Name = player1Name, player2Name = player2Name)
        val winner = when {
            state.player1Points > state.player2Points -> "$player1Name wins!"
            state.player2Points > state.player1Points -> "$player2Name wins!"
            else -> "It's a draw!"
        }
        Text(
            text = winner,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth(0.7f)) {
            Text("Back to Game", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultsCard(state: MojBrojState, player1Name: String, player2Name: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Target", style = MaterialTheme.typography.labelMedium)
            Text(
                text = "${state.targetNumber}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider()
            PlayerResultRow(player1Name, state.player1Expression, state.player1Answer)
            PlayerResultRow(player2Name, state.player2Expression, state.player2Answer)
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(player1Name, style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "${state.player1Points} pts",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text("vs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterVertically))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(player2Name, style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "${state.player2Points} pts",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerResultRow(name: String, expression: String, answer: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = "$expression = ${answer ?: "—"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun playTimerColor(secondsLeft: Int, phase: MojBrojPhase): Color = when {
    phase == MojBrojPhase.TargetCountdown || phase == MojBrojPhase.NumbersCountdown ->
        MaterialTheme.colorScheme.primary
    secondsLeft >= 30 -> MaterialTheme.colorScheme.primary
    secondsLeft >= 15 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}
