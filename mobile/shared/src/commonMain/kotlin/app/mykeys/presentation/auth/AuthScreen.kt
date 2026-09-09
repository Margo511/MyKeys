package app.mykeys.presentation.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

@Composable
fun AuthScreen(
    state: AuthUiState,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { AuthHeader() }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (state.screen) {
                        AuthScreen.RESTORING -> RestoringContent()
                        AuthScreen.LOGIN -> LoginContent(state, onEvent)
                        AuthScreen.REGISTER -> RegisterContent(state, onEvent)
                        AuthScreen.VERIFY_EMAIL -> VerifyEmailContent(state, onEvent)
                        AuthScreen.FORGOT_PASSWORD -> ForgotPasswordContent(state, onEvent)
                        AuthScreen.RESET_PASSWORD -> ResetPasswordContent(state, onEvent)
                        AuthScreen.MFA_ENROLL -> TotpEnrollmentContent(state, onEvent)
                        AuthScreen.MFA_FACTOR_SELECTION -> FactorSelectionContent(state, onEvent)
                        AuthScreen.MFA_CHALLENGE -> TotpChallengeContent(state, onEvent)
                        AuthScreen.AUTHENTICATED -> AuthenticatedContent(state, onEvent)
                        AuthScreen.SESSION_EXPIRED -> SessionExpiredContent(onEvent)
                        AuthScreen.AAL_INSUFFICIENT -> AalInsufficientContent(onEvent)
                    }
                    state.error?.let { MessageCard(it.userMessage(), isError = true) }
                    state.notice?.let { MessageCard(it, isError = false) }
                }
            }
        }
    }
}

@Composable
private fun AuthHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("K", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text("MY KEYS", fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(
                "Autenticación protegida por MFA",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RestoringContent() {
    ScreenTitle("Restaurando sesión", "Validando de forma segura tu sesión guardada.")
    CircularProgressIndicator()
}

@Composable
private fun LoginContent(state: AuthUiState, onEvent: (AuthEvent) -> Unit) {
    ScreenTitle("Inicia sesión", "Usa la contraseña de acceso. La contraseña maestra del vault es distinta.")
    EmailField(state, onEvent)
    PasswordField("Contraseña de acceso", state.password) { onEvent(AuthEvent.PasswordChanged(it)) }
    PrimaryButton("Continuar", state.isBusy) { onEvent(AuthEvent.SubmitLogin) }
    TextButton(onClick = { onEvent(AuthEvent.ShowForgotPassword) }) {
        Text("He olvidado mi contraseña de acceso")
    }
    TextButton(onClick = { onEvent(AuthEvent.ShowRegister) }) { Text("Crear una cuenta") }
}

@Composable
private fun RegisterContent(state: AuthUiState, onEvent: (AuthEvent) -> Unit) {
    ScreenTitle("Crea tu cuenta", "Después de verificar el email deberás registrar un factor TOTP.")
    EmailField(state, onEvent)
    PasswordField("Contraseña de acceso", state.password) { onEvent(AuthEvent.PasswordChanged(it)) }
    PasswordField("Repite la contraseña de acceso", state.passwordConfirmation) {
        onEvent(AuthEvent.PasswordConfirmationChanged(it))
    }
    Text(
        "Mínimo 12 caracteres. No reutilices aquí la futura contraseña maestra.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    PrimaryButton("Registrarme", state.isBusy) { onEvent(AuthEvent.SubmitRegister) }
    TextButton(onClick = { onEvent(AuthEvent.ShowLogin) }) { Text("Ya tengo una cuenta") }
}

@Composable
private fun VerifyEmailContent(state: AuthUiState, onEvent: (AuthEvent) -> Unit) {
    ScreenTitle("Verifica tu email", "Abre el enlace enviado a ${state.email}. Volverás a My Keys para continuar.")
    MessageCard("El enlace solo inicia la sesión AAL1. Tus datos seguirán bloqueados hasta completar MFA.", false)
    PrimaryButton("Reenviar email", state.isBusy) { onEvent(AuthEvent.ResendVerification) }
    TextButton(onClick = { onEvent(AuthEvent.ShowLogin) }) { Text("Volver al login") }
}

@Composable
private fun ForgotPasswordContent(state: AuthUiState, onEvent: (AuthEvent) -> Unit) {
    ScreenTitle("Recupera el acceso", "Te enviaremos un enlace para cambiar solo la contraseña de acceso.")
    EmailField(state, onEvent)
    MessageCard("Este proceso no recupera MFA, la contraseña maestra ni la Recovery Key del vault.", false)
    PrimaryButton("Enviar enlace", state.isBusy) { onEvent(AuthEvent.SubmitPasswordResetRequest) }
    TextButton(onClick = { onEvent(AuthEvent.ShowLogin) }) { Text("Volver al login") }
}

@Composable
private fun ResetPasswordContent(state: AuthUiState, onEvent: (AuthEvent) -> Unit) {
    ScreenTitle("Nueva contraseña de acceso", "El vault y sus claves no cambian con esta operación.")
    PasswordField("Nueva contraseña de acceso", state.password) { onEvent(AuthEvent.PasswordChanged(it)) }
    PasswordField("Repite la nueva contraseña", state.passwordConfirmation) {
        onEvent(AuthEvent.PasswordConfirmationChanged(it))
    }
    PrimaryButton("Guardar contraseña", state.isBusy) { onEvent(AuthEvent.SubmitNewPassword) }
}

@Composable
private fun TotpEnrollmentContent(state: AuthUiState, onEvent: (AuthEvent) -> Unit) {
    ScreenTitle("Protege tu cuenta con TOTP", "MFA es obligatorio antes de acceder a cualquier dato privado.")
    if (state.enrollment == null) {
        OutlinedTextField(
            value = state.factorName,
            onValueChange = { onEvent(AuthEvent.FactorNameChanged(it)) },
            label = { Text("Nombre del factor") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        MessageCard("Recomendación: añade después otro factor en un dispositivo o aplicación distintos.", false)
        PrimaryButton("Generar QR", state.isBusy) { onEvent(AuthEvent.BeginTotpEnrollment) }
    } else {
        val enrollment = state.enrollment
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(20.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberQrCodePainter(enrollment.uri),
                contentDescription = "Código QR para configurar el factor TOTP",
                modifier = Modifier.size(240.dp),
            )
        }
        Text("Alternativa manual", fontWeight = FontWeight.SemiBold)
        SelectionContainer {
            Text(
                enrollment.secret,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            )
        }
        TotpCodeField(state, onEvent)
        PrimaryButton("Verificar y activar", state.isBusy) { onEvent(AuthEvent.VerifyTotp) }
    }
    MessageCard("La Recovery Key del vault nunca permite omitir MFA.", false)
}

@Composable
private fun FactorSelectionContent(state: AuthUiState, onEvent: (AuthEvent) -> Unit) {
    ScreenTitle("Elige un factor", "Tienes varios factores verificados. Usa cualquiera de ellos para alcanzar AAL2.")
    state.factors.forEach { factor ->
        Button(
            onClick = { onEvent(AuthEvent.FactorSelected(factor.id)) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(factor.friendlyName)
        }
    }
}

@Composable
private fun TotpChallengeContent(state: AuthUiState, onEvent: (AuthEvent) -> Unit) {
    val selectedName = state.factors.firstOrNull { it.id == state.selectedFactorId }?.friendlyName
    ScreenTitle("Código de verificación", "Introduce el código de seis dígitos${selectedName?.let { " de $it" }.orEmpty()}.")
    TotpCodeField(state, onEvent)
    PrimaryButton("Verificar MFA", state.isBusy) { onEvent(AuthEvent.VerifyTotp) }
    if (state.factors.size > 1) {
        TextButton(onClick = { onEvent(AuthEvent.FactorSelected("")) }) {
            Text("Elegir otro factor")
        }
    }
}

@Composable
private fun AuthenticatedContent(state: AuthUiState, onEvent: (AuthEvent) -> Unit) {
    ScreenTitle("Sesión protegida", "${state.email} ha alcanzado AAL2.")
    MessageCard("RLS permite ahora datos propios. La criptografía del vault llegará en la Fase 3.", false)
    PrimaryButton("Añadir factor de respaldo", state.isBusy) { onEvent(AuthEvent.AddBackupFactor) }
    TextButton(onClick = { onEvent(AuthEvent.Logout) }, enabled = !state.isBusy) {
        Text("Cerrar sesión")
    }
}

@Composable
private fun SessionExpiredContent(onEvent: (AuthEvent) -> Unit) {
    ScreenTitle("Sesión expirada", "La copia local ya no puede usarse y debes autenticarte de nuevo.")
    PrimaryButton("Volver al login", false) { onEvent(AuthEvent.ShowLogin) }
}

@Composable
private fun AalInsufficientContent(onEvent: (AuthEvent) -> Unit) {
    ScreenTitle("Falta completar MFA", "Una sesión AAL1 no puede acceder a datos privados.")
    PrimaryButton("Revisar sesión", false) { onEvent(AuthEvent.Restore) }
    TextButton(onClick = { onEvent(AuthEvent.Logout) }) { Text("Cerrar sesión") }
}

@Composable
private fun EmailField(state: AuthUiState, onEvent: (AuthEvent) -> Unit) {
    OutlinedTextField(
        value = state.email,
        onValueChange = { onEvent(AuthEvent.EmailChanged(it)) },
        label = { Text("Email") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TotpCodeField(state: AuthUiState, onEvent: (AuthEvent) -> Unit) {
    OutlinedTextField(
        value = state.totpCode,
        onValueChange = { onEvent(AuthEvent.TotpCodeChanged(it)) },
        label = { Text("Código TOTP") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PrimaryButton(text: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Text(text)
        }
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = title,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = subtitle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun MessageCard(message: String, isError: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
