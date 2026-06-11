package uns.ac.rs.team23.slagalica.views.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class TableRowData(
    val label: String,
    val value: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileStatisticsScreen(
    onNavigateBack: () -> Unit,
) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Test data for the checkpoint.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                StatsSectionTitle("Average Points")
                StatsTableCard(
                    subtitle = "Average points by game",
                    rows = listOf(
                        TableRowData("Ko zna zna", "18 / 50"),
                        TableRowData("Moj broj", "16 / 30"),
                        TableRowData("Korak po korak", "22 / 30"),
                        TableRowData("Asocijacije", "24 / 30"),
                        TableRowData("Skočko", "14 / 20"),
                        TableRowData("Spojnice", "12 / 20"),
                    ),
                )
            }

            item {
                StatsSectionTitle("Ko zna zna")
                StatsTableCard(
                    subtitle = "Share of answers ",
                    rows = listOf(
                        TableRowData("Correct", "67%"),
                        TableRowData("Incorrect", "33%"),
                    ),
                )
            }

            item {
                StatsSectionTitle("Moj broj")
                StatsTableCard(
                    subtitle = "Exact target number",
                    rows = listOf(
                        TableRowData("Hit", "61%"),
                        TableRowData("Miss", "39%"),
                    ),
                )
            }

            item {
                StatsSectionTitle("Korak po korak")
                StatsTableCard(
                    subtitle = "Solves by reveal step ",
                    rows = listOf(
                        TableRowData("Step 1", "10%"),
                        TableRowData("Step 2", "15%"),
                        TableRowData("Step 3", "20%"),
                        TableRowData("Step 4", "25%"),
                        TableRowData("Step 5+", "30%"),
                    ),
                )
            }

            item {
                StatsSectionTitle("Asocijacije")
                StatsTableCard(
                    subtitle = "Rounds outcome ",
                    rows = listOf(
                        TableRowData("Solved", "68%"),
                        TableRowData("Unsolved", "32%"),
                    ),
                )
            }

            item {
                StatsSectionTitle("Skočko")
                StatsTableCard(
                    subtitle = "Solved on attempt ",
                    rows = listOf(
                        TableRowData("Attempt 1", "8%"),
                        TableRowData("Attempt 2", "12%"),
                        TableRowData("Attempt 3", "18%"),
                        TableRowData("Attempt 4", "22%"),
                        TableRowData("Attempt 5", "22%"),
                        TableRowData("Attempt 6", "18%"),
                    ),
                )
            }

            item {
                StatsSectionTitle("Spojnice")
                StatsTableCard(
                    subtitle = "Connections",
                    rows = listOf(
                        TableRowData("Successful", "74%"),
                        TableRowData("Not successful", "26%"),
                    ),
                )
            }

            item {
                StatsSectionTitle("Match Summary")
                StatsTableCard(
                    subtitle = "Overall match performance",
                    rows = listOf(
                        TableRowData("Total Matches", "42"),
                        TableRowData("Wins", "26"),
                        TableRowData("Losses", "16"),
                        TableRowData("Win %", "62%"),
                        TableRowData("Loss %", "38%"),
                    ),
                )
            }
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
