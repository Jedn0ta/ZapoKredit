package cz.klubpos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cz.klubpos.AppServices
import cz.klubpos.LocalServices
import cz.klubpos.core.crypto.ImageStore
import cz.klubpos.data.db.createDesktopDatabase
import cz.klubpos.platform.Platform
import cz.klubpos.ui.AppRoot
import cz.klubpos.ui.theme.KlubTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import javax.swing.JOptionPane
import kotlin.system.exitProcess

/**
 * ZapoKredit for Windows: the same app as on the tablet (shared code), with its own encrypted database.
 * The PC is the main register: EET is sent from here and waiters' phones connect to it.
 */
fun main() {
    // one running copy only (both would write into the same database)
    val lock = runCatching { RandomAccessFile(File(Platform.dataDir, "app.lock"), "rw").channel.tryLock() }.getOrNull()
    if (lock == null) {
        JOptionPane.showMessageDialog(null, "ZapoKredit už běží.", "ZapoKredit", JOptionPane.INFORMATION_MESSAGE)
        exitProcess(0)
    }
    val services = try {
        AppServices({ createDesktopDatabase(File(Platform.dataDir, "klubpos.db"), Platform.subKey("database")) }, { ImageStore() })
            .also { it.repository }   // open the database now, so problems show up immediately
    } catch (e: Throwable) {
        JOptionPane.showMessageDialog(null, "Databázi nejde otevřít:\n${e.message}", "ZapoKredit", JOptionPane.ERROR_MESSAGE)
        exitProcess(1)
    }
    val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    background.launch { services.eet.retryLoop() }
    if (services.lan.config.value.enabled) background.launch { services.lan.start() }

    application {
        Window(
            onCloseRequest = { services.lan.stop(); exitApplication() },
            title = "ZapoKredit",
            state = rememberWindowState(placement = WindowPlacement.Maximized, width = 1366.dp, height = 820.dp),
        ) {
            KlubTheme {
                CompositionLocalProvider(LocalServices provides services) {
                    Box(Modifier.fillMaxSize()) { AppRoot() }
                }
            }
        }
    }
}
