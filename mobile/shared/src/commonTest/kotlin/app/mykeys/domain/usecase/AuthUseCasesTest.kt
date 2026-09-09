package app.mykeys.domain.usecase

import app.mykeys.domain.FakeSessionPort
import app.mykeys.domain.model.AuthFailure
import app.mykeys.domain.model.AuthResult
import app.mykeys.domain.model.TotpFactor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AuthUseCasesTest {
    @Test
    fun registerRejectsInvalidInputBeforeCallingTheAdapter() = runTest {
        val port = FakeSessionPort()
        val useCase = RegisterAccount(port)

        assertEquals(AuthResult.Failure(AuthFailure.InvalidEmail), useCase("bad", "long-enough-password", "long-enough-password"))
        assertEquals(AuthResult.Failure(AuthFailure.WeakAccessPassword), useCase("a@example.com", "short", "short"))
        assertEquals(AuthResult.Failure(AuthFailure.PasswordMismatch), useCase("a@example.com", "long-enough-password", "different-password"))
        assertEquals(0, port.registerCalls)
    }

    @Test
    fun registerNormalizesEmailWithoutMutatingPassword() = runTest {
        val port = FakeSessionPort()

        RegisterAccount(port)("  PERSON@Example.COM ", "long-enough-password", "long-enough-password")

        assertEquals("person@example.com", port.lastEmail)
        assertEquals(1, port.registerCalls)
    }

    @Test
    fun verifyTotpAcceptsOnlySixDigits() = runTest {
        val useCase = VerifyTotp(FakeSessionPort())

        val result = useCase("factor", "12 34")

        assertEquals(AuthResult.Failure(AuthFailure.InvalidTotpCode), result)
    }

    @Test
    fun removeTotpRefusesTheLastVerifiedFactor() = runTest {
        val port = FakeSessionPort().apply {
            factorsResult = AuthResult.Success(listOf(TotpFactor("primary", "Principal", true)))
        }

        val result = RemoveTotpFactor(port)("primary")

        assertEquals(AuthResult.Failure(AuthFailure.LastVerifiedFactor), result)
        assertNull(port.removedFactor)
    }

    @Test
    fun removeTotpAllowsOneFactorWhenAnotherVerifiedFactorRemains() = runTest {
        val port = FakeSessionPort().apply {
            factorsResult = AuthResult.Success(
                listOf(
                    TotpFactor("primary", "Principal", true),
                    TotpFactor("backup", "Respaldo", true),
                ),
            )
        }

        val result = RemoveTotpFactor(port)("primary")

        assertIs<AuthResult.Success<Unit>>(result)
        assertEquals("primary", port.removedFactor)
    }
}
