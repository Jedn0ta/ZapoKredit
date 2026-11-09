package cz.klubpos

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import cz.klubpos.ui.AppRoot
import cz.klubpos.ui.theme.AppBackground
import cz.klubpos.ui.theme.KlubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.IS_KEY_APP) {
            // Owner's phone: the key app only (no database, no POS).
            setContent { KlubTheme { AppBackground {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    cz.klubpos.ui.keyapp.KeyApp()
                }
            } } }
            return
        }
        if (BuildConfig.IS_WAITER_APP) {
            // Waiter's phone: orders over Wi-Fi to the register (no local database).
            setContent { KlubTheme { AppBackground {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { cz.klubpos.waiter.WaiterApp() }
            } } }
            return
        }
        val app = application as KlubPosApp
        // Register: accept orders from waiters / the PC when switched on in Settings
        if (app.lan.config.value.enabled) cz.klubpos.lan.LanService.start(app)
        setContent {
            KlubTheme {
                CompositionLocalProvider(LocalServices provides app.services) {
                    AppRoot(onKeepScreenOn = { on ->
                        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    })
                }
            }
        }
    }
}
