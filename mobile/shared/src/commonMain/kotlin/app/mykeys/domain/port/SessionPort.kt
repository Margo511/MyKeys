package app.mykeys.domain.port

import app.mykeys.domain.model.AuthResult
import app.mykeys.domain.model.SessionStage
import app.mykeys.domain.model.TotpEnrollment
import app.mykeys.domain.model.TotpFactor

interface SessionPort {
    suspend fun register(email: String, accessPassword: String): AuthResult<SessionStage>

    suspend fun resendVerificationEmail(email: String): AuthResult<Unit>

    suspend fun login(email: String, accessPassword: String): AuthResult<SessionStage>

    suspend fun logout(): AuthResult<Unit>

    suspend fun restoreSession(): AuthResult<SessionStage>

    suspend fun requestAccessPasswordReset(email: String): AuthResult<Unit>

    suspend fun resetAccessPassword(newAccessPassword: String): AuthResult<SessionStage>

    suspend fun handleDeepLink(uri: String): AuthResult<SessionStage>

    suspend fun listTotpFactors(): AuthResult<List<TotpFactor>>

    suspend fun enrollTotp(friendlyName: String): AuthResult<TotpEnrollment>

    suspend fun verifyTotp(factorId: String, code: String): AuthResult<SessionStage>

    suspend fun removeTotpFactor(factorId: String): AuthResult<Unit>
}
