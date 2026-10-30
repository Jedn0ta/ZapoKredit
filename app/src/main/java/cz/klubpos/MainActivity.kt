package cz.klubpos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cz.klubpos.ui.inventory.InventoryScreen
import cz.klubpos.ui.member.MemberDetailScreen
import cz.klubpos.ui.members.MembersScreen
import cz.klubpos.ui.products.ProductsScreen
import cz.klubpos.ui.settings.SettingsScreen
import cz.klubpos.ui.shopping.ShoppingScreen
import cz.klubpos.ui.theme.KlubTheme
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.ui.theme.AppBackground
import cz.klubpos.ui.theme.glass
import cz.klubpos.ui.login.LoginScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.tour.LocalTour
import cz.klubpos.ui.tour.TourController
import cz.klubpos.ui.tour.TourOverlay
import cz.klubpos.ui.tour.TourScreen
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember

private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    MEMBERS("members", "Home", Icons.Default.Home),
    PRODUCTS("products", "Nabídka", Icons.Default.LocalBar),
    INVENTORY("inventory", "Sklad", Icons.Default.Inventory2),
    SHOPPING("shopping", "Nákup", Icons.Default.ShoppingCart),
    SETTINGS("settings", "Nastavení", Icons.Default.Settings),
}

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
        val app = application as KlubPosApp
        val repo = app.repository
        setContent {
            KlubTheme {
                val settings by app.settings.flow.collectAsState()
                // Bar tablet: keep the screen awake while the POS is open.
                LaunchedEffect(settings.keepScreenOn) {
                    if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                val tour = remember { TourController() }
                LaunchedEffect(Unit) {
                    repo.seedIfEmpty() // demo data on first start – remove for production
                    if (!settings.tourDone) { tour.start(); app.settings.update { it.copy(tourDone = true) } }
                }
                // Bartender login: required as soon as at least one bartender exists.
                val bartenders by repo.bartenders().collectAsState(initial = null)
                val current by repo.bartender.collectAsState()
                val locked = !bartenders.isNullOrEmpty() && current == null && !tour.active

                // Auto-lock after inactivity.
                var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(settings.autoLockMinutes, current) {
                    while (settings.autoLockMinutes > 0 && current != null) {
                        delay(15_000)
                        if (System.currentTimeMillis() - lastTouch > settings.autoLockMinutes * 60_000L) repo.logout()
                    }
                }
                // No vibrations anywhere in the app
                val noHaptics = remember { object : androidx.compose.ui.hapticfeedback.HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) = Unit
                } }
                CompositionLocalProvider(LocalTour provides tour, androidx.compose.ui.platform.LocalHapticFeedback provides noHaptics) {
                  cz.klubpos.ui.components.TiltProvider {
                    AppBackground {
                        // Keep all content clear of the status bar, navigation bar and display cutouts
                        // (the backdrop still draws edge to edge behind them).
                        Box(Modifier.fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) { awaitPointerEvent(PointerEventPass.Initial); lastTouch = System.currentTimeMillis() }
                            }
                        }) {
                            if (bartenders == null) Unit
                            else if (locked) LoginScreen()
                            else AppNav()
                            TourOverlay(tour)
                        }
                    }
                  }
                }
            }
        }
    }
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    fun go(d: Dest) = nav.navigate(d.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true; restoreState = true
    }

    // The guided tour drives navigation: each step opens the screen it explains.
    val tour = LocalTour.current
    val repo = rememberRepo()
    val members by remember { repo.memberStatuses() }.collectAsState(initial = emptyList())
    val tourScreen = tour.step?.screen
    LaunchedEffect(tourScreen, members.isNotEmpty()) {
        when (tourScreen) {
            null -> Unit
            TourScreen.MEMBER -> members.firstOrNull()?.let { m ->
                if (current?.startsWith("member/") != true) nav.navigate("member/${m.memberId}")
            }
            else -> {
                if (current?.startsWith("member/") == true) nav.popBackStack()
                val d = when (tourScreen) {
                    TourScreen.MEMBERS -> Dest.MEMBERS; TourScreen.INVENTORY -> Dest.INVENTORY
                    TourScreen.SHOPPING -> Dest.SHOPPING; TourScreen.PRODUCTS -> Dest.PRODUCTS
                    else -> Dest.SETTINGS
                }
                if (current != d.route) go(d)
            }
        }
    }

    val host: @Composable (Modifier) -> Unit = { mod ->
        NavHost(nav, startDestination = Dest.MEMBERS.route, modifier = mod) {
            composable(Dest.MEMBERS.route) { MembersScreen(onOpenMember = { nav.navigate("member/$it") }) }
            composable("member/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                MemberDetailScreen(it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
            }
            composable(Dest.INVENTORY.route) { InventoryScreen() }
            composable(Dest.SHOPPING.route) { ShoppingScreen() }
            composable(Dest.PRODUCTS.route) { ProductsScreen() }
            composable(Dest.SETTINGS.route) { SettingsScreen(onStartTour = { tour.start() }) }
        }
    }

    // Phone: bottom bar. Tablet (>= 600dp wide): side rail – more room for the medallion grid.
    // The order screen is a full-screen workspace – no navigation chrome.
    val chrome = current?.startsWith("member/") != true
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        // Single, stable tree: NavHost always in the same place, bars just appear/disappear.
        Row(Modifier.fillMaxSize()) {
            if (wide && chrome) NavigationRail(containerColor = Color.Transparent, header = { BartenderChip(compact = true) }) {
                Spacer(Modifier.weight(1f))
                Dest.entries.forEach { d ->
                    NavigationRailItem(current == d.route, { go(d) }, icon = { Icon(d.icon, d.label) }, label = { Text(d.label) },
                        colors = NavigationRailItemDefaults.colors(indicatorColor = KlubColors.Gold.copy(alpha = 0.18f),
                            selectedIconColor = KlubColors.Gold, selectedTextColor = KlubColors.Gold))
                }
                Spacer(Modifier.weight(1f))
            }
            Column(Modifier.weight(1f).fillMaxSize()) {
                host(Modifier.weight(1f))
                if (!wide && chrome) FloatingNavBar(current, onSelect = { go(it) })
            }
        }
    }
}

/** Floating glass pill with the sections; the selected one expands with its label. */
@Composable
private fun FloatingNavBar(current: String?, onSelect: (Dest) -> Unit) {
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)
        .glass(RoundedCornerShape(30.dp), alpha = 0.08f).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Dest.entries.forEach { d ->
            val sel = current == d.route
            Row(Modifier.clip(RoundedCornerShape(24.dp))
                .then(if (sel) Modifier.background(KlubColors.GoldBrush) else Modifier)
                .clickable { onSelect(d) }.padding(horizontal = if (sel) 16.dp else 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(d.icon, d.label, tint = if (sel) KlubColors.Ink else KlubColors.Muted, modifier = Modifier.size(22.dp))
                AnimatedVisibility(sel) {
                    Text(d.label, Modifier.padding(start = 8.dp), color = KlubColors.Ink,
                        style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

/** Logged-in bartender; tap to log out (lock). */
@Composable
fun BartenderChip(compact: Boolean = false) {
    val repo = rememberRepo()
    val b by repo.bartender.collectAsState()
    val bt = b ?: return
    Column(Modifier.padding(vertical = 12.dp).clip(RoundedCornerShape(16.dp)).clickable { repo.logout() }.padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        cz.klubpos.ui.login.Avatar(bt, if (compact) 44 else 36)
        Text("Odhlásit", style = MaterialTheme.typography.labelSmall, color = KlubColors.Muted, modifier = Modifier.padding(top = 4.dp))
    }
}
