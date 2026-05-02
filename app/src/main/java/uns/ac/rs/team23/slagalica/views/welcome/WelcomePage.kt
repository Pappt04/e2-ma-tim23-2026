package uns.ac.rs.team23.slagalica.views.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.viewmodels.AuthViewModel

@Composable
fun WelcomePage(viewModel: AuthViewModel = koinViewModel()) {
    var showRegister by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "SLAGALICA",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (showRegister) {
                Column {
                    RegisterComponent(
                        viewModel = viewModel,
                        onNavigateToLogin = { showRegister = false },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showRegister = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Have an account already? Log in")
                    }
                }
            } else {
                Column {
                    LoginComponent(
                        viewModel = viewModel,
                        onNavigateToRegister = { showRegister = true },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showRegister = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Don't have an account? Register here")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text("or", style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::loginAsGuest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Continue as Guest")
                    }
                }
            }
        }
    }
}
