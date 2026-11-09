@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cz.klubpos.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cz.klubpos.domain.Payments
import cz.klubpos.net.Lan
import cz.klubpos.net.LanApi
import cz.klubpos.net.LanClient
import cz.klubpos.net.LanException
import cz.klubpos.net.MemberDetailDto
import cz.klubpos.net.MemberDto
import cz.klubpos.net.OrderDto
import cz.klubpos.net.OrderItemDto
import cz.klubpos.net.ProductDto
import cz.klubpos.net.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

// ---------------- look ----------------

private object C {
    val Gold = Color(0xFFF5C451)
    val Ink = Color(0xFF15131B)
    val Bg = Color(0xFF121016)
    val Surface = Color(0xFF1D1A24)
    val SurfaceHigh = Color(0xFF29252F)
    val Muted = Color(0xFF9C97A6)
    val Hairline = Color(0x33FFFFFF)
    val Green = Color(0xFF3DDC97)
    val Orange = Color(0xFFFF9F43)
    val Red = Color(0xFFFF5D6C)
}

private val TINTS = listOf(0xFFF5C451, 0xFFFF9F43, 0xFFFF5D6C, 0xFFFF7EB6, 0xFFA78BFA, 0xFF4DA8FF, 0xFF2DD4BF, 0xFF3DDC97, 0xFFB0A27A, 0xFF9C97A6)
    .map { Color(it) }

private fun kc(v: Long) = "%,d Kč".format(Locale("cs", "CZ"), v).replace(' ', ' ')
private fun qty(q: Double) = if (q % 1.0 == 0.0) q.toLong().toString() else "%.2f".format(q).trimEnd('0').trimEnd(',', '.')

@Composable
private fun PcTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = C.Gold, onPrimary = C.Ink, background = C.Bg, surface = C.Surface,
        surfaceVariant = C.SurfaceHigh, onSurface = Color(0xFFF4EFE6), onBackground = Color(0xFFF4EFE6), secondary = C.Gold)) {
        Surface(Modifier.fillMaxSize(), color = C.Bg) { content() }
    }
}

// ---------------- saved connection ----------------

private object Store {
    private val file = File(System.getProperty("user.home"), ".zapokredit/pc.properties")
    private val p = Properties().apply { runCatching { file.inputStream().use { load(it) } } }
    var code: Lan.Code?
        get() = p.getProperty("code")?.let { Lan.Code.parse(it) }
        set(v) { if (v == null) p.remove("code") else p.setProperty("code", v.text()); save() }
    var name: String
        get() = p.getProperty("name") ?: "PC"
        set(v) { p.setProperty("name", v); save() }
    private fun save() = runCatching { file.parentFile.mkdirs(); file.outputStream().use { p.store(it, "ZapoKredit PC") } }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "ZapoKredit – pokladna", state = rememberWindowState(width = 1366.dp, height = 820.dp)) {
        PcTheme { App() }
    }
}

@Composable
private fun App() {
    var code by remember { mutableStateOf(Store.code) }
    var name by remember { mutableStateOf(Store.name) }
    val c = code
    if (c == null) ConnectScreen(name) { newCode, newName -> Store.code = newCode; Store.name = newName; name = newName; code = newCode }
    else MainScreen(c, name, onCodeChanged = { Store.code = it; code = it }, onDisconnect = { Store.code = null; code = null })
}

// ---------------- connect ----------------

@Composable
private fun ConnectScreen(initialName: String, onConnected: (Lan.Code, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<List<Lan.Found>>(emptyList()) }
    var host by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { found = withContext(Dispatchers.IO) { Lan.discover() }; delay(5_000) } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(24.dp)).background(C.Surface).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ZapoKredit – pokladna pro PC", style = MaterialTheme.typography.headlineSmall)
            Text("Připoj se k hlavní pokladně (tablet). Na tabletu: Nastavení → Číšníci a PC → zapnout. Tam uvidíš adresu a kód.",
                color = C.Muted, style = MaterialTheme.typography.bodyMedium)
            if (found.isNotEmpty()) {
                Text("Nalezené pokladny v síti:", color = C.Muted)
                found.forEach { f ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (host.startsWith(f.host)) C.SurfaceHigh else Color.Transparent)
                        .clickable { host = "${f.host}:${f.port}" }.padding(10.dp)) {
                        Text(f.club.ifBlank { "Pokladna" }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(f.host, color = C.Muted, fontFamily = FontFamily.Monospace)
                    }
                }
            } else Text("Hledám pokladnu v síti…", color = C.Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(host, { host = it; error = null }, label = { Text("Adresa pokladny (např. 192.168.1.20:8770)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(key, { key = it; error = null }, label = { Text("Kód (XXXX-XXXX-XXXX-XXXX-XXXX)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(name, { name = it }, label = { Text("Název tohoto PC (uvidí ho bar)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = C.Red) }
            Button(enabled = !busy, onClick = {
                val k = Lan.normalizeKey(key)
                val h = host.substringBefore(':').trim(); val port = host.substringAfter(':', "").toIntOrNull() ?: Lan.PORT
                if (h.isBlank()) { error = "Zadej adresu"; return@Button }
                if (!Lan.isValidKey(k)) { error = "Kód má 20 znaků – opiš ho z tabletu"; return@Button }
                val code = Lan.Code(h, port, k)
                busy = true
                scope.launch {
                    try { withContext(Dispatchers.IO) { LanApi(LanClient(code, name.ifBlank { "PC" })).hello() }; onConnected(code, name.ifBlank { "PC" }) }
                    catch (e: LanException) { error = e.message }
                    busy = false
                }
            }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(if (busy) "Připojuji…" else "Připojit") }
        }
    }
}

// ---------------- main ----------------

private enum class Section(val label: String) { HOME("Home"), TABLES("Stoly"), ORDERS("Objednávky") }

@Composable
private fun MainScreen(code: Lan.Code, name: String, onCodeChanged: (Lan.Code) -> Unit, onDisconnect: () -> Unit) {
    val api = remember(code, name) { LanApi(LanClient(code, name)) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var snap by remember { mutableStateOf<Snapshot?>(null) }
    var orders by remember { mutableStateOf<List<OrderDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableStateOf(Section.HOME) }
    var open by remember { mutableStateOf<Long?>(null) }
    var tick by remember { mutableStateOf(0) }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        try { snap = api.snapshot(); orders = api.orders(); error = null }
        catch (e: LanException) {
            error = e.message
            Lan.discover().firstOrNull { it.keyId == Lan.keyId(code.key) && it.host != code.host }
                ?.let { f -> withContext(Dispatchers.Main) { onCodeChanged(code.copy(host = f.host, port = f.port)) } }
        }
    }
    LaunchedEffect(api) { while (true) { refresh(); tick++; delay(3_000) } }
    fun act(block: suspend () -> Unit) = scope.launch {
        try { withContext(Dispatchers.IO) { block() }; refresh() } catch (e: LanException) { snackbar.showSnackbar(e.message ?: "Chyba") }
    }

    val newOrders = orders.count { it.status == "NEW" }
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(containerColor = C.Surface) {
                Spacer(Modifier.height(12.dp))
                Section.entries.forEach { s ->
                    NavigationRailItem(section == s && open == null, { section = s; open = null }, icon = {
                        BadgedBox(badge = { if (s == Section.ORDERS && newOrders > 0) Badge { Text("$newOrders") } }) {
                            Icon(when (s) { Section.HOME -> Icons.Filled.Home; Section.TABLES -> Icons.Filled.TableRestaurant; Section.ORDERS -> Icons.Filled.ReceiptLong }, s.label)
                        }
                    }, label = { Text(s.label) })
                }
                Spacer(Modifier.weight(1f))
                NavigationRailItem(false, onDisconnect, icon = { Icon(Icons.Filled.LinkOff, "Odpojit") }, label = { Text("Odpojit") })
                Spacer(Modifier.height(12.dp))
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(20.dp, 12.dp, 20.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(snap?.club ?: "Pokladna", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (error == null && snap != null) C.Green else C.Red))
                    Text("  " + (error ?: "připojeno k ${code.host} · $name"), color = if (error != null) C.Red else C.Muted,
                        style = MaterialTheme.typography.bodySmall)
                }
                val s = snap
                val o = open
                when {
                    s == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error ?: "Načítám…", color = C.Muted) }
                    o != null -> MemberPane(api, o, s.products, tick, snackbar, onBack = { open = null; scope.launch { refresh() } })
                    section == Section.HOME -> HomePane(s.members.filter { !it.isTable }) { open = it }
                    section == Section.TABLES -> TablesPane(s.members.filter { it.isTable && (it.inClub || it.balance != 0L) },
                        onOpen = { open = it }, onNew = { no -> scope.launch {
                            try { val id = withContext(Dispatchers.IO) { api.table(no) }; refresh(); open = id }
                            catch (e: LanException) { snackbar.showSnackbar(e.message ?: "Chyba") }
                        } })
                    else -> OrdersPane(orders, onOpen = { it?.let { id -> open = id } }) { id, st -> act { api.setOrderStatus(id, st) } }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

// ---------------- home / tables ----------------

@Composable
private fun HomePane(members: List<MemberDto>, onOpen: (Long) -> Unit) {
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) }
    val shown = members.filter { m ->
        (q.isBlank() || m.name.contains(q, true) || m.nickname?.contains(q, true) == true) &&
            when (filter) { 1 -> m.inClub; 2 -> m.olderDebt > 0; else -> true }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(q, { q = it }, placeholder = { Text("Hledat člena") }, singleLine = true, modifier = Modifier.width(280.dp))
            listOf("Vše", "Přítomní", "Dluh").forEachIndexed { i, l -> FilterChip(filter == i, { filter = i }, label = { Text(l) }) }
        }
        TileGrid(shown, onOpen)
    }
}

@Composable
private fun TileGrid(list: List<MemberDto>, onOpen: (Long) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(170.dp), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list, key = { it.id }) { m -> Tile(m) { onOpen(m.id) } }
    }
}

@Composable
private fun Tile(m: MemberDto, onClick: () -> Unit) {
    val tint = TINTS.getOrNull(m.tileColor)
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier.height(104.dp).fillMaxWidth().alpha(if (m.inClub || m.balance > 0) 1f else 0.6f).clip(shape)
        .background(tint?.copy(alpha = 0.35f) ?: C.Surface)
        .then(if (m.olderDebt > 0) Modifier.border(4.dp, C.Red, shape) else Modifier)
        .clickable(onClick = onClick).padding(12.dp)) {
        if (m.inClub || m.visitedToday) Box(Modifier.align(Alignment.TopStart).size(12.dp).clip(CircleShape).background(if (m.inClub) C.Green else C.Muted))
        if (m.todayUnpaid > 0) Text(kc(m.todayUnpaid), Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(10.dp)).background(C.Orange)
            .padding(horizontal = 8.dp, vertical = 2.dp), color = C.Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(m.label, Modifier.align(Alignment.BottomStart), fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TablesPane(tables: List<MemberDto>, onOpen: (Long) -> Unit, onNew: (Int) -> Unit) {
    var no by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(no, { no = it.filter(Char::isDigit).take(3) }, label = { Text("Číslo stolu") }, singleLine = true, modifier = Modifier.width(160.dp))
            Button({ no.toIntOrNull()?.let(onNew); no = "" }, enabled = no.toIntOrNull() != null) { Text("Otevřít stůl") }
        }
        if (tables.isEmpty()) Text("Žádný obsazený stůl.", Modifier.padding(20.dp), color = C.Muted)
        TileGrid(tables.sortedBy { it.name.removePrefix("Stůl ").toIntOrNull() ?: 999 }, onOpen)
    }
}

// ---------------- orders to prepare ----------------

private fun ago(t: Long): String {
    val m = ((System.currentTimeMillis() - t) / 60_000).coerceAtLeast(0)
    return when { m < 1 -> "teď"; m < 60 -> "před $m min"; else -> "před ${m / 60} h" }
}

@Composable
private fun OrdersPane(orders: List<OrderDto>, onOpen: (Long?) -> Unit, onStatus: (Long, String) -> Unit) {
    val sorted = orders.sortedWith(compareBy<OrderDto>({ when (it.status) { "NEW" -> 0; "PREPARING" -> 1; "DONE" -> 2; else -> 3 } }, { it.createdAt }))
    if (sorted.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Žádné objednávky k přípravě.", color = C.Muted) }
    LazyVerticalGrid(GridCells.Adaptive(320.dp), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(sorted, key = { it.id }) { o ->
            val color = when (o.status) { "NEW" -> C.Orange; "PREPARING" -> C.Gold; "DONE" -> C.Green; else -> C.Muted }
            val done = o.status == "DONE" || o.status == "CANCELLED"
            Column(Modifier.fillMaxWidth().alpha(if (done) 0.55f else 1f).clip(RoundedCornerShape(20.dp)).background(C.Surface)
                .border(if (o.status == "NEW") 2.dp else 0.dp, color, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { onOpen(o.memberId) }) {
                        Text(o.target, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(listOfNotNull(if (o.isTable) "stůl" else "člen", o.waiter, ago(o.createdAt)).joinToString(" · "), color = C.Muted, fontSize = 12.sp)
                    }
                    Text(OrderDto.statusLabel(o.status), color = color, fontWeight = FontWeight.Bold)
                }
                o.items.forEach { i ->
                    Row {
                        Text("${qty(i.qty)}×", Modifier.width(44.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Column {
                            Text(i.name, fontSize = 18.sp)
                            i.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = C.Gold, fontSize = 13.sp) }
                        }
                    }
                }
                o.note?.let { Text("Pozn.: $it", color = C.Gold) }
                Row {
                    when (o.status) {
                        "NEW" -> { TextButton({ onStatus(o.id, "PREPARING") }) { Text("Připravuji") }; TextButton({ onStatus(o.id, "DONE") }) { Text("Hotovo", color = C.Green) } }
                        "PREPARING" -> TextButton({ onStatus(o.id, "DONE") }) { Text("Hotovo", color = C.Green) }
                        else -> TextButton({ onStatus(o.id, "NEW") }) { Text("Vrátit", color = C.Muted) }
                    }
                    Spacer(Modifier.weight(1f))
                    if (!done) TextButton({ onStatus(o.id, "CANCELLED") }) { Text("Zrušit", color = C.Muted) }
                }
            }
        }
    }
}

// ---------------- member / table account ----------------

private class Line(val p: ProductDto, q: Int) { var qty by mutableStateOf(q) }

@Composable
private fun MemberPane(api: LanApi, memberId: Long, products: List<ProductDto>, tick: Int, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<MemberDetailDto?>(null) }
    val cart = remember { mutableStateListOf<Line>() }
    var prep by remember { mutableStateOf(false) }
    var showPay by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val sections = remember(products) { products.sortedBy { it.sectionOrder }.map { it.section }.distinct() }
    var section by remember { mutableStateOf(sections.firstOrNull()) }

    suspend fun load() { runCatching { detail = withContext(Dispatchers.IO) { api.member(memberId) } } }
    LaunchedEffect(memberId, tick) { load() }

    fun add(p: ProductDto, d: Int) {
        val e = cart.firstOrNull { it.p.id == p.id }
        if (e == null) { if (d > 0) cart += Line(p, d) } else { e.qty += d; if (e.qty <= 0) cart.remove(e) }
    }
    /** Writes the new order to the account; leave = "Na futro" (member leaves, it becomes debt). */
    fun write(leave: Boolean, then: () -> Unit = {}) {
        busy = true
        scope.launch {
            try {
                val items = cart.map { OrderItemDto(it.p.id, it.p.name, it.qty.toDouble(), it.p.price) }
                withContext(Dispatchers.IO) { api.order(memberId, null, items, null, prep = prep, leave = leave) }
                cart.clear(); load(); then()
            } catch (e: LanException) { snackbar.showSnackbar(e.message ?: "Chyba") }
            busy = false
        }
    }

    val d = detail
    Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // products
        Column(Modifier.weight(1.5f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onBack) { Text("← Zpět") }
                Text(d?.member?.label ?: "…", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sections.forEach { s -> FilterChip(section == s, { section = s }, label = { Text(s) }) }
            }
            LazyVerticalGrid(GridCells.Adaptive(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(products.filter { it.section == section }, key = { it.id }) { p ->
                    val n = cart.firstOrNull { it.p.id == p.id }?.qty ?: 0
                    Column(Modifier.height(86.dp).clip(RoundedCornerShape(16.dp)).background(if (n > 0) C.Gold.copy(alpha = 0.22f) else C.Surface)
                        .clickable { add(p, 1) }.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row {
                            Text(kc(p.price), Modifier.weight(1f), color = if (p.price != p.basePrice) C.Gold else C.Muted)
                            if (n > 0) Text("$n×", color = C.Gold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        // order + account
        Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("NOVÁ OBJEDNÁVKA", color = C.Gold, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 12.sp)
            if (cart.isEmpty()) Text("Klikni na nápoje vlevo.", color = C.Muted)
            cart.toList().forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ add(l.p, -1) }) { Text("−") }
                    Text("${l.qty}", fontWeight = FontWeight.Bold)
                    TextButton({ add(l.p, 1) }) { Text("+") }
                    Text(l.p.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(kc(l.p.price * l.qty))
                }
            }
            if (cart.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { prep = !prep }) {
                Checkbox(prep, { prep = it }); Text("Poslat do přípravy na bar")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ write(false) }, enabled = cart.isNotEmpty() && !busy, modifier = Modifier.weight(1f)) { Text("Budiž") }
                OutlinedButton({ write(true, onBack) }, enabled = !busy && d != null, modifier = Modifier.weight(1f)) { Text("Na futro") }
                Button({ if (cart.isEmpty()) showPay = true else write(false) { showPay = true } }, enabled = !busy && d != null,
                    modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = C.Green, contentColor = C.Ink)) {
                    Text("Zaplatit", fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider(color = C.Hairline)
            if (d != null) {
                Row {
                    Text("Dnes nezaplaceno", Modifier.weight(1f), color = C.Muted); Text(kc(d.member.todayUnpaid), color = C.Orange, fontWeight = FontWeight.Bold)
                }
                if (d.member.olderDebt > 0) Row {
                    Text("Dluh z minula" + (d.debtSince?.let { " (od " + SimpleDateFormat("d. M.", Locale("cs", "CZ")).format(Date(it)) + ")" } ?: ""),
                        Modifier.weight(1f), color = C.Muted)
                    Text(kc(d.member.olderDebt), color = C.Red, fontWeight = FontWeight.Bold)
                }
                Text("DNES NA ÚČTU", color = C.Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 12.sp)
                LazyColumn(Modifier.weight(1f)) {
                    items(d.lines) { l ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${qty(l.qty)}×", Modifier.width(40.dp), fontWeight = FontWeight.Bold)
                            Text(l.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(kc(l.amount))
                            TextButton({ scope.launch {
                                try { withContext(Dispatchers.IO) { api.void(l.entryIds.first()) }; load() }
                                catch (e: LanException) { snackbar.showSnackbar(e.message ?: "Chyba") }
                            } }) { Text("storno", color = C.Muted, fontSize = 12.sp) }
                        }
                    }
                    items(d.payments) { p ->
                        Row { Text("Platba " + if (p.method == "QR") "QR" else "hotově", Modifier.weight(1f), color = C.Green); Text("−" + kc(p.amount), color = C.Green) }
                    }
                }
            }
        }
    }

    if (showPay && d != null) PayDialog(d.member, onDismiss = { showPay = false }) { amount, method, leave ->
        showPay = false
        scope.launch {
            try {
                withContext(Dispatchers.IO) { api.pay(memberId, amount, method, leave) }
                snackbar.showSnackbar("Zaplaceno ${kc(amount)}")
                if (leave) onBack() else load()
            } catch (e: LanException) { snackbar.showSnackbar(e.message ?: "Chyba") }
        }
    }
}

@Composable
private fun PayDialog(m: MemberDto, onDismiss: () -> Unit, onPay: (Long, String, Boolean) -> Unit) {
    val options = Payments.options(m.todayUnpaid, m.balance, null)
    var picked by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf<String?>(null) }
    var method by remember { mutableStateOf("CASH") }
    var received by remember { mutableStateOf("") }
    var leave by remember { mutableStateOf(m.inClub) }
    val amount = Payments.amount(options, picked, typed)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("${m.label} platí") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { o ->
                        FilterChip(typed == null && (picked ?: Payments.defaultKey(options)) == o.key, { picked = o.key; typed = null },
                            label = { Text("${o.label} ${kc(o.amount)}") })
                    }
                }
                OutlinedTextField(typed ?: amount.toString(), { typed = it.filter(Char::isDigit).take(7) }, label = { Text("Částka (Kč)") }, singleLine = true)
                Text(kc(amount), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(method == "CASH", { method = "CASH" }, label = { Text("Hotově") })
                    FilterChip(method == "QR", { method = "QR" }, label = { Text("QR / převod") })
                }
                if (method == "CASH") {
                    OutlinedTextField(received, { received = it.filter(Char::isDigit).take(7) }, label = { Text("Přijato (pro výpočet vrácení)") }, singleLine = true)
                    received.toLongOrNull()?.let { r ->
                        val ch = Payments.change(r, amount)
                        Text(if (ch >= 0) "Vrátit: ${kc(ch)}" else "Chybí: ${kc(-ch)}", color = if (ch >= 0) C.Green else C.Red, fontWeight = FontWeight.Bold)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { leave = !leave }) {
                    Checkbox(leave, { leave = it }); Text("Odchází")
                }
            }
        },
        confirmButton = { Button({ onPay(amount, method, leave) }, enabled = amount > 0,
            colors = ButtonDefaults.buttonColors(containerColor = C.Green, contentColor = C.Ink)) { Text("Zaplatit ${kc(amount)}") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } })
}
