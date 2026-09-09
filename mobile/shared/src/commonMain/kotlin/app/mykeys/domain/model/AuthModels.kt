package app.mykeys.domain.model

enum class AssuranceLevel {
    AAL1,
    AAL2,
}

data class AuthSession(
    val userId: String,
    val email: String,
    val emailVerified: Boolean,
    val assuranceLevel: AssuranceLevel,
    val expiresAtEpochSeconds: Long,
)

data class TotpFactor(
    val id: String,
    val friendlyName: String,
    val verified: Boolean,
)

data class TotpEnrollment(
    val factorId: String,
    val secret: String,
    val uri: String,
)

sealed interface SessionStage {
    data object Anonymous : SessionStage

    data class EmailVerificationRequired(val email: String) : SessionStage

    data class TotpEnrollmentRequired(val session: AuthSession) : SessionStage

    data class TotpChallengeRequired(
        val session: AuthSession,
        val factors: List<TotpFactor>,
    ) : SessionStage

    data class PasswordRecoveryReady(val session: AuthSession) : SessionStage

    data class PasswordRecoveryMfaRequired(
        val session: AuthSession,
        val factors: List<TotpFactor>,
    ) : SessionStage

    data class Authenticated(val session: AuthSession) : SessionStage
}

sealed interface AuthFailure {
    data object InvalidEmail : AuthFailure
    data object WeakAccessPassword : AuthFailure
    data object PasswordMismatch : AuthFailure
    data object InvalidCredentials : AuthFailure
    data object EmailNotVerified : AuthFailure
    data object EmailAlreadyRegistered : AuthFailure
    data object InvalidTotpCode : AuthFailure
    data object FactorNotFound : AuthFailure
    data object LastVerifiedFactor : AuthFailure
    data object SessionExpired : AuthFailure
    data object InsufficientAssurance : AuthFailure
    data object InvalidDeepLink : AuthFailure
    data object RateLimited : AuthFailure
    data object NetworkUnavailable : AuthFailure
    data object ConfigurationMissing : AuthFailure
    data object ServiceUnavailable : AuthFailure
    data object Unexpected : AuthFailure
}

sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>
    data class Failure(val reason: AuthFailure) : AuthResult<Nothing>
}
