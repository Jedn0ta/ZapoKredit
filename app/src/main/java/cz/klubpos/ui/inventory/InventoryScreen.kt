package cz.klubpos.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.klubpos.data.db.entity.StockItem
import cz.klubpos.domain.Kind
import cz.klubpos.domain.displayName
import cz.klubpos.domain.kindEnum
import cz.klubpos.domain.volumeLabel
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.member.Keypad
import cz.klubpos.ui.member.fmtQty
import cz.klubpos.ui.members.Segmented
import cz.klubpos.ui.theme.Display
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.ui.theme.glass
import cz.klubpos.ui.tour.tourTarget
import kotlinx.coroutines.launch

/** "3 lahve", "1,4 lahve", "12 ks" */
fun qtyLabel(q: Double, unit: String): String {
    val n = if (q % 1.0 == 0.0) q.toLong().toString() else "%.1f".format(java.util.Locale("cs", "CZ"), q)
    return "$n $unit"
}

/**
 * Sklad:
 *  Přehled  – kegs, then stock items by location with a bar towards the target stock, "+ Příjem".
 *  Inventura – count everything location by location (open bottles in tenths), save once.
 *  Objednat – fills the shopping list up to target stock, grouped by supplier.
 */
@Composable
fun InventoryScreen() {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val items by repo.stockItems().collectAsState(initial = emptyList())
    val suppliers by repo.suppliers().collectAsState(initial = emptyList())
    var tab by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<StockItem?>(null) }
    var receiveFor by remember { mutableStateOf<StockItem?>(null) }
    val below = items.count { it.target > 0 && it.qty < it.target }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(20.dp, 12.dp, 12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sklad", style = MaterialTheme.typography.headlineSmall)
                    Text(if (below > 0) "$below pod cílovým stavem" else "na cílovém stavu",
                        style = MaterialTheme.typography.labelMedium, color = if (below > 0) KlubColors.OpenTab else KlubColors.Muted)
                }
                Segmented(listOf("Přehled", "Inventura"), tab) { tab = it }
            }
            if (tab == 0) Overview(items, suppliers,
                onEdit = { editing = it }, onNew = { editing = StockItem(name = "") }, onReceive = { receiveFor = it },
                onOrder = {
                    scope.launch {
                        val n = repo.generateShoppingList()
                        snackbar.showSnackbar(if (n > 0) "Do nákupu přidáno: $n (doplní na cílový stav)" else "Vše je na cílovém stavu")
                    }
                })
            else CountScreen(items) { counts ->
                scope.launch { repo.saveCount(counts); snackbar.showSnackbar("Inventura uložena (${counts.size} položek)") }
                tab = 0
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    editing?.let { it0 -> StockItemEditor(it0, items, suppliers, onDismiss = { editing = null }) }
    receiveFor?.let { si ->
        KeypadDialog("Příjem · ${si.name}", "", si.unitLabel, onDismiss = { receiveFor = null }) { q ->
            scope.launch { repo.receiveStockItem(si.id, q) }; receiveFor = null
        }
    }
}

@Composable
private fun Overview(
    items: List<StockItem>, suppliers: List<cz.klubpos.data.db.entity.Supplier>,
    onEdit: (StockItem) -> Unit, onNew: () -> Unit, onReceive: (StockItem) -> Unit, onOrder: () -> Unit,
) {
    val byLoc = items.groupBy { it.location.ifBlank { "Sklad" } }
    LazyVerticalGrid(GridCells.Adaptive(330.dp), contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 96.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item(key = "kegs", span = { GridItemSpan(maxLineSpan) }) { KegsSection() }
        item(key = "actions", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onOrder, Modifier.tourTarget("stock_order")) { Text("Objednat do cílového stavu", color = KlubColors.Gold) }
                Spacer(Modifier.weight(1f))
                TextButton(onNew) { Text("+ Nová zásoba", color = KlubColors.Muted) }
            }
        }
        if (items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Zatím žádné zásoby. Přidej lahve, limonády ve skle, chipsy… a propoj je s nápoji z lístku – prodej je pak sám odečte.",
                Modifier.padding(8.dp), color = KlubColors.Muted)
        }
        byLoc.forEach { (loc, list) ->
            item(key = "loc-$loc", span = { GridItemSpan(maxLineSpan) }) {
                Text(loc.uppercase(), Modifier.padding(start = 4.dp, top = 12.dp), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                    color = KlubColors.Muted)
            }
            items(list, key = { it.id }) { si ->
                StockCard(si, suppliers.firstOrNull { it.id == si.supplierId }?.name, Modifier.tourTarget("stock_row"),
                    onClick = { onEdit(si) }, onReceive = { onReceive(si) })
            }
        }
    }
}

@Composable
private fun StockCard(si: StockItem, supplier: String?, modifier: Modifier, onClick: () -> Unit, onReceive: () -> Unit) {
    val frac = if (si.target > 0) (si.qty / si.target).toFloat().coerceIn(0f, 1f) else 1f
    val low = si.target > 0 && si.qty < si.target
    val empty = si.qty <= 0.0
    val color = when { empty -> KlubColors.OldDebt; frac < 0.5f -> KlubColors.OpenTab; low -> KlubColors.Gold; else -> KlubColors.PaidToday }
    Column(modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(si.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(if (si.unitType == "BOTTLE" && si.containerMl > 0) volumeLabel(si.containerMl) else null,
                    if (si.target > 0) "cíl ${fmtQty(si.target)}" else null, supplier).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = KlubColors.Muted, maxLines = 1)
            }
            Text(qtyLabel(si.qty, si.unitLabel), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = color)
            Text("+", Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onReceive)
                .padding(horizontal = 10.dp, vertical = 2.dp), fontSize = 20.sp, color = KlubColors.Muted)
        }
        if (si.target > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(KlubColors.Hairline)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(color))
        }
    }
}

/** Inventura: whole containers with −/+, open bottle in tenths with a slider. Saved all at once. */
@Composable
private fun CountScreen(items: List<StockItem>, onSave: (Map<Long, Double>) -> Unit) {
    val full = remember(items) { mutableStateMapOf<Long, Int>().apply { items.forEach { put(it.id, kotlin.math.floor(it.qty).toInt()) } } }
    val open = remember(items) { mutableStateMapOf<Long, Int>().apply { items.forEach { put(it.id, Math.round((it.qty - kotlin.math.floor(it.qty)) * 10).toInt().coerceIn(0, 9)) } } }
    val byLoc = items.groupBy { it.location.ifBlank { "Sklad" } }
    Column(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Adaptive(360.dp), Modifier.weight(1f), contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 16.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Spočítej, co opravdu máš. Otevřenou lahev nastav posuvníkem (desetiny). Ulož až na konci.",
                    Modifier.padding(6.dp), style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted)
            }
            byLoc.forEach { (loc, list) ->
                item(key = "c-loc-$loc", span = { GridItemSpan(maxLineSpan) }) {
                    Text(loc.uppercase(), Modifier.padding(start = 4.dp, top = 12.dp), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                        color = KlubColors.Muted)
                }
                items(list, key = { "c${it.id}" }) { si ->
                    val f = full[si.id] ?: 0
                    val o = open[si.id] ?: 0
                    val counted = f + o / 10.0
                    val changed = kotlin.math.abs(counted - si.qty) > 0.01
                    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(si.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("bylo ${qtyLabel(si.qty, si.unitLabel)}", style = MaterialTheme.typography.labelSmall, color = KlubColors.Muted)
                            }
                            CountButton("−") { full[si.id] = (f - 1).coerceAtLeast(0) }
                            Text("$f", Modifier.width(40.dp), style = MaterialTheme.typography.titleMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = if (changed) KlubColors.Gold else MaterialTheme.colorScheme.onSurface)
                            CountButton("+") { full[si.id] = f + 1 }
                        }
                        if (si.unitType == "BOTTLE") Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("otevřená", Modifier.width(72.dp), style = MaterialTheme.typography.labelSmall, color = KlubColors.Muted)
                            Slider(o.toFloat(), { open[si.id] = Math.round(it) }, Modifier.weight(1f).height(28.dp), valueRange = 0f..9f, steps = 8)
                            Text(if (o == 0) "—" else "$o/10", Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall, color = KlubColors.Muted)
                        }
                        HorizontalDivider(Modifier.padding(top = 4.dp), color = KlubColors.Hairline)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(12.dp).height(48.dp).clip(RoundedCornerShape(12.dp)).background(KlubColors.SurfaceHigh)
            .clickable {
                onSave(items.associate { it.id to ((full[it.id] ?: 0) + (open[it.id] ?: 0) / 10.0) }
                    .filter { (id, q) -> kotlin.math.abs(q - (items.first { it.id == id }.qty)) > 0.01 })
            }, contentAlignment = Alignment.Center) {
            Text("Uložit inventuru", color = KlubColors.Gold, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CountButton(label: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.titleLarge, color = KlubColors.Muted)
    }
}

/** Create / edit a stock item: type, bottle size, location, target, supplier and which drinks take from it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StockItemEditor(si: StockItem, all: List<StockItem>, suppliers: List<cz.klubpos.data.db.entity.Supplier>, onDismiss: () -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val products by repo.activeProducts().collectAsState(initial = emptyList())
    var name by remember { mutableStateOf(si.name) }
    var bottle by remember { mutableStateOf(si.unitType == "BOTTLE") }
    var ml by remember { mutableStateOf(if (si.containerMl > 0) si.containerMl.toString() else "700") }
    var unit by remember { mutableStateOf(si.unitLabel) }
    var loc by remember { mutableStateOf(si.location) }
    var target by remember { mutableStateOf(if (si.target > 0) fmtQty(si.target) else "") }
    var qty by remember { mutableStateOf(fmtQty(si.qty)) }
    var supplierId by remember { mutableStateOf(si.supplierId) }
    val linked = remember { mutableStateMapOf<Long, Boolean>() }
    var loaded by remember { mutableStateOf(si.id == 0L) }
    androidx.compose.runtime.LaunchedEffect(si.id) {
        if (si.id != 0L) { repo.linkedProducts(si.id).forEach { linked[it.id] = true }; loaded = true }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val locations = (listOf("Sklad", "Bar", "Lednice", "Sklep") + all.map { it.location }).distinct()
    val num = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    fun d(s: String) = s.replace(',', '.').toDoubleOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (si.id == 0L) "Nová zásoba" else si.name, style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(name, { name = it }, label = { Text("Název (např. Božkov rum 0,7 l)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text("Co to je", style = MaterialTheme.typography.labelLarge, color = KlubColors.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(bottle, { bottle = true; unit = "lahev" }, label = { Text("Lahev – rozlévá se") })
                FilterChip(!bottle, { bottle = false; if (unit == "lahev") unit = "ks" }, label = { Text("Kusy – prodává se celé") })
            }
            if (bottle) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(500, 700, 750, 1000).forEach { v -> FilterChip(ml == v.toString(), { ml = v.toString() }, label = { Text(volumeLabel(v)) }) }
                OutlinedTextField(ml, { ml = it.filter(Char::isDigit).take(5) }, label = { Text("ml") }, singleLine = true,
                    modifier = Modifier.width(100.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            } else OutlinedTextField(unit, { unit = it }, label = { Text("Jednotka (ks, balení…)") }, singleLine = true)

            Text("Kde je", style = MaterialTheme.typography.labelLarge, color = KlubColors.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                locations.forEach { l -> FilterChip(loc == l, { loc = l }, label = { Text(l) }) }
                OutlinedTextField(if (loc in locations) "" else loc, { loc = it }, label = { Text("jiné místo") }, singleLine = true, modifier = Modifier.width(150.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(qty, { qty = it }, label = { Text("Teď mám") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = num,
                    supportingText = { Text(unit) })
                OutlinedTextField(target, { target = it }, label = { Text("Cílový stav") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = num,
                    supportingText = { Text("objednávka doplní sem") })
            }

            Text("Dodavatel", style = MaterialTheme.typography.labelLarge, color = KlubColors.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(supplierId == null, { supplierId = null }, label = { Text("—") })
                suppliers.forEach { s -> FilterChip(supplierId == s.id, { supplierId = s.id }, label = { Text(s.name) }) }
            }
            if (suppliers.isEmpty()) Text("Dodavatele přidáš v Nastavení → Dodavatelé.", style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted)

            HorizontalDivider(color = KlubColors.Hairline)
            Text("Při prodeji odečíst z této zásoby", style = MaterialTheme.typography.labelLarge, color = KlubColors.Muted)
            val candidates = products.filter { it.kindEnum != Kind.DRAUGHT }.sortedBy { it.displayName.lowercase() }
            if (candidates.isEmpty()) Text("V lístku zatím nic není.", color = KlubColors.Muted)
            candidates.forEach { p ->
                val on = linked[p.id] == true
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { linked[p.id] = !on }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(on, { linked[p.id] = it })
                    Text(p.displayName, Modifier.weight(1f))
                    if (on && bottle && (ml.toIntOrNull() ?: 0) > 0 && p.pourMl > 0)
                        Text("${p.pourMl}/${ml} lahve", style = MaterialTheme.typography.labelSmall, color = KlubColors.Muted)
                }
            }

            HorizontalDivider(color = KlubColors.Hairline)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (si.id != 0L) TextButton({ confirmDelete = true }) { Text("Smazat", color = KlubColors.OldDebt) }
                Spacer(Modifier.weight(1f))
                TextButton(onDismiss) { Text("Zrušit") }
                Button(enabled = name.isNotBlank() && loaded, onClick = {
                    val item = si.copy(name = name.trim(), unitType = if (bottle) "BOTTLE" else "PIECE",
                        unitLabel = if (bottle) "lahev" else unit.ifBlank { "ks" }, containerMl = if (bottle) ml.toIntOrNull() ?: 0 else 0,
                        location = loc.ifBlank { "Sklad" }, qty = d(qty) ?: si.qty, target = d(target) ?: 0.0, supplierId = supplierId)
                    scope.launch { repo.saveStockItem(item, linked.filterValues { it }.keys.toList()) }
                    onDismiss()
                }) { Text("Uložit") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Smazat ${si.name}?") }, text = { Text("Nápoje z lístku zůstanou, jen se přestanou odečítat.") },
        confirmButton = { TextButton({ scope.launch { repo.deleteStockItem(si.id) }; confirmDelete = false; onDismiss() }) { Text("Smazat", color = KlubColors.OldDebt) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Zpět") } })
}

/** Numeric dialog with the POS keypad. */
@Composable
fun KeypadDialog(title: String, initial: String, unit: String, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var fresh by remember { mutableStateOf(true) }   // first key replaces the prefilled value
    val v = text.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${text.ifEmpty { "0" }} $unit", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Keypad(Modifier.fillMaxWidth(),
                    onDigit = { d -> text = if (fresh) d.trimStart('0').ifEmpty { "0" } else (text + d).trimStart('0').ifEmpty { "0" }; fresh = false },
                    onBack = { text = text.dropLast(1); fresh = false },
                    onClear = { text = ""; fresh = false })
            }
        },
        confirmButton = { TextButton(enabled = v != null, onClick = { onConfirm(v!!) }) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}
