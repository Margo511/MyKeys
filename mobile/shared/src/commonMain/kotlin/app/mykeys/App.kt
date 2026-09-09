package app.mykeys

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import app.mykeys.designsystem.MyKeysTheme
import app.mykeys.presentation.auth.AuthEvent
import app.mykeys.presentation.auth.AuthPresenter
import app.mykeys.presentation.auth.AuthScreen
import kotlinx.coroutines.launch

@Composable
fun App(
    presenter: AuthPresenter,
    initialDeepLink: String? = null,
) {
    val state by presenter.state.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(presenter, initialDeepLink) {
        presenter.onEvent(AuthEvent.Restore)
        initialDeepLink?.let { presenter.onEvent(AuthEvent.DeepLinkReceived(it)) }
    }

    MyKeysTheme {
        AuthScreen(
            state = state,
            onEvent = { event -> scope.launch { presenter.onEvent(event) } },
        )
    }
}
