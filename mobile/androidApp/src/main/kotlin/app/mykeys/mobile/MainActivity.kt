package app.mykeys.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.mykeys.App
import app.mykeys.data.auth.AndroidSecureAuthStorage
import app.mykeys.data.auth.SupabaseAuthConfig
import app.mykeys.data.auth.createAuthPresenter
import app.mykeys.presentation.auth.AuthEvent
import app.mykeys.presentation.auth.AuthPresenter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var presenter: AuthPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        presenter = createAuthPresenter(
            config = SupabaseAuthConfig(
                url = BuildConfig.SUPABASE_URL,
                publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            ),
            storage = AndroidSecureAuthStorage(applicationContext),
        )
        setContent {
            App(
                presenter = presenter,
                initialDeepLink = intent?.dataString,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let { uri ->
            lifecycleScope.launch {
                presenter.onEvent(AuthEvent.DeepLinkReceived(uri))
            }
        }
    }
}
