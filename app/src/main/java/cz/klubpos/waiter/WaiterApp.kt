package cz.klubpos.waiter

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import cz.klubpos.core.crypto.DatabaseKeyManager
import cz.klubpos.net.Lan
import cz.klubpos.net.LanApi
import cz.klubpos.net.LanClient
import cz.klubpos.net.LanException
import cz.klubpos.net.MemberDto
import cz.klubpos.net.OrderDto
import cz.klubpos.net.OrderItemDto
import cz.klubpos.net.ProductDto
import cz.klubpos.net.Snapshot
import cz.klubpos.ui.member.fmtQty
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.ui.theme.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Saved connection of this phone (encrypted). */
private class WaiterStore(c: Context) {
    private val p = EncryptedSharedPreferences.create(c, "waiter", DatabaseKeyManager.masterKey(c),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    var code: Lan.Code?
        get() = p.getString("code", null)?.let { Lan.Code.parse(it) }
        set(v) { p.edit().putString("code", v?.text()).apply() }
    var name: String
        get() = p.getString("name", "") ?: ""
        set(v) { p.edit().putString("name", v).apply() }
}

/** Target of an order: a member or a table number. */
private data class Target(val memberId: Long?, val table: Int?, val label: String)

private class CartItem(val p: ProductDto, qty: Int) { var qty by mutableStateOf(qty) }

/** Waiter's phone: pick member/table → drinks → send to the bar. */
@Composable
fun WaiterApp() {
    val ctx = LocalContext.current
    val store = remember { WaiterStore(ctx) }
    var code by remember { mutableStateOf(store.code) }
    var name by remember { mutableStateOf(store.name) }
    val c = code
    if (c == null || name.isBlank()) PairScreen(name) { newCode, newName ->
        store.code = newCode; store.name = newName; code = newCode; name = newName
    } else WaiterMain(c, name, onCodeChanged = { store.code = it; code = it }, onLogout = { store.code = null; code = null })
}

@Composable
private fun PairScreen(initialName: String, onDone: (Lan.Code, String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var manual by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { r ->
        val code = r.contents?.let { Lan.Code.parse(it) }
        if (code == null) { if (r.contents != null) error = "Tohle není QR z pokladny ZapoKredit" }
        else if (name.isBlank()) error = "Nejdřív napiš své jméno"
        else onDone(code, name.trim())
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
        Text("ZapoKredit Číšník", style = MaterialTheme.typography.headlineMedium)
        Text("Připoj se k pokladně: na tabletu otevři Nastavení → Číšníci a PC a naskenuj QR kód.", color = KlubColors.Muted)
        OutlinedTextField(name, { name = it; error = null }, label = { Text("Tvoje jméno (uvidí ho bar)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button({ scanner.launch(ScanOptions().setPrompt("QR z pokladny").setBeepEnabled(false).setOrientationLocked(false)
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)) }, Modifier.fillMaxWidth()) { Text("Naskenovat QR pokladny") }
        Text("…nebo vlož text kódu (ZK1;adresa;port;kód):", style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted)
        OutlinedTextField(manual, { manual = it; error = null }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (manual.isNotBlank()) OutlinedButton({
            val code = Lan.Code.parse(manual)
            if (code == null) error = "Kód nejde přečíst" else if (name.isBlank()) error = "Napiš své jméno" else onDone(code, name.trim())
        }) { Text("Připojit") }
        error?.let { Text(it, color = KlubColors.OldDebt) }
    }
}

@Composable
private fun WaiterMain(code: Lan.Code, name: String, onCodeChanged: (Lan.Code) -> Unit, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    val api = remember(code, name) { LanApi(LanClient(code, name)) }
    var snap by remember { mutableStateOf<Snapshot?>(null) }
    var orders by remember { mutableStateOf<List<OrderDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }
    var target by remember { mutableStateOf<Target?>(null) }
    val cart = remember { mutableStateListOf<CartItem>() }
    var note by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            snap = api.snapshot(); orders = api.orders().filter { it.waiter == name }; error = null
        } catch (e: LanException) {
            error = e.message
            // the register may have a new IP address: find it by its key on the network
            Lan.discover().firstOrNull { it.keyId == Lan.keyId(code.key) }?.let { f ->
                if (f.host != code.host) withContext(Dispatchers.Main) { onCodeChanged(code.copy(host = f.host, port = f.port)) }
            }
        }
    }
    LaunchedEffect(api) { while (true) { refresh(); delay(if (tab == 1) 4_000 else 10_000) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp, 8.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(snap?.club?.ifBlank { null } ?: "Pokladna", style = MaterialTheme.typography.titleLarge)
                    Text(error ?: "připojeno · $name", style = MaterialTheme.typography.labelSmall,
                        color = if (error != null) KlubColors.OldDebt else KlubColors.Muted, maxLines = 2)
                }
                TextButton(onLogout) { Text("Odpojit", color = KlubColors.Muted) }
            }
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Objednat", tab == 0) { tab = 0 }
                val open = orders.count { it.status == "NEW" || it.status == "PREPARING" }
                Chip("Moje objednávky" + if (open > 0) " ($open)" else "", tab == 1) { tab = 1; scope.launch { refresh() } }
            }
            Spacer(Modifier.size(8.dp))
            val s = snap
            when {
                s == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error ?: "Načítám…", color = KlubColors.Muted) }
                tab == 1 -> MyOrders(orders)
                target == null -> TargetPicker(s) { target = it }
                else -> Composer(s, target!!, cart, note, { note = it }, sending, onChangeTarget = { target = null }) {
                    val t = target!!
                    sending = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                api.order(t.memberId, t.table, cart.map { OrderItemDto(it.p.id, it.p.name, it.qty.toDouble(), it.p.price) },
                                    note.ifBlank { null }, prep = true)
                            }
                            snackbar.showSnackbar("Odesláno na bar: ${t.label}")
                            cart.clear(); note = ""; target = null; refresh()
                        } catch (e: LanException) { snackbar.showSnackbar(e.message ?: "Chyba") }
                        sending = false
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Chip(label: String, sel: Boolean, onClick: () -> Unit) {
    Text(label, Modifier.clip(RoundedCornerShape(16.dp)).then(if (sel) Modifier.background(KlubColors.GoldBrush) else Modifier.glass(RoundedCornerShape(16.dp)))
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (sel) KlubColors.Ink else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun TargetPicker(s: Snapshot, onPick: (Target) -> Unit) {
    var mode by remember { mutableStateOf(0) }
    var q by remember { mutableStateOf("") }
    var otherTable by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Členové", mode == 0) { mode = 0 }
            Chip("Stoly", mode == 1) { mode = 1 }
        }
        Spacer(Modifier.size(8.dp))
        if (mode == 0) {
            OutlinedTextField(q, { q = it }, placeholder = { Text("Hledat člena") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val list = s.members.filter { !it.isTable && (q.isBlank() || it.name.contains(q, true) || it.nickname?.contains(q, true) == true) }
                .sortedWith(compareByDescending<MemberDto> { it.inClub }.thenBy { it.label.lowercase() })
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(list, key = { it.id }) { m ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(Target(m.id, null, m.label)) }
                        .padding(vertical = 12.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (m.inClub) KlubColors.PaidToday else KlubColors.Hairline))
                        Text(m.label, Modifier.weight(1f).padding(start = 12.dp), fontSize = 18.sp)
                        if (m.todayUnpaid > 0) Text("${m.todayUnpaid} Kč", color = KlubColors.OpenTab)
                    }
                }
            }
        } else {
            val occupied = s.members.filter { it.isTable && it.inClub }.mapNotNull { it.name.removePrefix("Stůl ").toIntOrNull() }.toSet()
            LazyVerticalGrid(GridCells.Adaptive(72.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items((1..30).toList()) { n ->
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                        .then(if (n in occupied) Modifier.background(KlubColors.Gold.copy(alpha = 0.25f)).border(2.dp, KlubColors.Gold, RoundedCornerShape(16.dp))
                              else Modifier.glass(RoundedCornerShape(16.dp)))
                        .clickable { onPick(Target(null, n, "Stůl $n")) }, contentAlignment = Alignment.Center) {
                        Text("$n", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(otherTable, { otherTable = it.filter(Char::isDigit).take(3) }, label = { Text("Jiný stůl") }, singleLine = true,
                    modifier = Modifier.width(140.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                otherTable.toIntOrNull()?.let { n -> TextButton({ onPick(Target(null, n, "Stůl $n")) }) { Text("Vybrat") } }
            }
        }
    }
}

@Composable
private fun Composer(
    s: Snapshot, target: Target, cart: MutableList<CartItem>, note: String, onNote: (String) -> Unit, sending: Boolean,
    onChangeTarget: () -> Unit, onSend: () -> Unit,
) {
    val sections = remember(s.products) { s.products.sortedBy { it.sectionOrder }.map { it.section }.distinct() }
    var section by remember { mutableStateOf(sections.firstOrNull()) }
    fun count(p: ProductDto) = cart.firstOrNull { it.p.id == p.id }?.qty ?: 0
    fun add(p: ProductDto, d: Int) {
        val e = cart.firstOrNull { it.p.id == p.id }
        if (e == null) { if (d > 0) cart += CartItem(p, d) } else { e.qty += d; if (e.qty <= 0) cart.remove(e) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Pro: ", color = KlubColors.Muted)
            Text(target.label, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = KlubColors.Gold)
            TextButton(onChangeTarget) { Text("Změnit") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sections.forEach { sec -> Chip(sec, section == sec) { section = sec } }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
            items(s.products.filter { it.section == section }, key = { it.id }) { p ->
                val n = count(p)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { add(p, 1) }.padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, Modifier.weight(1f), fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${p.price} Kč", color = KlubColors.Muted, modifier = Modifier.padding(horizontal = 8.dp))
                    if (n > 0) {
                        Text("−", Modifier.clip(CircleShape).clickable { add(p, -1) }.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 22.sp)
                        Text("$n", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = KlubColors.Gold)
                    }
                    Text("+", Modifier.clip(CircleShape).clickable { add(p, 1) }.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 22.sp)
                }
            }
        }
        if (cart.isNotEmpty()) Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.heightIn(max = 160.dp)) {
                LazyColumn { items(cart.toList()) { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${e.qty}×", Modifier.width(36.dp), fontWeight = FontWeight.Bold)
                        Text(e.p.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("−", Modifier.clickable { add(e.p, -1) }.padding(horizontal = 10.dp), fontSize = 20.sp)
                        Text("+", Modifier.clickable { add(e.p, 1) }.padding(horizontal = 10.dp), fontSize = 20.sp)
                    }
                } }
            }
            OutlinedTextField(note, onNote, placeholder = { Text("Poznámka pro bar (nepovinné)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val total = cart.sumOf { it.p.price * it.qty }
            Button(onSend, Modifier.fillMaxWidth().padding(top = 2.dp), enabled = !sending,
                colors = ButtonDefaults.buttonColors(containerColor = KlubColors.Gold, contentColor = KlubColors.Ink)) {
                Text(if (sending) "Odesílám…" else "Odeslat na bar · ${cart.sumOf { it.qty }} ks · $total Kč", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MyOrders(orders: List<OrderDto>) {
    if (orders.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Dnes zatím žádné objednávky.", color = KlubColors.Muted) }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(orders.sortedByDescending { it.createdAt }, key = { it.id }) { o ->
            val color = when (o.status) { "NEW" -> KlubColors.OpenTab; "PREPARING" -> KlubColors.Gold; "DONE" -> KlubColors.PaidToday; else -> KlubColors.Muted }
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(o.target, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(OrderDto.statusLabel(o.status), color = color, fontWeight = FontWeight.Bold)
                }
                Text(o.items.joinToString(", ") { "${fmtQty(it.qty)}× ${it.name}" }, style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted)
            }
        }
    }
}
