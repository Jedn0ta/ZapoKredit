package cz.klubpos.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import cz.klubpos.AppServices
import cz.klubpos.LocalServices
import cz.klubpos.core.crypto.ImageStore
import cz.klubpos.data.db.createDesktopDatabase
import cz.klubpos.platform.Platform
import cz.klubpos.ui.AppRoot
import cz.klubpos.ui.theme.AppBackground
import cz.klubpos.ui.theme.KlubTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Renders the real screens of the PC app off-screen (same composables as on the tablet) and saves PNGs.
 * Catches crashes in composition on the PC and lets us look at the result.
 */
class ScreensTest {
    private val out = File(System.getProperty("zk.screens") ?: "build/screens").apply { mkdirs() }

    private fun shot(name: String, services: AppServices, w: Int = 1366, h: Int = 820, content: @Composable () -> Unit) {
        ImageComposeScene(w, h, Density(1f)) {
            KlubTheme { CompositionLocalProvider(LocalServices provides services) { AppBackground { content() } } }
        }.use { scene ->
            var t = 0L
            repeat(30) { scene.render(t); t += 100_000_000; Thread.sleep(60) }
            val img = scene.render(t)
            File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }

    @Test fun render_screens() {
        val services = AppServices({ createDesktopDatabase(File(Platform.dataDir, "screens.db").apply { delete() }, Platform.subKey("database")) }, { ImageStore() })
        runBlocking { services.repository.seedIfEmpty() }
        services.settings.update { it.copy(tourDone = true) }
        val first = runBlocking { services.repository.memberStatuses().first() }.first()

        shot("1-home", services) { AppRoot() }
        shot("2-ucet", services) { cz.klubpos.ui.member.MemberDetailScreen(first.memberId, onBack = {}) }
        shot("3-nabidka", services) { cz.klubpos.ui.products.DrinkMenuScreen() }
        shot("4-sklad", services) { cz.klubpos.ui.inventory.InventoryScreen() }
        shot("5-nakup", services) { cz.klubpos.ui.shopping.ShoppingScreen() }
        shot("6-nastaveni", services) { cz.klubpos.ui.settings.SettingsScreen() }
        shot("7-objednavky", services) { cz.klubpos.ui.orders.OrdersScreen(onOpenMember = {}) }
        shot("8-home-mobil", services, 420, 860) { AppRoot() }
        assertTrue(File(out, "1-home.png").length() > 10_000)
    }
}
