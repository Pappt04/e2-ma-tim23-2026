package uns.ac.rs.team23.slagalica.views.game.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.models.LEAGUE_NAMES
import uns.ac.rs.team23.slagalica.models.leagueIconFor
import uns.ac.rs.team23.slagalica.viewmodels.AuthViewModel
import uns.ac.rs.team23.slagalica.viewmodels.UserSession

/** Spec: tokens, stars and league visible at all times, including during a match. */
@Composable
fun MatchPlayerHud(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = koinViewModel(),
) {
    val session by authViewModel.userSession.collectAsState()
    val profile by authViewModel.userProfile.collectAsState()
    if (session !is UserSession.LoggedIn) return

    val t = profile?.tokens ?: 0
    val s = profile?.stars ?: 0
    val league = profile?.leagueLevel ?: 0
    val leagueLabel = LEAGUE_NAMES.getOrElse(league) { "Liga $league" }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudItem("🎟", "Tokeni", t.toString())
            HudItem("⭐", "Zvezde", s.toString())
            HudItem(leagueIconFor(league), leagueLabel, "")
        }
    }
}

@Composable
private fun HudItem(icon: String, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(icon, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        if (value.isNotEmpty()) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** Block leaving a game screen via system back — only forfeit ends the match. */
@Composable
fun BlockGameBackNavigation() {
    BackHandler(enabled = true) { /* blocked during active game */ }
}
