package app.mykeys.data.auth

import app.mykeys.domain.model.AssuranceLevel
import app.mykeys.domain.model.AuthFailure
import app.mykeys.domain.model.AuthResult
import app.mykeys.domain.model.AuthSession
import app.mykeys.domain.model.SessionStage
import app.mykeys.domain.model.TotpEnrollment
import app.mykeys.domain.model.TotpFactor
import app.mykeys.domain.port.SessionPort
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.mfa.AuthenticatorAssuranceLevel
import io.github.jan.supabase.auth.mfa.FactorType
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.logging.LogLevel
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

data class SupabaseAuthConfig(
    val url: String,
    val publishableKey: String,
) {
    val isValid: Boolean
        get() {
            val validUrl = runCatching { Url(url) }.getOrNull()?.let { parsed ->
                parsed.protocol.name == "https" ||
                    (parsed.protocol.name == "http" && parsed.host in LOCAL_HOSTS)
            } ?: false
            return validUrl &&
                publishableKey.startsWith("sb_publishable_") &&
                !publishableKey.startsWith("sb_secret_") &&
                !publishableKey.contains("service_role", ignoreCase = true)
        }

    private companion object {
        val LOCAL_HOSTS = setOf("127.0.0.1", "localhost", "10.0.2.2")
    }
}

class SupabaseSessionAdapter(
    config: SupabaseAuthConfig,
    secureStorage: SecureAuthStorage,
) : SessionPort {
    private val configurationValid = config.isValid
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val sessionManager = SecureSessionManager(secureStorage, json)
    private val codeVerifierCache = SecureCodeVerifierCache(secureStorage)
    private var passwordRecoveryPending = false
    private val client = createSupabaseClient(
        supabaseUrl = if (config.url.isBlank()) "https://configuration-required.invalid" else config.url,
        supabaseKey = if (config.publishableKey.isBlank()) "sb_publishable_configuration_required" else config.publishableKey,
    ) {
        defaultLogLevel = LogLevel.NONE
        requestTimeout = kotlin.time.Duration.parse("15s")
        install(Auth) {
            flowType = FlowType.PKCE
            scheme = CALLBACK_SCHEME
            host = CALLBACK_HOST
            defaultRedirectUrl = CALLBACK_URL
            alwaysAutoRefresh = true
            autoLoadFromStorage = false
            autoSaveToStorage = true
            sessionManager = this@SupabaseSessionAdapter.sessionManager
            codeVerifierCache = this@SupabaseSessionAdapter.codeVerifierCache
        }
    }

    override suspend fun register(email: String, accessPassword: String): AuthResult<SessionStage> = guarded {
        ensureConfigured()
        passwordRecoveryPending = false
        client.auth.signUpWith(Email, redirectUrl = CALLBACK_URL) {
            this.email = email
            password = accessPassword
        }
        val session = client.auth.currentSessionOrNull()
        if (session == null) {
            SessionStage.EmailVerificationRequired(email)
        } else {
            stageFor(session)
        }
    }

    override suspend fun resendVerificationEmail(email: String): AuthResult<Unit> = guarded {
        ensureConfigured()
        client.auth.resendEmail(OtpType.Email.SIGNUP, email, redirectUrl = CALLBACK_URL)
    }

    override suspend fun login(email: String, accessPassword: String): AuthResult<SessionStage> = guarded {
        ensureConfigured()
        passwordRecoveryPending = false
        client.auth.signInWith(Email) {
            this.email = email
            password = accessPassword
        }
        stageFor(requireNotNull(client.auth.currentSessionOrNull()))
    }

    override suspend fun logout(): AuthResult<Unit> {
        if (!configurationValid) {
            sessionManager.deleteSession()
            codeVerifierCache.deleteCodeVerifier()
            return AuthResult.Success(Unit)
        }
        return try {
            client.auth.signOut(SignOutScope.LOCAL)
            AuthResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AuthResult.Failure(mapFailure(error))
        } finally {
            passwordRecoveryPending = false
            client.auth.clearSession()
            codeVerifierCache.deleteCodeVerifier()
        }
    }

    override suspend fun restoreSession(): AuthResult<SessionStage> = guarded {
        ensureConfigured()
        val restored = client.auth.loadFromStorage(autoRefresh = false)
        if (!restored) return@guarded SessionStage.Anonymous
        try {
            client.auth.refreshCurrentSession()
        } catch (error: AuthRestException) {
            if (error.errorCode in SESSION_ERROR_CODES) client.auth.clearSession()
            throw error
        }
        val session = requireNotNull(client.auth.currentSessionOrNull())
        passwordRecoveryPending = session.type == "recovery"
        stageFor(session)
    }

    override suspend fun requestAccessPasswordReset(email: String): AuthResult<Unit> = guarded {
        ensureConfigured()
        client.auth.resetPasswordForEmail(email, redirectUrl = CALLBACK_URL)
    }

    override suspend fun resetAccessPassword(newAccessPassword: String): AuthResult<SessionStage> = guarded {
        ensureConfigured()
        client.auth.updateUser {
            password = newAccessPassword
        }
        passwordRecoveryPending = false
        stageFor(requireNotNull(client.auth.currentSessionOrNull()))
    }

    override suspend fun handleDeepLink(uri: String): AuthResult<SessionStage> = guarded {
        ensureConfigured()
        val url = Url(uri)
        if (url.protocol.name != CALLBACK_SCHEME || url.host != CALLBACK_HOST || url.encodedPath != CALLBACK_PATH) {
            throw InvalidCallbackException()
        }
        if (url.parameters["error"] != null || url.parameters["error_code"] != null) {
            throw InvalidCallbackException()
        }
        val code = url.parameters["code"] ?: throw InvalidCallbackException()
        val session = client.auth.exchangeCodeForSession(code)
        if (session.type == "recovery") {
            passwordRecoveryPending = true
            stageFor(session)
        } else {
            passwordRecoveryPending = false
            stageFor(session)
        }
    }

    override suspend fun listTotpFactors(): AuthResult<List<TotpFactor>> = guarded {
        ensureConfigured()
        client.auth.mfa.retrieveFactorsForCurrentUser()
            .filter { it.factorType == "totp" && it.isVerified }
            .map { factor ->
                TotpFactor(
                    id = factor.id,
                    friendlyName = factor.friendlyName?.takeIf(String::isNotBlank) ?: "Authenticator",
                    verified = factor.isVerified,
                )
            }
    }

    override suspend fun enrollTotp(friendlyName: String): AuthResult<TotpEnrollment> = guarded {
        ensureConfigured()
        val factor = client.auth.mfa.enroll(FactorType.TOTP, friendlyName) {
            issuer = "My Keys"
        }
        TotpEnrollment(
            factorId = factor.id,
            secret = factor.data.secret,
            uri = factor.data.uri,
        )
    }

    override suspend fun verifyTotp(factorId: String, code: String): AuthResult<SessionStage> = guarded {
        ensureConfigured()
        val session = client.auth.mfa.createChallengeAndVerify(
            factorId = factorId,
            code = code,
            saveSession = true,
        )
        stageFor(session)
    }

    override suspend fun removeTotpFactor(factorId: String): AuthResult<Unit> = guarded {
        ensureConfigured()
        val verified = client.auth.mfa.retrieveFactorsForCurrentUser()
            .filter { it.factorType == "totp" && it.isVerified }
        if (verified.none { it.id == factorId }) throw FactorMissingException()
        if (verified.size <= 1) throw LastFactorException()
        client.auth.mfa.unenroll(factorId)
        client.auth.refreshCurrentSession()
    }

    private suspend fun stageFor(session: UserSession): SessionStage {
        val user = session.user ?: client.auth.retrieveUserForCurrentSession(updateSession = true)
        if (user.emailConfirmedAt == null) {
            return SessionStage.EmailVerificationRequired(user.email.orEmpty())
        }
        val domainSession = session.copy(user = user).toDomainSession()
        val factors = client.auth.mfa.retrieveFactorsForCurrentUser()
            .filter { it.factorType == "totp" && it.isVerified }
            .map { factor ->
                TotpFactor(
                    id = factor.id,
                    friendlyName = factor.friendlyName?.takeIf(String::isNotBlank) ?: "Authenticator",
                    verified = true,
                )
            }
        return when {
            passwordRecoveryPending && domainSession.assuranceLevel == AssuranceLevel.AAL2 -> {
                SessionStage.PasswordRecoveryReady(domainSession)
            }
            passwordRecoveryPending && factors.isNotEmpty() -> {
                SessionStage.PasswordRecoveryMfaRequired(domainSession, factors)
            }
            passwordRecoveryPending -> SessionStage.PasswordRecoveryReady(domainSession)
            domainSession.assuranceLevel == AssuranceLevel.AAL2 -> SessionStage.Authenticated(domainSession)
            factors.isEmpty() -> SessionStage.TotpEnrollmentRequired(domainSession)
            else -> SessionStage.TotpChallengeRequired(domainSession, factors)
        }
    }

    private fun UserSession.toDomainSession(): AuthSession {
        val user = requireNotNull(user)
        val level = client.auth.mfa.getAuthenticatorAssuranceLevel(accessToken).current
        return AuthSession(
            userId = user.id,
            email = user.email.orEmpty(),
            emailVerified = user.emailConfirmedAt != null,
            assuranceLevel = if (level == AuthenticatorAssuranceLevel.AAL2) {
                AssuranceLevel.AAL2
            } else {
                AssuranceLevel.AAL1
            },
            expiresAtEpochSeconds = expiresAt.epochSeconds,
        )
    }

    private fun ensureConfigured() {
        if (!configurationValid) throw ConfigurationException()
    }

    private suspend inline fun <T> guarded(crossinline block: suspend () -> T): AuthResult<T> = try {
        AuthResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        AuthResult.Failure(mapFailure(error))
    }

    private fun mapFailure(error: Throwable): AuthFailure = when (error) {
        is ConfigurationException -> AuthFailure.ConfigurationMissing
        is InvalidCallbackException -> AuthFailure.InvalidDeepLink
        is LastFactorException -> AuthFailure.LastVerifiedFactor
        is FactorMissingException -> AuthFailure.FactorNotFound
        is HttpRequestTimeoutException,
        is HttpRequestException,
        -> AuthFailure.NetworkUnavailable

        is AuthRestException -> when (error.errorCode) {
            AuthErrorCode.InvalidCredentials -> AuthFailure.InvalidCredentials
            AuthErrorCode.EmailNotConfirmed,
            AuthErrorCode.ProviderEmailNeedsVerification,
            -> AuthFailure.EmailNotVerified

            AuthErrorCode.EmailExists,
            AuthErrorCode.UserAlreadyExists,
            -> AuthFailure.EmailAlreadyRegistered

            AuthErrorCode.WeakPassword -> AuthFailure.WeakAccessPassword
            AuthErrorCode.MfaVerificationFailed,
            AuthErrorCode.MfaVerificationRejected,
            AuthErrorCode.MfaChallengeExpired,
            -> AuthFailure.InvalidTotpCode

            AuthErrorCode.MfaFactorNotFound -> AuthFailure.FactorNotFound
            AuthErrorCode.InsufficientAal -> AuthFailure.InsufficientAssurance
            AuthErrorCode.OverRequestRateLimit,
            AuthErrorCode.OverEmailSendRateLimit,
            -> AuthFailure.RateLimited

            in SESSION_ERROR_CODES -> AuthFailure.SessionExpired
            else -> AuthFailure.ServiceUnavailable
        }

        else -> AuthFailure.Unexpected
    }

    private class ConfigurationException : IllegalStateException()
    private class InvalidCallbackException : IllegalArgumentException()
    private class LastFactorException : IllegalStateException()
    private class FactorMissingException : IllegalArgumentException()

    private companion object {
        const val CALLBACK_SCHEME = "mykeys"
        const val CALLBACK_HOST = "auth"
        const val CALLBACK_PATH = "/callback"
        const val CALLBACK_URL = "$CALLBACK_SCHEME://$CALLBACK_HOST$CALLBACK_PATH"

        val SESSION_ERROR_CODES = setOf(
            AuthErrorCode.SessionNotFound,
            AuthErrorCode.SessionExpired,
            AuthErrorCode.RefreshTokenNotFound,
            AuthErrorCode.RefreshTokenAlreadyUsed,
        )
    }
}
