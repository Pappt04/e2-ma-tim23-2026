package uns.ac.rs.team23.slagalica.views.game.korakpokorak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import uns.ac.rs.team23.slagalica.viewmodels.KorakPoKorakState

@Composable
fun ActiveTurnContent(
    state: KorakPoKorakState,
    turnLabel: String,
    submitLabel: String,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .imePadding(),
    ) {
        // Scrollable clue area
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Phase header
            Text(
                text = turnLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )

            val maxPoints = 20 - 2 * (state.currentStep - 1)
            Text(
                text = "Step ${state.currentStep}/7  ·  Correct answer = $maxPoints pts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Clue cards
            state.revealedClues.forEachIndexed { index, clue ->
                val isLatest = index == state.revealedClues.size - 1
                ClueCard(
                    stepNumber = index + 1,
                    clue = clue,
                    highlight = isLatest,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        HorizontalDivider()

        // Answer section (pinned at bottom)
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.showWrongFeedback) {
                Text(
                    text = "Wrong answer, try again!",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = state.currentAnswer,
                onValueChange = onAnswerChange,
                label = { Text("Your answer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                isError = state.showWrongFeedback,
            )
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.currentAnswer.isNotBlank(),
            ) {
                Text(submitLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

