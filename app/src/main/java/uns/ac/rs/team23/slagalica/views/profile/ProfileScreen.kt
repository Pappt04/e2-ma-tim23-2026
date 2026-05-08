package uns.ac.rs.team23.slagalica.views.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class TableRowData(
    val label: String,
    val value: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    username: String,
    email: String,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
) {
    var avatarVariant by remember { mutableIntStateOf(0) }
    val avatarColors = listOf(Color(0xFF42A5F5), Color(0xFFAB47BC), Color(0xFF66BB6A), Color(0xFFFF7043))
    val selectedAvatarColor = avatarColors[avatarVariant % avatarColors.size]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Profile") },
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
                ElevatedCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(selectedAvatarColor)
                                .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Avatar",
                                tint = Color.White,
                                modifier = Modifier.size(72.dp),
                            )
                        }

                        OutlinedButton(onClick = {
                            avatarVariant = (avatarVariant + 1) % avatarColors.size
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.padding(3.dp))
                            Text("Change Avatar")
                        }

                        Text(username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ProfileInfoRow("Token Count", "340")
                        ProfileInfoRow("Total Stars", "127")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("League", fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFC107),
                                )
                                Spacer(modifier = Modifier.padding(3.dp))
                                Text("Gold League")
                            }
                        }
                        ProfileInfoRow("Region", "Vojvodina")
                    }
                }
            }

            item {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Friend Invite QR Code", style = MaterialTheme.typography.titleMedium)
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "QR",
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = "scan://$username",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                StatsHeaderStrip(title = "Game Statistics")
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
                    subtitle = "Correct and incorrect answers",
                    rows = listOf(
                        TableRowData("Correct", "38"),
                        TableRowData("Incorrect", "19"),
                    ),
                )
            }

            item {
                StatsSectionTitle("Moj broj")
                StatsTableCard(
                    subtitle = "Exact target number hit rate",
                    rows = listOf(
                        TableRowData("Accuracy", "61%"),
                    ),
                )
            }

            item {
                StatsSectionTitle("Korak po korak")
                StatsTableCard(
                    subtitle = "Solved term by reveal step",
                    rows = listOf(
                        TableRowData("Step 1", "20%"),
                        TableRowData("Step 2", "32%"),
                        TableRowData("Step 3", "48%"),
                        TableRowData("Step 4", "67%"),
                        TableRowData("Step 5+", "78%"),
                    ),
                )
            }

            item {
                StatsSectionTitle("Asocijacije")
                StatsTableCard(
                    subtitle = "Solved vs unsolved rounds",
                    rows = listOf(
                        TableRowData("Solved", "15"),
                        TableRowData("Unsolved", "7"),
                    ),
                )
            }

            item {
                StatsSectionTitle("Skočko")
                StatsTableCard(
                    subtitle = "Solved combination by attempt",
                    rows = listOf(
                        TableRowData("Attempt 1", "5%"),
                        TableRowData("Attempt 2", "11%"),
                        TableRowData("Attempt 3", "17%"),
                        TableRowData("Attempt 4", "23%"),
                        TableRowData("Attempt 5", "18%"),
                        TableRowData("Attempt 6", "9%"),
                    ),
                )
            }

            item {
                StatsSectionTitle("Spojnice")
                StatsTableCard(
                    subtitle = "Successfully connected terms",
                    rows = listOf(
                        TableRowData("Connection Accuracy", "74%"),
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
                        TableRowData("Win Rate", "62%"),
                    ),
                )
            }

            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.padding(3.dp))
                    Text("Log Out")
                }
            }
        }
    }
}

@Composable
private fun StatsHeaderStrip(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
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

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold)
        Text(text = value)
    }
}
