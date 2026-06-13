package uns.ac.rs.team23.slagalica.views.game.asocijacije

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.viewmodels.*


// ─── Glavni Screen ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsocijacijeScreen(
    player1Name: String,
    player2Name: String,
    onFinish: () -> Unit,
    viewModel: AsocijacijeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.enter() }

    // Both clients reach GAME_OVER together (host-driven); auto-advance to the next game.
    LaunchedEffect(state.phase) {
        if (state.phase == AsocijacijePhase.GAME_OVER) {
            kotlinx.coroutines.delay(4_000)
            onFinish()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Asocijacije", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Runda ${state.currentRound}/2  ·  " +
                                    "$player1Name: ${state.player1Points}  " +
                                    "$player2Name: ${state.player2Points}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onFinish) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Nazad")
                    }
                },
                actions = {
                    if (state.phase == AsocijacijePhase.PLAYING) {
                        val timerColor = if (state.secondsLeft <= 15) MaterialTheme.colorScheme.primary
                        else Color.White
                        Text(
                            text = "${state.secondsLeft}s",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = timerColor,
                            modifier = Modifier.padding(end = 16.dp),
                        )
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
            if (state.phase == AsocijacijePhase.PLAYING) {
                LinearProgressIndicator(
                    progress = { state.secondsLeft / 120f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            when (state.phase) {
                AsocijacijePhase.ROUND_INTRO, AsocijacijePhase.LOADING -> RoundIntroContent(
                    round = state.currentRound,
                    activeName = if (state.currentRound == 1) player1Name else player2Name,
                    onStart = viewModel::startRound,
                )
                AsocijacijePhase.PLAYING -> PlayingContent(
                    state = state,
                    player1Name = player1Name,
                    player2Name = player2Name,
                    onReveal = viewModel::revealField,
                    onSelectTarget = viewModel::selectGuessTarget,
                    onGuessChange = viewModel::onGuessChange,
                    onSubmit = viewModel::submitGuess,
                    onPass = viewModel::passGuess,
                )
                AsocijacijePhase.ROUND_END -> RoundEndContent(
                    state = state,
                    player1Name = player1Name,
                    player2Name = player2Name,
                    onNextRound = viewModel::nextRound,
                )
                AsocijacijePhase.GAME_OVER -> GameOverContent(
                    state = state,
                    player1Name = player1Name,
                    player2Name = player2Name,
                    onFinish = onFinish,
                )
            }
        }
    }
}

// ─── Intro runde ─────────────────────────────────────────────────────────────

@Composable
private fun RoundIntroContent(
    round: Int,
    activeName: String,
    onStart: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Runda $round",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Asocijacije",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                HorizontalDivider()
                Text(
                    text = "$activeName počinje rundu",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Maks. 30 bodova · 2 minuta",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Počni rundu $round")
                }
            }
        }
    }
}

// ─── Glavni sadržaj igre ──────────────────────────────────────────────────────

@Composable
private fun PlayingContent(
    state: AsocijacijeState,
    player1Name: String,
    player2Name: String,
    onReveal: (col: Int, row: Int) -> Unit,
    onSelectTarget: (GuessTarget) -> Unit,
    onGuessChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onPass: () -> Unit,
) {
    val activeName = if (state.activePlayer == 1) player1Name else player2Name
    val hasRevealableFields = state.columns.any { column ->
        !column.isSolved && column.revealedFields.any { revealed -> !revealed }
    }
    val canGuessNow = state.waitingForGuess || !hasRevealableFields
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.selectedGuessTarget) {
        if (state.selectedGuessTarget != null) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // Indikator aktivnog igrača
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(vertical = 6.dp, horizontal = 16.dp),
        ) {
            Text(
                text = if (canGuessNow)
                    "$activeName pogađa..."
                else
                    "$activeName otkriva polje",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        // Grid asocijacija
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (row in 0..3) {
                PairWordRow(state = state, leftCol = 0, rightCol = 1, row = row, onReveal = onReveal)
            }
            PairAnswerRow(state = state, leftCol = 0, rightCol = 1, onSelectTarget = onSelectTarget)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val finalSelected = state.selectedGuessTarget == GuessTarget.Final
                val finalWrong = state.wrongGuessTarget == GuessTarget.Final
                val finalClickable = canGuessNow && !state.isFinalSolved
                AssocCell(
                    text = when {
                        state.isFinalSolved -> state.finalAnswer
                        finalSelected -> state.guessInput.ifBlank { "RESENJE" }
                        else -> "RESENJE"
                    },
                    backgroundColor = when {
                        finalWrong -> MaterialTheme.colorScheme.error
                        state.isFinalSolved -> MaterialTheme.colorScheme.tertiary
                        finalSelected -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    },
                    textColor = when {
                        finalWrong -> MaterialTheme.colorScheme.onError
                        state.isFinalSolved -> MaterialTheme.colorScheme.onTertiary
                        finalSelected -> MaterialTheme.colorScheme.onSecondary
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    clickable = finalClickable,
                    onClick = { onSelectTarget(GuessTarget.Final) },
                )
                Button(
                    onClick = onPass,
                    enabled = canGuessNow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.height(56.dp),
                ) { Text("Dalje") }
            }

            PairAnswerRow(state = state, leftCol = 2, rightCol = 3, onSelectTarget = onSelectTarget)
            for (row in 3 downTo 0) {
                PairWordRow(state = state, leftCol = 2, rightCol = 3, row = row, onReveal = onReveal)
            }
        }
        OutlinedTextField(
            value = state.guessInput,
            onValueChange = onGuessChange,
            enabled = state.selectedGuessTarget != null && canGuessNow,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier
                .size(1.dp)
                .focusRequester(focusRequester),
        )
    }
}

@Composable
private fun PairWordRow(
    state: AsocijacijeState,
    leftCol: Int,
    rightCol: Int,
    row: Int,
    onReveal: (col: Int, row: Int) -> Unit,
) {
    val canReveal = state.columns.any { column ->
        !column.isSolved && column.revealedFields.any { revealed -> !revealed }
    } && !state.waitingForGuess
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(leftCol, rightCol).forEach { col ->
            val column = state.columns[col]
            val isRevealed = column.revealedFields[row]
            val canClick = !isRevealed && !column.isSolved && canReveal
            val fieldLabel = "${('A'.code + col).toChar()}${row + 1}"
            AssocCell(
                text = if (isRevealed || column.isSolved) column.words[row] else fieldLabel,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                clickable = canClick,
                onClick = { onReveal(col, row) },
            )
        }
    }
}

@Composable
private fun PairAnswerRow(
    state: AsocijacijeState,
    leftCol: Int,
    rightCol: Int,
    onSelectTarget: (GuessTarget) -> Unit,
) {
    val hasRevealableFields = state.columns.any { column ->
        !column.isSolved && column.revealedFields.any { revealed -> !revealed }
    }
    val canGuessNow = state.waitingForGuess || !hasRevealableFields
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(leftCol, rightCol).forEach { col ->
            val column = state.columns[col]
            val selected = state.selectedGuessTarget == GuessTarget.Column(col)
            val wrong = state.wrongGuessTarget == GuessTarget.Column(col)
            val clickable = canGuessNow && !column.isSolved
            val answerLabel = ('A'.code + col).toChar().toString()
            AssocCell(
                text = when {
                    column.isSolved -> column.answer
                    selected -> state.guessInput.ifBlank { answerLabel }
                    else -> answerLabel
                },
                backgroundColor = when {
                    wrong -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                textColor = when {
                    wrong -> MaterialTheme.colorScheme.onError
                    else -> MaterialTheme.colorScheme.onPrimary
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                fontWeight = FontWeight.Bold,
                clickable = clickable,
                onClick = { onSelectTarget(GuessTarget.Column(col)) },
            )
        }
    }
}

// ─── Ćelija mreže ─────────────────────────────────────────────────────────────

@Composable
private fun AssocCell(
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    fontWeight: FontWeight = FontWeight.SemiBold,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    clickable: Boolean = false,
    onClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .then(
                if (clickable) Modifier
                    .border(2.dp, textColor.copy(alpha = 0.4f), shape)
                    .clickable(onClick = onClick)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = fontWeight,
            fontSize = fontSize,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(4.dp),
        )
    }
}

// ─── Kraj runde ────────────────────────────────────────────────────────────────

@Composable
private fun RoundEndContent(
    state: AsocijacijeState,
    player1Name: String,
    player2Name: String,
    onNextRound: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Runda 1 završena!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                HorizontalDivider()
                Text(
                    text = "Finalno rešenje: ${state.finalAnswer}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ScoreColumn(player1Name, state.player1Points)
                    ScoreColumn(player2Name, state.player2Points)
                }
                Button(
                    onClick = onNextRound,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Počni rundu 2")
                }
            }
        }
    }
}

// ─── Kraj igre ─────────────────────────────────────────────────────────────────

@Composable
private fun GameOverContent(
    state: AsocijacijeState,
    player1Name: String,
    player2Name: String,
    onFinish: () -> Unit,
) {
    val winner = when {
        state.player1Points > state.player2Points -> player1Name
        state.player2Points > state.player1Points -> player2Name
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Kraj igre!",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (winner != null) "🏆 Pobednik: $winner" else "🤝 Nerešeno!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ScoreColumn(player1Name, state.player1Points)
                    ScoreColumn(player2Name, state.player2Points)
                }
                Button(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Nazad na igre")
                }
            }
        }
    }
}

// ─── Pomoćni composable za skor ───────────────────────────────────────────────

@Composable
private fun ScoreColumn(name: String, points: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = MaterialTheme.typography.labelMedium)
        Text(
            text = "$points",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text("bodova", style = MaterialTheme.typography.labelSmall)
    }
}