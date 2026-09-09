package app.mykeys.domain.usecase

import app.mykeys.domain.model.AuthFailure
import app.mykeys.domain.model.AuthResult
import app.mykeys.domain.model.SessionStage
import app.mykeys.domain.model.TotpEnrollment
import app.mykeys.domain.model.TotpFactor
import app.mykeys.domain.port.SessionPort

private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private val totpPattern = Regex("^[0-9]{6}$")

internal fun normalizeEmail(value: String): String = value.trim().lowercase()

internal fun validEmail(value: String): Boolean = emailPattern.matches(normalizeEmail(value))

internal fun validAccessPassword(value: String): Boolean = value.length in 12..128

class RegisterAccount(private val sessionPort: SessionPort) {
    suspend operator fun invoke(
        email: String,
        password: String,
        confirmation: String,
    ): AuthResult<SessionStage> {
        if (!validEmail(email)) return AuthResult.Failure(AuthFailure.InvalidEmail)
        if (!validAccessPassword(password)) return AuthResult.Failure(AuthFailure.WeakAccessPassword)
        if (password != confirmation) return AuthResult.Failure(AuthFailure.PasswordMismatch)
        return sessionPort.register(normalizeEmail(email), password)
    }
}

class ResendVerificationEmail(private val sessionPort: SessionPort) {
    suspend operator fun invoke(email: String): AuthResult<Unit> {
        if (!validEmail(email)) return AuthResult.Failure(AuthFailure.InvalidEmail)
        return sessionPort.resendVerificationEmail(normalizeEmail(email))
    }
}

class Login(private val sessionPort: SessionPort) {
    suspend operator fun invoke(email: String, password: String): AuthResult<SessionStage> {
        if (!validEmail(email)) return AuthResult.Failure(AuthFailure.InvalidEmail)
        if (password.isEmpty()) return AuthResult.Failure(AuthFailure.InvalidCredentials)
        return sessionPort.login(normalizeEmail(email), password)
    }
}

class Logout(private val sessionPort: SessionPort) {
    suspend operator fun invoke(): AuthResult<Unit> = sessionPort.logout()
}

class RestoreSession(private val sessionPort: SessionPort) {
    suspend operator fun invoke(): AuthResult<SessionStage> = sessionPort.restoreSession()
}

class RequestAccessPasswordReset(private val sessionPort: SessionPort) {
    suspend operator fun invoke(email: String): AuthResult<Unit> {
        if (!validEmail(email)) return AuthResult.Failure(AuthFailure.InvalidEmail)
        return sessionPort.requestAccessPasswordReset(normalizeEmail(email))
    }
}

class ResetAccessPassword(private val sessionPort: SessionPort) {
    suspend operator fun invoke(password: String, confirmation: String): AuthResult<SessionStage> {
        if (!validAccessPassword(password)) return AuthResult.Failure(AuthFailure.WeakAccessPassword)
        if (password != confirmation) return AuthResult.Failure(AuthFailure.PasswordMismatch)
        return sessionPort.resetAccessPassword(password)
    }
}

class HandleAuthDeepLink(private val sessionPort: SessionPort) {
    suspend operator fun invoke(uri: String): AuthResult<SessionStage> {
        if (!uri.startsWith("mykeys://auth/callback?")) {
            return AuthResult.Failure(AuthFailure.InvalidDeepLink)
        }
        return sessionPort.handleDeepLink(uri)
    }
}

class ListTotpFactors(private val sessionPort: SessionPort) {
    suspend operator fun invoke(): AuthResult<List<TotpFactor>> = sessionPort.listTotpFactors()
}

class EnrollTotp(private val sessionPort: SessionPort) {
    suspend operator fun invoke(friendlyName: String): AuthResult<TotpEnrollment> {
        val safeName = friendlyName.trim().ifEmpty { "Authenticator" }.take(64)
        return sessionPort.enrollTotp(safeName)
    }
}

class VerifyTotp(private val sessionPort: SessionPort) {
    suspend operator fun invoke(factorId: String, code: String): AuthResult<SessionStage> {
        if (factorId.isBlank()) return AuthResult.Failure(AuthFailure.FactorNotFound)
        val normalizedCode = code.filterNot(Char::isWhitespace)
        if (!totpPattern.matches(normalizedCode)) {
            return AuthResult.Failure(AuthFailure.InvalidTotpCode)
        }
        return sessionPort.verifyTotp(factorId, normalizedCode)
    }
}

class RemoveTotpFactor(private val sessionPort: SessionPort) {
    suspend operator fun invoke(factorId: String): AuthResult<Unit> {
        val factors = when (val result = sessionPort.listTotpFactors()) {
            is AuthResult.Failure -> return result
            is AuthResult.Success -> result.value.filter(TotpFactor::verified)
        }
        if (factors.none { it.id == factorId }) {
            return AuthResult.Failure(AuthFailure.FactorNotFound)
        }
        if (factors.size <= 1) {
            return AuthResult.Failure(AuthFailure.LastVerifiedFactor)
        }
        return sessionPort.removeTotpFactor(factorId)
    }
}

data class AuthUseCases(
    val register: RegisterAccount,
    val resendVerification: ResendVerificationEmail,
    val login: Login,
    val logout: Logout,
    val restore: RestoreSession,
    val requestPasswordReset: RequestAccessPasswordReset,
    val resetPassword: ResetAccessPassword,
    val handleDeepLink: HandleAuthDeepLink,
    val listFactors: ListTotpFactors,
    val enrollTotp: EnrollTotp,
    val verifyTotp: VerifyTotp,
    val removeFactor: RemoveTotpFactor,
) {
    companion object {
        fun from(port: SessionPort) = AuthUseCases(
            register = RegisterAccount(port),
            resendVerification = ResendVerificationEmail(port),
            login = Login(port),
            logout = Logout(port),
            restore = RestoreSession(port),
            requestPasswordReset = RequestAccessPasswordReset(port),
            resetPassword = ResetAccessPassword(port),
            handleDeepLink = HandleAuthDeepLink(port),
            listFactors = ListTotpFactors(port),
            enrollTotp = EnrollTotp(port),
            verifyTotp = VerifyTotp(port),
            removeFactor = RemoveTotpFactor(port),
        )
    }
}
