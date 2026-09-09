package app.mykeys.domain

import app.mykeys.domain.model.AuthResult
import app.mykeys.domain.model.SessionStage
import app.mykeys.domain.model.TotpEnrollment
import app.mykeys.domain.model.TotpFactor
import app.mykeys.domain.port.SessionPort

class FakeSessionPort : SessionPort {
    var stageResult: AuthResult<SessionStage> = AuthResult.Success(SessionStage.Anonymous)
    var unitResult: AuthResult<Unit> = AuthResult.Success(Unit)
    var factorsResult: AuthResult<List<TotpFactor>> = AuthResult.Success(emptyList())
    var enrollmentResult: AuthResult<TotpEnrollment> = AuthResult.Success(
        TotpEnrollment("factor", "SECRET", "otpauth://totp/MyKeys:test?secret=SECRET"),
    )
    var registerCalls = 0
    var lastEmail: String? = null
    var removedFactor: String? = null

    override suspend fun register(email: String, accessPassword: String): AuthResult<SessionStage> {
        registerCalls += 1
        lastEmail = email
        return stageResult
    }

    override suspend fun resendVerificationEmail(email: String): AuthResult<Unit> = unitResult

    override suspend fun login(email: String, accessPassword: String): AuthResult<SessionStage> {
        lastEmail = email
        return stageResult
    }

    override suspend fun logout(): AuthResult<Unit> = unitResult

    override suspend fun restoreSession(): AuthResult<SessionStage> = stageResult

    override suspend fun requestAccessPasswordReset(email: String): AuthResult<Unit> = unitResult

    override suspend fun resetAccessPassword(newAccessPassword: String): AuthResult<SessionStage> = stageResult

    override suspend fun handleDeepLink(uri: String): AuthResult<SessionStage> = stageResult

    override suspend fun listTotpFactors(): AuthResult<List<TotpFactor>> = factorsResult

    override suspend fun enrollTotp(friendlyName: String): AuthResult<TotpEnrollment> = enrollmentResult

    override suspend fun verifyTotp(factorId: String, code: String): AuthResult<SessionStage> = stageResult

    override suspend fun removeTotpFactor(factorId: String): AuthResult<Unit> {
        removedFactor = factorId
        return unitResult
    }
}
