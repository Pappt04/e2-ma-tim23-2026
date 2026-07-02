package uns.ac.rs.team23.slagalica.views.leaderboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.models.LeaderboardEntry
import uns.ac.rs.team23.slagalica.models.leagueIconFor
import uns.ac.rs.team23.slagalica.viewmodels.LeaderboardPeriod
import uns.ac.rs.team23.slagalica.viewmodels.LeaderboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onNavigateBack: () -> Unit,
    viewModel: LeaderboardViewModel = koinViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    var showNewCycleConfirm by remember { mutableStateOf(false) }

    if (showNewCycleConfirm) {
        AlertDialog(
            onDismissRequest = { showNewCycleConfirm = false },
            title = { Text("End current cycle now?") },
            text = {
                Text(
                    "This distributes rewards, resets cycle stars, and recalculates leagues " +
                        "immediately — for demo purposes only.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNewCycleConfirm = false
                    viewModel.triggerNewCycle()
                }) {
                    Text("Start new cycle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCycleConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leaderboard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showNewCycleConfirm = true },
                        enabled = !ui.triggering,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Start new cycle (demo)")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = ui.period.ordinal) {
                Tab(
                    selected = ui.period == LeaderboardPeriod.WEEKLY,
                    onClick = { viewModel.setPeriod(LeaderboardPeriod.WEEKLY) },
                    text = { Text("Weekly") },
                )
                Tab(
                    selected = ui.period == LeaderboardPeriod.MONTHLY,
                    onClick = { viewModel.setPeriod(LeaderboardPeriod.MONTHLY) },
                    text = { Text("Monthly") },
                )
            }

            if (ui.dateRange.isNotBlank()) {
                Text(
                    text = ui.dateRange,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            when {
                ui.loading && ui.entries.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                ui.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(ui.error ?: "", color = MaterialTheme.colorScheme.error)
                    }
                }
                ui.entries.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No one has played a match in this cycle yet.",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(ui.entries, key = { "${ui.period}_${it.rank}_${it.username}" }) { entry ->
                            LeaderboardRow(entry = entry, leagueName = viewModel.leagueName(entry.leagueLevel))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry, leagueName: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "#${entry.rank}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(36.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.username, fontWeight = FontWeight.SemiBold)
                Text(
                    "${leagueIconFor(entry.leagueLevel)} $leagueName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${entry.cycleStars}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
