package app.mykeys.data.auth

import app.mykeys.domain.usecase.AuthUseCases
import app.mykeys.presentation.auth.AuthPresenter

fun createAuthPresenter(
    config: SupabaseAuthConfig,
    storage: SecureAuthStorage,
): AuthPresenter {
    val port = SupabaseSessionAdapter(config, storage)
    return AuthPresenter(AuthUseCases.from(port))
}
