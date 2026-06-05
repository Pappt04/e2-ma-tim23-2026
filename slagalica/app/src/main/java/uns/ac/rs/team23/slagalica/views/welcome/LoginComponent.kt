package uns.ac.rs.team23.slagalica.views.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uns.ac.rs.team23.slagalica.viewmodels.AuthState
import uns.ac.rs.team23.slagalica.viewmodels.AuthViewModel

@Composable
fun LoginComponent(
    viewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit = {},
) {
    val loginState by viewModel.loginState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(loginState) {
        if (loginState is AuthState.Success) {
            viewModel.clearLoginState()
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Prijava",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = viewModel.loginEmailOrUsername,
                onValueChange = viewModel::onLoginEmailOrUsernameChange,
                label = { Text("Email ili korisničko ime") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            OutlinedTextField(
                value = viewModel.loginPassword,
                onValueChange = viewModel::onLoginPasswordChange,
                label = { Text("Lozinka") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation =
                    if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(if (passwordVisible) "Sakrij" else "Prikaži")
                    }
                },
            )

            if (loginState is AuthState.Error) {
                Text(
                    text = (loginState as AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = viewModel::login,
                modifier = Modifier.fillMaxWidth(),
                enabled = loginState !is AuthState.Loading,
            ) {
                Text(if (loginState is AuthState.Loading) "Prijavljivanje..." else "Prijavi se")
            }

            TextButton(
                onClick = onNavigateToForgotPassword,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Zaboravili ste lozinku?",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
