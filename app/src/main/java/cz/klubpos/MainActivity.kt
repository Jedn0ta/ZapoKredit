package cz.klubpos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.People
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
    MEMBERS("members", "Členové", Icons.Default.People),
    INVENTORY("inventory", "Sklad", Icons.Default.Inventory2),
    SHOPPING("shopping", "Nákup", Icons.Default.ShoppingCart),
    PRODUCTS("products", "Položky", Icons.Default.LocalBar),
    SETTINGS("settings", "Nastavení", Icons.Default.Settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                CompositionLocalProvider(LocalTour provides tour) {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            AppNav()
                            TourOverlay(tour)
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
            if (wide && chrome) NavigationRail {
                Dest.entries.forEach { d ->
                    NavigationRailItem(current == d.route, { go(d) }, icon = { Icon(d.icon, d.label) }, label = { Text(d.label) })
                }
            }
            Column(Modifier.weight(1f).fillMaxSize()) {
                host(Modifier.weight(1f))
                if (!wide && chrome) NavigationBar {
                    Dest.entries.forEach { d ->
                        NavigationBarItem(current == d.route, { go(d) }, icon = { Icon(d.icon, d.label) }, label = { Text(d.label) })
                    }
                }
            }
        }
    }
}
