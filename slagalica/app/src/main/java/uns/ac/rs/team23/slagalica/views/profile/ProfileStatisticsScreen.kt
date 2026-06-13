package uns.ac.rs.team23.slagalica.views.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.models.PlayerStatistics
import uns.ac.rs.team23.slagalica.viewmodels.StatisticsUiState
import uns.ac.rs.team23.slagalica.viewmodels.StatisticsViewModel

private data class TableRowData(
    val label: String,
    val value: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileStatisticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatisticsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game Statistics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val state = uiState) {
                is StatisticsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is StatisticsUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = viewModel::load) { Text("Pokušaj ponovo") }
                    }
                }

                is StatisticsUiState.Success -> {
                    if (state.statistics.hasData) {
                        StatisticsContent(state.statistics)
                    } else {
                        Text(
                            text = "Još nema odigranih mečeva.\nOdigraj meč da bi se ovde pojavila statistika.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsContent(stats: PlayerStatistics) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StatsSectionTitle("Match Summary")
            StatsTableCard(
                subtitle = "Overall match performance",
                rows = listOf(
                    TableRowData("Total Matches", stats.totalMatches.toString()),
                    TableRowData("Wins", stats.wins.toString()),
                    TableRowData("Losses", stats.losses.toString()),
                    TableRowData("Draws", stats.draws.toString()),
                    TableRowData("Win %", "${stats.winRate.roundedPercent()}%"),
                    TableRowData("Loss %", "${stats.lossRate.roundedPercent()}%"),
                ),
            )
        }

        item {
            StatsSectionTitle("Points")
            StatsTableCard(
                subtitle = "Points across all matches",
                rows = listOf(
                    TableRowData("Total Points", stats.totalPoints.toString()),
                    TableRowData("Avg / Match", stats.averagePointsPerMatch.oneDecimal()),
                    TableRowData("Best Match", stats.bestMatchScore.toString()),
                ),
            )
        }

        if (stats.perGame.isNotEmpty()) {
            item {
                StatsSectionTitle("Average Points by Game")
                StatsTableCard(
                    subtitle = "Average points per game type",
                    rows = stats.perGame.map { game ->
                        TableRowData(game.displayName, game.averagePoints.oneDecimal())
                    },
                )
            }

            items(stats.perGame.size) { index ->
                val game = stats.perGame[index]
                StatsSectionTitle(game.displayName)
                StatsTableCard(
                    subtitle = "Per-game performance",
                    rows = listOf(
                        TableRowData("Games Played", game.gamesPlayed.toString()),
                        TableRowData("Total Points", game.totalPoints.toString()),
                        TableRowData("Average", game.averagePoints.oneDecimal()),
                        TableRowData("Best", game.bestPoints.toString()),
                    ),
                )
            }
        }
    }
}

private fun Double.roundedPercent(): Int = Math.round(this).toInt()

private fun Double.oneDecimal(): String = String.format("%.1f", this)

@Composable
private fun StatsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun StatsTableCard(
    subtitle: String,
    rows: List<TableRowData>,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.padding(6.dp))
                    Text(
                        text = row.value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                    )
                }
                if (index != rows.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}
