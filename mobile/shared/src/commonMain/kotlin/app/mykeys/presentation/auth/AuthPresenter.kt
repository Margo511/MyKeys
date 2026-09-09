package app.mykeys.presentation.auth

import app.mykeys.domain.model.AuthFailure
import app.mykeys.domain.model.AuthResult
import app.mykeys.domain.model.SessionStage
import app.mykeys.domain.usecase.AuthUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthPresenter(
    private val useCases: AuthUseCases,
) {
    private val mutableState = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = mutableState.asStateFlow()

    suspend fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.EmailChanged -> update { copy(email = event.value, error = null) }
            is AuthEvent.PasswordChanged -> update { copy(password = event.value, error = null) }
            is AuthEvent.PasswordConfirmationChanged -> update {
                copy(passwordConfirmation = event.value, error = null)
            }
            is AuthEvent.TotpCodeChanged -> update {
                copy(totpCode = event.value.filter(Char::isDigit).take(6), error = null)
            }
            is AuthEvent.FactorNameChanged -> update { copy(factorName = event.value.take(64), error = null) }
            is AuthEvent.DeepLinkReceived -> execute { useCases.handleDeepLink(event.uri) }
            is AuthEvent.FactorSelected -> update {
                copy(
                    screen = if (event.factorId.isBlank()) {
                        AuthScreen.MFA_FACTOR_SELECTION
                    } else {
                        AuthScreen.MFA_CHALLENGE
                    },
                    selectedFactorId = event.factorId.ifBlank { null },
                    totpCode = "",
                    error = null,
                )
            }

            AuthEvent.Restore -> execute { useCases.restore() }
            AuthEvent.ShowLogin -> show(AuthScreen.LOGIN)
            AuthEvent.ShowRegister -> show(AuthScreen.REGISTER)
            AuthEvent.ShowForgotPassword -> show(AuthScreen.FORGOT_PASSWORD)
            AuthEvent.SubmitRegister -> execute {
                useCases.register(state.value.email, state.value.password, state.value.passwordConfirmation)
            }
            AuthEvent.SubmitLogin -> execute {
                useCases.login(state.value.email, state.value.password)
            }
            AuthEvent.ResendVerification -> executeUnit(
                successNotice = "Si el email corresponde a una cuenta pendiente, recibirás un nuevo enlace.",
            ) {
                useCases.resendVerification(state.value.email)
            }
            AuthEvent.SubmitPasswordResetRequest -> executeUnit(
                successNotice = "Si existe una cuenta con ese email, recibirás un enlace para restablecerla.",
            ) {
                useCases.requestPasswordReset(state.value.email)
            }
            AuthEvent.SubmitNewPassword -> execute {
                useCases.resetPassword(state.value.password, state.value.passwordConfirmation)
            }
            AuthEvent.BeginTotpEnrollment -> executeEnrollment()
            AuthEvent.VerifyTotp -> verifyTotp()
            AuthEvent.AddBackupFactor -> update {
                copy(
                    screen = AuthScreen.MFA_ENROLL,
                    enrollment = null,
                    factorName = "Authenticator de respaldo",
                    totpCode = "",
                    error = null,
                    notice = null,
                )
            }
            AuthEvent.Logout -> logout()
            AuthEvent.DismissMessage -> update { copy(error = null, notice = null) }
        }
    }

    private suspend fun execute(operation: suspend () -> AuthResult<SessionStage>) {
        update { copy(isBusy = true, error = null, notice = null) }
        when (val result = operation()) {
            is AuthResult.Success -> applyStage(result.value)
            is AuthResult.Failure -> applyFailure(result.reason)
        }
    }

    private suspend fun executeEnrollment() {
        update { copy(isBusy = true, error = null, notice = null) }
        when (val result = useCases.enrollTotp(state.value.factorName)) {
            is AuthResult.Success -> update {
                copy(
                    screen = AuthScreen.MFA_ENROLL,
                    isBusy = false,
                    enrollment = result.value,
                    selectedFactorId = result.value.factorId,
                    totpCode = "",
                )
            }
            is AuthResult.Failure -> applyFailure(result.reason)
        }
    }

    private suspend fun verifyTotp() {
        val factorId = state.value.selectedFactorId ?: state.value.enrollment?.factorId.orEmpty()
        update { copy(isBusy = true, error = null, notice = null) }
        when (val result = useCases.verifyTotp(factorId, state.value.totpCode)) {
            is AuthResult.Success -> applyStage(result.value)
            is AuthResult.Failure -> applyFailure(result.reason)
        }
    }

    private suspend fun executeUnit(
        successNotice: String,
        operation: suspend () -> AuthResult<Unit>,
    ) {
        update { copy(isBusy = true, error = null, notice = null) }
        when (val result = operation()) {
            is AuthResult.Success -> update { copy(isBusy = false, notice = successNotice) }
            is AuthResult.Failure -> applyFailure(result.reason)
        }
    }

    private suspend fun logout() {
        update { copy(isBusy = true, error = null, notice = null) }
        val result = useCases.logout()
        mutableState.value = AuthUiState(
            screen = AuthScreen.LOGIN,
            error = (result as? AuthResult.Failure)?.reason,
        )
    }

    private fun applyStage(stage: SessionStage) {
        val currentEmail = state.value.email
        mutableState.value = when (stage) {
            SessionStage.Anonymous -> AuthUiState(screen = AuthScreen.LOGIN)
            is SessionStage.EmailVerificationRequired -> AuthUiState(
                screen = AuthScreen.VERIFY_EMAIL,
                email = stage.email.ifBlank { currentEmail },
            )
            is SessionStage.TotpEnrollmentRequired -> AuthUiState(
                screen = AuthScreen.MFA_ENROLL,
                email = stage.session.email,
            )
            is SessionStage.TotpChallengeRequired -> AuthUiState(
                screen = if (stage.factors.size == 1) {
                    AuthScreen.MFA_CHALLENGE
                } else {
                    AuthScreen.MFA_FACTOR_SELECTION
                },
                email = stage.session.email,
                factors = stage.factors,
                selectedFactorId = stage.factors.singleOrNull()?.id,
            )
            is SessionStage.PasswordRecoveryReady -> AuthUiState(
                screen = AuthScreen.RESET_PASSWORD,
                email = stage.session.email,
            )
            is SessionStage.PasswordRecoveryMfaRequired -> AuthUiState(
                screen = if (stage.factors.size == 1) {
                    AuthScreen.MFA_CHALLENGE
                } else {
                    AuthScreen.MFA_FACTOR_SELECTION
                },
                email = stage.session.email,
                factors = stage.factors,
                selectedFactorId = stage.factors.singleOrNull()?.id,
                notice = "Completa MFA antes de cambiar tu contraseña de acceso.",
            )
            is SessionStage.Authenticated -> AuthUiState(
                screen = AuthScreen.AUTHENTICATED,
                email = stage.session.email,
            )
        }
    }

    private fun applyFailure(failure: AuthFailure) {
        update {
            copy(
                screen = when (failure) {
                    AuthFailure.SessionExpired -> AuthScreen.SESSION_EXPIRED
                    AuthFailure.InsufficientAssurance -> AuthScreen.AAL_INSUFFICIENT
                    else -> screen
                },
                isBusy = false,
                password = "",
                passwordConfirmation = "",
                totpCode = "",
                error = failure,
            )
        }
    }

    private fun show(screen: AuthScreen) {
        mutableState.value = AuthUiState(
            screen = screen,
            email = state.value.email,
        )
    }

    private inline fun update(block: AuthUiState.() -> AuthUiState) {
        mutableState.value = mutableState.value.block()
    }
}
