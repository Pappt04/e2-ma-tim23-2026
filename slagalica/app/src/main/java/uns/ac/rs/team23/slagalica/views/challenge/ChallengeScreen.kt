package uns.ac.rs.team23.slagalica.views.challenge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.network.dto.ChallengeResponseDto
import uns.ac.rs.team23.slagalica.viewmodels.ChallengeViewModel

private enum class ChallengeAction { Join, Play, Waiting, Full }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeScreen(
    region: String,
    username: String,
    onNavigateBack: () -> Unit,
    onPlayAttempt: () -> Unit,
    viewModel: ChallengeViewModel = koinViewModel(),
) {
    LaunchedEffect(region) { viewModel.init(region) }

    val challenges by viewModel.challenges.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    error?.let {
        LaunchedEffect(it) { viewModel.clearError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Challenges", style = MaterialTheme.typography.titleMedium)
                        Text(region, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, "New Challenge")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading && challenges.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (challenges.isEmpty()) {
                Text(
                    "No open challenges.\nTap + to create one!",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val uid = viewModel.myUid
                    items(challenges, key = { it.id }) { c ->
                        if (c.status == "COMPLETED") {
                            CompletedChallengeCard(challenge = c)
                        } else {
                            val me = c.participants.find { it.id == uid }
                            val action = when {
                                me == null && c.participants.size < 4 -> ChallengeAction.Join
                                me != null && me.gamesCompleted < 6 -> ChallengeAction.Play
                                me != null -> ChallengeAction.Waiting
                                else -> ChallengeAction.Full
                            }
                            ChallengeCard(
                                challenge = c,
                                action = action,
                                onJoin = { viewModel.joinChallenge(c.id) {} },
                                onPlay = { viewModel.startAttempt(c, username) { onPlayAttempt() } },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateChallengeDialog(
            stakedStars = viewModel.stakedStars,
            stakedTokens = viewModel.stakedTokens,
            onStarsChange = viewModel::onStakedStarsChange,
            onTokensChange = viewModel::onStakedTokensChange,
            onConfirm = {
                viewModel.createChallenge {}
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

@Composable
private fun CompletedChallengeCard(challenge: ChallengeResponseDto) {
    val sorted = challenge.participants.sortedByDescending { it.totalScore }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "by ${challenge.creatorUsername}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                AssistChip(onClick = {}, label = { Text("COMPLETED") })
            }
            Text(
                text = "⭐ ${challenge.stakedStars} stars · 🎟 ${challenge.stakedTokens} tokens",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            sorted.forEachIndexed { index, p ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (index == 0) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = "Winner",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        } else {
                            Spacer(Modifier.width(22.dp))
                        }
                        Text(
                            text = "${index + 1}. ${p.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${p.totalScore} pts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (index == 1) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Gets stake back",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeCard(
    challenge: ChallengeResponseDto,
    action: ChallengeAction,
    onJoin: () -> Unit,
    onPlay: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "by ${challenge.creatorUsername}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "⭐ ${challenge.stakedStars} stars · 🎟 ${challenge.stakedTokens} tokens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${challenge.participants.size}/4 players",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                when (action) {
                    ChallengeAction.Join -> Button(onClick = onJoin) { Text("Join") }
                    ChallengeAction.Play -> Button(onClick = onPlay) { Text("Play") }
                    ChallengeAction.Waiting -> AssistChip(
                        onClick = {},
                        label = { Text("Waiting…") },
                    )
                    ChallengeAction.Full -> AssistChip(
                        onClick = {},
                        label = { Text("Full") },
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateChallengeDialog(
    stakedStars: Int,
    stakedTokens: Int,
    onStarsChange: (Int) -> Unit,
    onTokensChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Challenge") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Stake stars and tokens. The winner gets 75% of all stakes.")

                Text("Stars to stake: $stakedStars (max 10)",
                    style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = stakedStars.toFloat(),
                    onValueChange = { onStarsChange(it.toInt()) },
                    valueRange = 0f..10f,
                    steps = 9,
                )

                Text("Tokens to stake: $stakedTokens (max 2)",
                    style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = stakedTokens.toFloat(),
                    onValueChange = { onTokensChange(it.toInt()) },
                    valueRange = 0f..2f,
                    steps = 1,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
