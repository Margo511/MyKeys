package app.mykeys

import androidx.compose.ui.window.ComposeUIViewController
import app.mykeys.data.auth.IosSecureAuthStorage
import app.mykeys.data.auth.SupabaseAuthConfig
import app.mykeys.data.auth.createAuthPresenter
import app.mykeys.presentation.auth.AuthEvent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class IosAuthRuntime(
    supabaseUrl: String,
    publishableKey: String,
) {
    private val scope = MainScope()
    private val presenter = createAuthPresenter(
        config = SupabaseAuthConfig(supabaseUrl, publishableKey),
        storage = IosSecureAuthStorage(),
    )

    fun makeViewController() = ComposeUIViewController { App(presenter) }

    fun handleDeepLink(uri: String) {
        scope.launch { presenter.onEvent(AuthEvent.DeepLinkReceived(uri)) }
    }

    fun close() {
        scope.cancel()
    }
}
