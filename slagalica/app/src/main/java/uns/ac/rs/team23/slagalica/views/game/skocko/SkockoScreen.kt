package uns.ac.rs.team23.slagalica.views.game.skocko

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.R
import uns.ac.rs.team23.slagalica.data.MatchGameOrder
import uns.ac.rs.team23.slagalica.data.MatchStore
import uns.ac.rs.team23.slagalica.viewmodels.*
import uns.ac.rs.team23.slagalica.views.game.common.MatchGameAdvanceEffect
import uns.ac.rs.team23.slagalica.views.game.common.RoundReadyButton
import androidx.compose.runtime.Composable


// Stanja koja jedan kružić za rezultat može da ima
enum class ResultState {
    CORRECT_PLACE, // Crveni krug - Tačan znak na tačnom mestu
    WRONG_PLACE,   // Žuti krug - Tačan znak na pogrešnom mestu
    EMPTY          // Sivi krug - Neispunjeno / Pogrešan znak
}

// Funkcija koja crta JEDAN krug
@Composable
fun ResultCircle(state: ResultState) {
    val circleColor = when (state) {
        ResultState.CORRECT_PLACE -> Color(0xFFD32F2F)   // jasno crvena
        ResultState.WRONG_PLACE   -> Color(0xFFFBC02D)   // jasno žuta
        ResultState.EMPTY         -> Color(0xFFE0E0E0)   // svetlo siva
    }
    val borderColor = when (state) {
        ResultState.CORRECT_PLACE -> Color(0xFF7F0000)
        ResultState.WRONG_PLACE   -> Color(0xFFF57F17)
        ResultState.EMPTY         -> Color(0xFF9E9E9E)
    }

    Box(
        modifier = Modifier
            .size(20.dp)
            .background(color = circleColor, shape = CircleShape)
            .border(2.dp, borderColor, CircleShape)
    )
}

// Funkcija koja crta SVA 4 KRUGA za jedan pokušaj
@Composable
fun ResultRow(redCount: Int, yellowCount: Int) {
    val states = List(4) { index ->
        when {
            index < redCount -> ResultState.CORRECT_PLACE
            index < redCount + yellowCount -> ResultState.WRONG_PLACE
            else -> ResultState.EMPTY
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ResultCircle(state = states[0])
            ResultCircle(state = states[1])
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ResultCircle(state = states[2])
            ResultCircle(state = states[3])
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkockoScreen(
    player1Name: String,
    player2Name: String,
    onFinish: () -> Unit,
    viewModel: SkockoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.enter() }

    MatchGameAdvanceEffect(thisGameIndex = MatchGameOrder.SKOCKO, onLeaveGame = onFinish)

    LaunchedEffect(state.phase) {
        if (state.phase == SkockoPhase.GAME_OVER) {
            delay(3_000)
            onFinish()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Skočko", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Runda ${state.currentRound}/2  ·  " +
                                    "$player1Name: ${state.player1Points}  " +
                                    "$player2Name: ${state.player2Points}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (state.phase == SkockoPhase.PLAYER_TURN ||
                        state.phase == SkockoPhase.OPPONENT_STEAL
                    ) {
                        Text(
                            text = "${state.secondsLeft}s",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (state.secondsLeft <= 10)
                                MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (state.phase) {
                SkockoPhase.ROUND_INTRO -> RoundIntroContent(
                    round = state.currentRound,
                    activeName = if (state.currentRound == 1) player1Name else player2Name,
                    onStart = viewModel::startRound,
                )
                SkockoPhase.PLAYER_TURN, SkockoPhase.OPPONENT_STEAL -> {
                    val isSteal = state.phase == SkockoPhase.OPPONENT_STEAL
                    val activeName = when {
                        isSteal  -> if (state.activePlayerIsP1) player2Name else player1Name
                        state.activePlayerIsP1 -> player1Name
                        else     -> player2Name
                    }
                    if (!isSteal) {
                        LinearProgressIndicator(
                            progress = { state.secondsLeft / 30f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    GameContent(
                        state = state,
                        activeName = activeName,
                        isSteal = isSteal,
                        isMyTurn = viewModel.isMyTurn(),
                        onAddSymbol = viewModel::addSymbol,
                        onRemoveAt  = viewModel::removeSymbolAt,
                        onSubmit    = viewModel::submitAttempt,
                        onConfirmRoundEnd = viewModel::confirmRoundEnd,
                    )
                }
                SkockoPhase.ROUND_END -> RoundEndContent(
                    state = state,
                    player1Name = player1Name,
                    player2Name = player2Name,
                    myReady = if (MatchStore.isHost) state.p1Ready else state.p2Ready,
                    opponentReady = if (MatchStore.isHost) state.p2Ready else state.p1Ready,
                    onReady = viewModel::markReady,
                )
                SkockoPhase.GAME_OVER -> GameOverContent(
                    state = state,
                    player1Name = player1Name,
                    player2Name = player2Name,
                    onFinish = onFinish,
                )
            }
        }
    }
}

// ─── Faze ekrana ─────────────────────────────────────────────────────────────

@Composable
private fun RoundIntroContent(round: Int, activeName: String, onStart: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Runda $round od 2",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "$activeName počinje rundu",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Pogodi kombinaciju 4 simbola u 6 pokušaja.\n" +
                        "Simboli: Pik, Karo, Tref, Srce, Zvezda, Skočko",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth(0.6f)) {
                Text("Počni rundu", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GameContent(
    state: SkockoState,
    activeName: String,
    isSteal: Boolean,
    isMyTurn: Boolean,
    onAddSymbol: (SkockoSymbol) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onSubmit: () -> Unit,
    onConfirmRoundEnd: () -> Unit,
) {
    val listState = rememberLazyListState()
    val mainAttempts = state.attempts.filter { !it.isOpponentAttempt }
    val opponentAttempt = state.attempts.firstOrNull { it.isOpponentAttempt }
    val emptyRows = maxOf(0, 6 - mainAttempts.size - (if (isSteal) 0 else 1))

    Column(modifier = Modifier.fillMaxSize()) {

        // Naslov aktivnog igrača
        Surface(
            color = if (isSteal) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (isSteal) "Krađa: $activeName (1 pokušaj)"
                else "Na potezu: $activeName  " +
                        "(pokušaj ${mainAttempts.size + 1}/6)",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isSteal) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Row(modifier = Modifier.weight(1f).padding(8.dp)) {

            // ── Levo: tabela pokušaja ──────────────────────────────────────
            Column(
                modifier = Modifier.weight(0.72f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Prethodni pokušaji
                    items(mainAttempts) { attempt ->
                        AttemptRow(attempt = attempt, isOpponent = false)
                    }

                    // Trenutni unos (ako nije steal faza)
                    if (!isSteal) {
                        item {
                            CurrentInputRow(
                                input = state.currentInput,
                                onRemoveAt = onRemoveAt,
                                isActive = true,
                                inputLocked = state.awaitingRoundEndConfirm,
                            )
                        }
                    }

                    // Prazni redovi
                    items(emptyRows) {
                        EmptyAttemptRow()
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "Krađa",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                when {
                    isSteal -> CurrentInputRow(
                        input = state.currentInput,
                        onRemoveAt = onRemoveAt,
                        isActive = true,
                        inputLocked = state.awaitingRoundEndConfirm,
                    )
                    opponentAttempt != null -> AttemptRow(attempt = opponentAttempt, isOpponent = true)
                    else -> EmptyAttemptRow()
                }

                Text(
                    text = "Konačno rešenje",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.showSolution) {
                    SolutionRow(solution = state.solution)
                } else {
                    EmptyAttemptRow()
                }
            }

            Spacer(Modifier.width(8.dp))

            // ── Desno: panel sa simbolima ─────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(0.28f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Simboli",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SkockoSymbol.values().forEach { symbol ->
                    SymbolButton(
                        symbol = symbol,
                        enabled = isMyTurn && state.currentInput.any { it == null } && !state.awaitingRoundEndConfirm,
                        onClick = { onAddSymbol(symbol) },
                    )
                }
            }
        }

        // ── Dno: OK dugme ─────────────────────────────────────────────────
        val showOk = state.awaitingRoundEndConfirm || isMyTurn
        if (showOk) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Button(
                    onClick = {
                        if (state.awaitingRoundEndConfirm) onConfirmRoundEnd() else onSubmit()
                    },
                    enabled = if (state.awaitingRoundEndConfirm) true
                    else isMyTurn && state.currentInput.none { it == null },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("OK", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun RoundEndContent(
    state: SkockoState,
    player1Name: String,
    player2Name: String,
    myReady: Boolean,
    opponentReady: Boolean,
    onReady: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Kraj runde ${state.currentRound}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        // Rešenje
        if (state.showSolution) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (state.roundSolved) "✅ Pogođeno!" else "❌ Nije pogođeno",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (state.roundSolved) Color(0xFF388E3C) else MaterialTheme.colorScheme.error,
                    )
                    Text("Tačno rešenje:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.solution.forEach { sym -> SymbolCell(sym) }
                    }
                }
            }
        }

        // Bodovi
        ScoreCard(player1Name, state.player1Points, player2Name, state.player2Points)

        Spacer(Modifier.weight(1f))

        RoundReadyButton(
            myReady = myReady,
            opponentReady = opponentReady,
            onReady = onReady,
            modifier = Modifier.fillMaxWidth(0.7f),
        )
    }
}

@Composable
private fun GameOverContent(
    state: SkockoState,
    player1Name: String,
    player2Name: String,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Kraj igre!",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        ScoreCard(player1Name, state.player1Points, player2Name, state.player2Points)
        val winner = when {
            state.player1Points > state.player2Points -> "$player1Name pobedio!"
            state.player2Points > state.player1Points -> "$player2Name pobedio!"
            else -> "Nerešeno!"
        }
        Text(
            text = winner,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth(0.7f)) {
            Text("Nazad na igru", fontWeight = FontWeight.Bold)
        }
    }
}

// ─── Reusable komponente ──────────────────────────────────────────────────────

@Composable
private fun AttemptRow(attempt: SkockoAttempt, isOpponent: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 4 simbola
        attempt.symbols.forEach { sym -> SymbolCell(sym) }

        Spacer(Modifier.width(6.dp))

        // 4 kružića u redu — crveni=tačna pozicija, žuti=tačan simbol, sivi=promašaj
        ResultRow(
            redCount    = attempt.correctPosition,
            yellowCount = attempt.correctSymbol,
        )

        if (isOpponent) {
            Text(
                text = "K",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

@Composable
private fun CurrentInputRow(
    input: List<SkockoSymbol?>,
    onRemoveAt: (Int) -> Unit,
    isActive: Boolean,
    inputLocked: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(4) { i ->
            val sym = input[i]
            if (sym != null) {
                SymbolCell(
                    symbol = sym,
                    modifier = Modifier.clickable(
                        enabled = isActive && !inputLocked
                    ) { onRemoveAt(i) },
                )
            } else {
                // Prazno polje
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(8.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun EmptyAttemptRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(8.dp),
                    ),
            )
        }
    }
}

@Composable
private fun SymbolCell(
    symbol: SkockoSymbol,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        SymbolContent(symbol = symbol, compact = false)
    }
}

@Composable
private fun SymbolButton(
    symbol: SkockoSymbol,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = Color(symbol.hexColor),
            disabledContentColor = Color(symbol.hexColor).copy(alpha = 0.35f),
        ),
        contentPadding = PaddingValues(4.dp),
        shape = RoundedCornerShape(10.dp),
    ) {
        SymbolContent(symbol = symbol, compact = true)
    }
}

@Composable
private fun SymbolContent(symbol: SkockoSymbol, compact: Boolean) {
    if (symbol == SkockoSymbol.SKOCKO) {
        Box(
            modifier = Modifier
                .size(if (compact) 20.dp else 28.dp)
                .background(Color(0xFF4CAF50), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Skočko",
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        Text(
            text = symbol.label,
            color = Color(symbol.hexColor),
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 15.sp else 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SolutionRow(solution: List<SkockoSymbol>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        solution.forEach { symbol ->
            SymbolCell(symbol)
        }
    }
}

@Composable
private fun FeedbackDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun ScoreCard(p1Name: String, p1Pts: Int, p2Name: String, p2Pts: Int) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
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