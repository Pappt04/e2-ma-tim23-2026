package uns.ac.rs.team23.slagalica.views.region

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.koin.androidx.compose.koinViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import uns.ac.rs.team23.slagalica.models.RegionStanding
import uns.ac.rs.team23.slagalica.models.Regions
import uns.ac.rs.team23.slagalica.viewmodels.RegionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionMapScreen(
    onNavigateBack: () -> Unit,
    viewModel: RegionViewModel = koinViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current

    // osmdroid needs a user-agent set before any tiles are requested.
    remember { Configuration.getInstance().userAgentValue = context.packageName }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(7.0)
            controller.setCenter(GeoPoint(44.0, 20.9))
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onDetach() }
    }

    // Re-draw markers whenever player points change.
    DisposableEffect(ui.playerPoints) {
        mapView.overlays.clear()
        ui.playerPoints.forEach { point ->
            val marker = Marker(mapView)
            marker.position = GeoPoint(point.lat, point.lng)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            val icon = Regions.byId(point.regionId)?.icon ?: ""
            marker.title = "$icon ${point.username}"
            marker.subDescription = point.regionId
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
        onDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Regioni") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Nazad")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Mesečna rang lista regiona",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Broj zvezda osvojenih u tekućem mesečnom ciklusu (resetuje se na kraju ciklusa).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ui.standings.forEachIndexed { index, standing ->
                    RegionRow(
                        position = index + 1,
                        standing = standing,
                        isMyRegion = standing.region.id == ui.myRegion,
                        frameRank = viewModel.frameRankFor(standing.region.id),
                        onClick = { viewModel.selectRegion(standing.region.id) },
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = viewModel::forceEndCycle,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Simuliraj kraj ciklusa (demo)")
                }
            }
        }
    }

    val stats = ui.selectedStats
    if (stats != null) {
        val info = Regions.byId(stats.regionId)
        AlertDialog(
            onDismissRequest = viewModel::clearSelection,
            confirmButton = { TextButton(onClick = viewModel::clearSelection) { Text("Zatvori") } },
            title = { Text("${info?.icon ?: ""} ${info?.displayName ?: stats.regionId}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatLine("Osvojenih prvih mesta", stats.firsts)
                    StatLine("Osvojenih drugih mesta", stats.seconds)
                    StatLine("Osvojenih trećih mesta", stats.thirds)
                    StatLine("Trenutno aktivnih igrača", stats.activePlayers)
                    StatLine("Ukupno registrovanih igrača", stats.totalPlayers)
                }
            },
        )
    }

    ui.info?.let { msg ->
        androidx.compose.runtime.LaunchedEffect(msg) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearInfo()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionRow(
    position: Int,
    standing: RegionStanding,
    isMyRegion: Boolean,
    frameRank: Int,
    onClick: () -> Unit,
) {
    val medal = when (frameRank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> ""
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isMyRegion) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$position.", fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
            Text(standing.region.icon, modifier = Modifier.padding(end = 8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        standing.region.displayName,
                        fontWeight = if (isMyRegion) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (medal.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text(medal)
                    }
                    if (isMyRegion) {
                        Spacer(Modifier.width(6.dp))
                        Text("(vaš region)", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    "${standing.playerCount} igrača",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("⭐ ${standing.totalCycleStars}", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatLine(label: String, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text("$value", fontWeight = FontWeight.SemiBold)
    }
}
