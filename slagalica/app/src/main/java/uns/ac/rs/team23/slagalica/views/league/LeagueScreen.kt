package uns.ac.rs.team23.slagalica.views.league

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uns.ac.rs.team23.slagalica.models.LEAGUE_NAMES
import uns.ac.rs.team23.slagalica.models.LEAGUE_STAR_THRESHOLDS
import uns.ac.rs.team23.slagalica.models.LEAGUE_ICONS
import uns.ac.rs.team23.slagalica.models.dailyTokensForLeague

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeagueScreen(
    leagueLevel: Int,
    stars: Int,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leagues") },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                CurrentLeagueHeader(leagueLevel = leagueLevel, stars = stars)
            }
            item {
                Text(
                    "All leagues",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            itemsIndexed(LEAGUE_NAMES) { level, name ->
                LeagueRow(
                    level = level,
                    name = name,
                    threshold = LEAGUE_STAR_THRESHOLDS.getOrElse(level) { 0 },
                    dailyTokens = dailyTokensForLeague(level),
                    isCurrent = level == leagueLevel,
                )
            }
        }
    }
}

@Composable
private fun CurrentLeagueHeader(leagueLevel: Int, stars: Int) {
    val currentThreshold = LEAGUE_STAR_THRESHOLDS.getOrElse(leagueLevel) { 0 }
    val nextThreshold = LEAGUE_STAR_THRESHOLDS.getOrNull(leagueLevel + 1)
    val progress = if (nextThreshold == null) {
        1f
    } else {
        ((stars - currentThreshold).toFloat() / (nextThreshold - currentThreshold)).coerceIn(0f, 1f)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                LEAGUE_ICONS.getOrElse(leagueLevel) { "🏅" },
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                LEAGUE_NAMES.getOrElse(leagueLevel) { "League $leagueLevel" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "⭐ $stars stars",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )
            Text(
                text = if (nextThreshold == null) {
                    "Highest league reached!"
                } else {
                    "${nextThreshold - stars} more stars until the next league"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun LeagueRow(
    level: Int,
    name: String,
    threshold: Int,
    dailyTokens: Int,
    isCurrent: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isCurrent) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(LEAGUE_ICONS.getOrElse(level) { "🏅" }, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                )
                Text(
                    "Required: ⭐ $threshold",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Benefit: +$dailyTokens tokens daily",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isCurrent) {
                Text(
                    "CURRENT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
