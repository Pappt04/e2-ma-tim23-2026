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
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.models.GameDetailStats
import uns.ac.rs.team23.slagalica.models.GameTypeStatistic
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
                        Button(onClick = viewModel::load) { Text("Try again") }
                    }
                }

                is StatisticsUiState.Success -> {
                    if (state.statistics.hasData) {
                        StatisticsContent(state.statistics)
                    } else {
                        Text(
                            text = "No matches played yet.\nPlay a match to see statistics here.",
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
            StatsChartCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PieChartCard(
                        title = "Win / loss / draw",
                        segments = listOf(
                            ChartSegment("Wins", stats.wins.toFloat(), Color(0xFF43A047)),
                            ChartSegment("Losses", stats.losses.toFloat(), Color(0xFFE53935)),
                            ChartSegment("Draws", stats.draws.toFloat(), Color(0xFF9E9E9E)),
                        ),
                    )
                    Text(
                        text = "${stats.totalMatches} matches played",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    HorizontalDivider()
                    SimpleRows(
                        listOf(
                            TableRowData("Total points", stats.totalPoints.toString()),
                            TableRowData("Avg / match", stats.averagePointsPerMatch.oneDecimal()),
                            TableRowData("Best match", stats.bestMatchScore.toString()),
                        ),
                    )
                }
            }
        }

        detailSections(stats.detail)

        if (stats.perGame.isNotEmpty()) {
            item {
                StatsSectionTitle("Average Points by Game")
                StatsChartCard {
                    HorizontalValueBars(
                        title = "",
                        bars = stats.perGame.mapIndexed { index, game ->
                            ChartSegment(
                                label = game.displayName,
                                value = game.averagePoints.toFloat(),
                                color = gameChartColor(index),
                            )
                        },
                    )
                }
            }

            item {
                StatsSectionTitle("Points by Game")
                GamePointsTable(stats.perGame)
            }
        }
    }
}

@Composable
private fun GamePointsTable(games: List<GameTypeStatistic>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TableHeaderCell("Game", Modifier.weight(1.2f))
                TableHeaderCell("Total", Modifier.weight(0.7f))
                TableHeaderCell("Avg", Modifier.weight(0.7f))
                TableHeaderCell("Best", Modifier.weight(0.7f))
            }
            HorizontalDivider()
            games.forEachIndexed { index, game ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TableBodyCell(game.displayName, Modifier.weight(1.2f), bold = false)
                    TableBodyCell(game.totalPoints.toString(), Modifier.weight(0.7f))
                    TableBodyCell(game.averagePoints.oneDecimal(), Modifier.weight(0.7f))
                    TableBodyCell(game.bestPoints.toString(), Modifier.weight(0.7f))
                }
                if (index != games.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TableHeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun TableBodyCell(text: String, modifier: Modifier = Modifier, bold: Boolean = true) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier,
    )
}

@Composable
private fun SimpleRows(rows: List<TableRowData>) {
    rows.forEachIndexed { index, row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.label, style = MaterialTheme.typography.bodyMedium)
            Text(row.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        if (index != rows.lastIndex) {
            Spacer(modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

private fun Double.oneDecimal(): String = String.format("%.1f", this)

private fun pct(part: Int, total: Int): Int =
    if (total <= 0) 0 else Math.round(part * 100.0 / total).toInt()

private fun gameChartColor(index: Int): Color {
    val palette = listOf(
        Color(0xFF42A5F5),
        Color(0xFFAB47BC),
        Color(0xFF66BB6A),
        Color(0xFFFF7043),
        Color(0xFFFFCA28),
        Color(0xFF26C6DA),
    )
    return palette[index % palette.size]
}

/** Spec-required per-game ratios (req 2.c.ii–vii), shown only for games with recorded data. */
private fun LazyListScope.detailSections(d: GameDetailStats) {
    val koTotal = d.koCorrect + d.koIncorrect
    if (koTotal > 0) item {
        StatsSectionTitle("Who Knows Who")
        StatsChartCard {
            HorizontalPercentBars(
                title = "",
                bars = listOf(
                    ChartSegment("Correct", pct(d.koCorrect, koTotal).toFloat(), Color(0xFF43A047)),
                    ChartSegment("Incorrect", pct(d.koIncorrect, koTotal).toFloat(), Color(0xFFE53935)),
                ),
            )
        }
    }

    if (d.mbRounds > 0) item {
        val miss = d.mbRounds - d.mbFound
        StatsSectionTitle("My Number")
        StatsChartCard {
            HorizontalPercentBars(
                title = "",
                bars = listOf(
                    ChartSegment("Hit", pct(d.mbFound, d.mbRounds).toFloat(), Color(0xFF43A047)),
                    ChartSegment("Miss", pct(miss, d.mbRounds).toFloat(), Color(0xFFE53935)),
                ),
            )
        }
    }

    val korakTotal = d.korakSteps.sum()
    if (korakTotal > 0) item {
        StatsSectionTitle("Step by Step")
        StatsChartCard {
            HorizontalPercentBars(
                title = "",
                bars = d.korakSteps.mapIndexedNotNull { i, c ->
                    if (c == 0) null else ChartSegment("Step ${i + 1}", pct(c, korakTotal).toFloat(), gameChartColor(i))
                },
            )
        }
    }

    val asoTotal = d.asoSolved + d.asoUnsolved
    if (asoTotal > 0) item {
        StatsSectionTitle("Associations")
        StatsChartCard {
            HorizontalPercentBars(
                title = "",
                bars = listOf(
                    ChartSegment("Solved", pct(d.asoSolved, asoTotal).toFloat(), Color(0xFF43A047)),
                    ChartSegment("Unsolved", pct(d.asoUnsolved, asoTotal).toFloat(), Color(0xFFE53935)),
                ),
            )
        }
    }

    val skoTotal = d.skockoAttempts.sum()
    if (skoTotal > 0) item {
        StatsSectionTitle("Skocko")
        StatsChartCard {
            HorizontalPercentBars(
                title = "",
                bars = d.skockoAttempts.mapIndexedNotNull { i, c ->
                    if (c == 0) null else ChartSegment("Attempt ${i + 1}", pct(c, skoTotal).toFloat(), gameChartColor(i))
                },
            )
        }
    }

    if (d.spojniceTotal > 0) item {
        StatsSectionTitle("Connections")
        StatsChartCard {
            HorizontalPercentBars(
                title = "",
                bars = listOf(
                    ChartSegment(
                        "Connected",
                        pct(d.spojniceConnected, d.spojniceTotal).toFloat(),
                        Color(0xFF43A047),
                    ),
                    ChartSegment(
                        "Missed",
                        pct(d.spojniceTotal - d.spojniceConnected, d.spojniceTotal).toFloat(),
                        Color(0xFFE53935),
                    ),
                ),
            )
        }
    }
}

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
private fun StatsChartCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}
