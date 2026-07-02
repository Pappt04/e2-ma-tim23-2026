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
    val resendState by viewModel.resendState.collectAsState()
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
                text = "Log in",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = viewModel.loginEmailOrUsername,
                onValueChange = viewModel::onLoginEmailOrUsernameChange,
                label = { Text("Email or username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            OutlinedTextField(
                value = viewModel.loginPassword,
                onValueChange = viewModel::onLoginPasswordChange,
                label = { Text("Password") },
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
                        Text(if (passwordVisible) "Hide" else "Show")
                    }
                },
            )

            if (loginState is AuthState.Error) {
                Text(
                    text = (loginState as AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                if ((loginState as AuthState.Error).message.contains("not verified", ignoreCase = true)) {
                    TextButton(
                        onClick = viewModel::resendVerificationEmail,
                        enabled = resendState !is AuthState.Loading,
                    ) {
                        Text(
                            if (resendState is AuthState.Loading) "Sending..." else "Resend verification email",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            when (val state = resendState) {
                is AuthState.Success -> Text(
                    "Verification email sent — check your inbox.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
                is AuthState.Error -> Text(
                    state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                else -> {}
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = viewModel::login,
                modifier = Modifier.fillMaxWidth(),
                enabled = loginState !is AuthState.Loading,
            ) {
                Text(if (loginState is AuthState.Loading) "Logging in..." else "Log in")
            }

            TextButton(
                onClick = onNavigateToForgotPassword,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Forgot your password?",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
