package uns.ac.rs.team23.slagalica.views.challenge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeScreen(
    region: String,
    onNavigateBack: () -> Unit,
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
                    items(challenges, key = { it.id }) { c ->
                        ChallengeCard(
                            challenge = c,
                            onJoin = { viewModel.joinChallenge(c.id) {} },
                        )
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
private fun ChallengeCard(challenge: ChallengeResponseDto, onJoin: () -> Unit) {
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
                if (challenge.status == "OPEN" && challenge.participants.size < 4) {
                    Button(onClick = onJoin) { Text("Join") }
                } else {
                    AssistChip(
                        onClick = {},
                        label = { Text(challenge.status) },
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
