package uns.ac.rs.team23.slagalica.views.profile

import androidx.compose.foundation.Image
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
import uns.ac.rs.team23.slagalica.models.LEAGUE_NAMES
import uns.ac.rs.team23.slagalica.utils.QrGenerator
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
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    username: String,
    email: String,
    tokens: Int = 0,
    stars: Int = 0,
    leagueLevel: Int = 0,
    region: String = "",
    onNavigateBack: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToChangePassword: () -> Unit = {},
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
                        ProfileInfoRow("Token Count", tokens.toString())
                        ProfileInfoRow("Total Stars", stars.toString())
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
                                Text(LEAGUE_NAMES.getOrElse(leagueLevel) { "League $leagueLevel" })
                            }
                        }
                        ProfileInfoRow("Region", region)
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
                        val inviteCode = "slagalica-friend:$username"
                        val qrBitmap = remember(username) { QrGenerator.generate(inviteCode) }
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap,
                                    contentDescription = "Friend invite QR code",
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(
                                    text = "QR",
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                )
                            }
                        }
                        Text(
                            text = inviteCode,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onNavigateToStatistics,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Statistika")
                }
            }

            item {
                OutlinedButton(
                    onClick = onNavigateToChangePassword,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Promeni lozinku")
                }
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
                    Text("Odjava")
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
