package app.mykeys.presentation.auth

import app.mykeys.domain.model.AuthFailure
import app.mykeys.domain.model.TotpEnrollment
import app.mykeys.domain.model.TotpFactor

enum class AuthScreen {
    RESTORING,
    LOGIN,
    REGISTER,
    VERIFY_EMAIL,
    FORGOT_PASSWORD,
    RESET_PASSWORD,
    MFA_ENROLL,
    MFA_FACTOR_SELECTION,
    MFA_CHALLENGE,
    AUTHENTICATED,
    SESSION_EXPIRED,
    AAL_INSUFFICIENT,
}

data class AuthUiState(
    val screen: AuthScreen = AuthScreen.RESTORING,
    val isBusy: Boolean = false,
    val email: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val totpCode: String = "",
    val factorName: String = "Authenticator principal",
    val factors: List<TotpFactor> = emptyList(),
    val selectedFactorId: String? = null,
    val enrollment: TotpEnrollment? = null,
    val error: AuthFailure? = null,
    val notice: String? = null,
)

sealed interface AuthEvent {
    data class EmailChanged(val value: String) : AuthEvent
    data class PasswordChanged(val value: String) : AuthEvent
    data class PasswordConfirmationChanged(val value: String) : AuthEvent
    data class TotpCodeChanged(val value: String) : AuthEvent
    data class FactorNameChanged(val value: String) : AuthEvent
    data class DeepLinkReceived(val uri: String) : AuthEvent
    data class FactorSelected(val factorId: String) : AuthEvent

    data object Restore : AuthEvent
    data object ShowLogin : AuthEvent
    data object ShowRegister : AuthEvent
    data object ShowForgotPassword : AuthEvent
    data object SubmitRegister : AuthEvent
    data object SubmitLogin : AuthEvent
    data object ResendVerification : AuthEvent
    data object SubmitPasswordResetRequest : AuthEvent
    data object SubmitNewPassword : AuthEvent
    data object BeginTotpEnrollment : AuthEvent
    data object VerifyTotp : AuthEvent
    data object AddBackupFactor : AuthEvent
    data object Logout : AuthEvent
    data object DismissMessage : AuthEvent
}

fun AuthFailure.userMessage(): String = when (this) {
    AuthFailure.InvalidEmail -> "Introduce un email válido."
    AuthFailure.WeakAccessPassword -> "La contraseña de acceso debe tener entre 12 y 128 caracteres."
    AuthFailure.PasswordMismatch -> "Las contraseñas de acceso no coinciden."
    AuthFailure.InvalidCredentials -> "No se pudo iniciar sesión con esas credenciales."
    AuthFailure.EmailNotVerified -> "Verifica tu email antes de continuar."
    AuthFailure.EmailAlreadyRegistered -> "No se pudo completar el registro con ese email."
    AuthFailure.InvalidTotpCode -> "El código no es válido o ha caducado. Inténtalo de nuevo."
    AuthFailure.FactorNotFound -> "Ese factor ya no está disponible."
    AuthFailure.LastVerifiedFactor -> "No puedes eliminar el último factor verificado. Añade primero uno de respaldo."
    AuthFailure.SessionExpired -> "Tu sesión ha caducado. Inicia sesión de nuevo."
    AuthFailure.InsufficientAssurance -> "Debes completar MFA para acceder a tus datos."
    AuthFailure.InvalidDeepLink -> "El enlace de autenticación no es válido o ha caducado."
    AuthFailure.RateLimited -> "Demasiados intentos. Espera unos minutos antes de reintentar."
    AuthFailure.NetworkUnavailable -> "No se pudo conectar. Comprueba la red y vuelve a intentarlo."
    AuthFailure.ConfigurationMissing -> "La configuración pública de Supabase no está disponible en este build."
    AuthFailure.ServiceUnavailable -> "El servicio de autenticación no está disponible ahora mismo."
    AuthFailure.Unexpected -> "No se pudo completar la operación."
}
