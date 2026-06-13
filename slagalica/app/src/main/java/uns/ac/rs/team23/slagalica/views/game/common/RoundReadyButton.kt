package uns.ac.rs.team23.slagalica.views.game.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun RoundReadyButton(
    myReady: Boolean,
    opponentReady: Boolean,
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onReady,
        enabled = !myReady,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            when {
                myReady && opponentReady -> "Starting…"
                myReady -> "Waiting for opponent…"
                else -> "Ready"
            },
        )
    }
}
