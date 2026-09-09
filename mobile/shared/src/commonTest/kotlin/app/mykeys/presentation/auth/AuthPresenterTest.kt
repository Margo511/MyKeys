package app.mykeys.presentation.auth

import app.mykeys.domain.FakeSessionPort
import app.mykeys.domain.model.AssuranceLevel
import app.mykeys.domain.model.AuthFailure
import app.mykeys.domain.model.AuthResult
import app.mykeys.domain.model.AuthSession
import app.mykeys.domain.model.SessionStage
import app.mykeys.domain.model.TotpFactor
import app.mykeys.domain.usecase.AuthUseCases
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthPresenterTest {
    private val session = AuthSession(
        userId = "user-a",
        email = "a@example.com",
        emailVerified = true,
        assuranceLevel = AssuranceLevel.AAL1,
        expiresAtEpochSeconds = 1_800_000_000,
    )

    @Test
    fun restoreWithMultipleFactorsShowsSelectionThenChallenge() = runTest {
        val factors = listOf(
            TotpFactor("primary", "Principal", true),
            TotpFactor("backup", "Respaldo", true),
        )
        val presenter = presenterFor(AuthResult.Success(SessionStage.TotpChallengeRequired(session, factors)))

        presenter.onEvent(AuthEvent.Restore)
        assertEquals(AuthScreen.MFA_FACTOR_SELECTION, presenter.state.value.screen)

        presenter.onEvent(AuthEvent.FactorSelected("backup"))
        assertEquals(AuthScreen.MFA_CHALLENGE, presenter.state.value.screen)
        assertEquals("backup", presenter.state.value.selectedFactorId)

        presenter.onEvent(AuthEvent.FactorSelected(""))
        assertEquals(AuthScreen.MFA_FACTOR_SELECTION, presenter.state.value.screen)
        assertNull(presenter.state.value.selectedFactorId)
    }

    @Test
    fun loginFailureClearsThePasswordAndUsesTypedError() = runTest {
        val presenter = presenterFor(AuthResult.Failure(AuthFailure.InvalidCredentials))
        presenter.onEvent(AuthEvent.ShowLogin)
        presenter.onEvent(AuthEvent.EmailChanged("a@example.com"))
        presenter.onEvent(AuthEvent.PasswordChanged("not-persisted"))

        presenter.onEvent(AuthEvent.SubmitLogin)

        assertEquals(AuthFailure.InvalidCredentials, presenter.state.value.error)
        assertEquals("", presenter.state.value.password)
        assertNull(presenter.state.value.notice)
    }

    @Test
    fun aal2StageOpensOnlyTheAuthenticatedShell() = runTest {
        val aal2 = session.copy(assuranceLevel = AssuranceLevel.AAL2)
        val presenter = presenterFor(AuthResult.Success(SessionStage.Authenticated(aal2)))

        presenter.onEvent(AuthEvent.Restore)

        assertEquals(AuthScreen.AUTHENTICATED, presenter.state.value.screen)
        assertEquals("a@example.com", presenter.state.value.email)
    }

    @Test
    fun passwordRecoveryWithMfaRequiresChallengeBeforeNewPassword() = runTest {
        val factor = TotpFactor("primary", "Principal", true)
        val presenter = presenterFor(
            AuthResult.Success(SessionStage.PasswordRecoveryMfaRequired(session, listOf(factor))),
        )

        presenter.onEvent(AuthEvent.DeepLinkReceived("mykeys://auth/callback?code=test"))

        assertEquals(AuthScreen.MFA_CHALLENGE, presenter.state.value.screen)
        assertEquals("primary", presenter.state.value.selectedFactorId)
        assertEquals(
            "Completa MFA antes de cambiar tu contraseña de acceso.",
            presenter.state.value.notice,
        )
    }

    private fun presenterFor(result: AuthResult<SessionStage>): AuthPresenter {
        val port = FakeSessionPort().apply { stageResult = result }
        return AuthPresenter(AuthUseCases.from(port))
    }
}
