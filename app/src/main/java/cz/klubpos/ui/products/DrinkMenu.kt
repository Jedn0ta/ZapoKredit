package cz.klubpos.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.klubpos.data.db.entity.Product
import cz.klubpos.data.repo.ClubRepository
import cz.klubpos.domain.Credits
import cz.klubpos.domain.Kind
import cz.klubpos.domain.kindEnum
import cz.klubpos.domain.menuTitle
import cz.klubpos.domain.servingLabel
import cz.klubpos.domain.volumeLabel
import cz.klubpos.ui.components.rememberMinuteClock
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.inventory.KegGauge
import cz.klubpos.ui.owner.OwnerUnlockDialog
import cz.klubpos.ui.owner.rememberOwnerUnlocked
import cz.klubpos.ui.theme.Display
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.ui.theme.glass
import cz.klubpos.ui.tour.tourTarget
import kotlinx.coroutines.launch

/** A drink on the card with its serving sizes. */
private data class Drink(val kind: Kind, val name: String, val servings: List<Product>)

private fun buildMenu(products: List<Product>): Map<Kind, List<Drink>> =
    products.filter { it.active }
        .groupBy { it.kindEnum to it.menuTitle }
        .map { (k, list) -> Drink(k.first, k.second, list.sortedWith(compareBy({ it.pourMl }, { it.price }))) }
        .groupBy { it.kind }
        .mapValues { (_, l) -> l.sortedBy { it.name.lowercase() } }

/**
 * Nabídka = a real printed-style drink list: section heading, then one line per serving
 * "0,5 l   Pilsner Urquell ........ 55". No emoji, no cards.
 *  Edit (unlock → "Upravit"): the same list; a drink opens inline with its sizes (ml / name / price).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrinkMenuScreen() {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val products by repo.allProducts().collectAsState(initial = emptyList())
    val kegs by repo.activeKegs().collectAsState(initial = emptyList())
    val menu = remember(products) { buildMenu(products) }
    val unlocked = rememberOwnerUnlocked()
    var editing by remember { mutableStateOf(false) }
    var askUnlock by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }      // "KIND|name" being edited, "KIND|" = new
    var showIo by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf<Product?>(null) }
    val now = rememberMinuteClock()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("menu_layout", android.content.Context.MODE_PRIVATE) }
    var cols by remember { mutableStateOf(prefs.getInt("cols", 2).coerceIn(1, 3)) }
    var scale by remember { mutableStateOf(prefs.getFloat("scale", 1f)) }
    var layoutRaw by remember { mutableStateOf(prefs.getString("layout$cols", "") ?: "") }
    LaunchedEffect(cols) { layoutRaw = prefs.getString("layout$cols", "") ?: "" }
    // column lists of section codes, every Kind placed exactly once
    val layout: List<List<Kind>> = remember(cols, layoutRaw) {
        val parsed = layoutRaw.split("|").map { c -> c.split(",").mapNotNull { code -> Kind.entries.firstOrNull { it.code == code } } }
        val res = List(cols) { i -> parsed.getOrNull(i).orEmpty().toMutableList() }
        // extra stored columns (when there were more) fold into the last one
        parsed.drop(cols).flatten().forEach { res.last().add(it) }
        Kind.entries.filter { k -> res.none { k in it } }.forEach { k -> res.minBy { it.size }.add(k) }
        res.map { it.distinct() }
    }
    fun saveLayout(l: List<List<Kind>>) {
        layoutRaw = l.joinToString("|") { c -> c.joinToString(",") { it.code } }
        prefs.edit().putString("layout$cols", layoutRaw).apply()
    }
    fun move(k: Kind, dCol: Int, dRow: Int) {
        val l = layout.map { it.toMutableList() }
        val c = l.indexOfFirst { k in it }; val r = l[c].indexOf(k)
        if (dCol != 0) { val nc = (c + dCol).coerceIn(0, l.lastIndex); if (nc == c) return; l[c].removeAt(r); l[nc].add(r.coerceAtMost(l[nc].size), k) }
        else { val nr = r + dRow; if (nr !in l[c].indices) return; l[c].removeAt(r); l[c].add(nr, k) }
        saveLayout(l)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(24.dp, 14.dp, 12.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Nápojový lístek", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // columns 1/2/3
            (1..3).forEach { n ->
                Text("$n", Modifier.clip(RoundedCornerShape(8.dp)).clickable { cols = n; prefs.edit().putInt("cols", n).apply() }
                    .padding(horizontal = 9.dp, vertical = 6.dp), fontWeight = if (cols == n) FontWeight.Bold else null,
                    color = if (cols == n) KlubColors.Gold else KlubColors.Muted)
            }
            Spacer(Modifier.width(6.dp))
            Text("A−", Modifier.clip(RoundedCornerShape(8.dp)).clickable { scale = (scale - 0.1f).coerceAtLeast(0.7f); prefs.edit().putFloat("scale", scale).apply() }
                .padding(horizontal = 8.dp, vertical = 6.dp), fontSize = 13.sp, color = KlubColors.Muted)
            Text("A+", Modifier.clip(RoundedCornerShape(8.dp)).clickable { scale = (scale + 0.1f).coerceAtMost(1.8f); prefs.edit().putFloat("scale", scale).apply() }
                .padding(horizontal = 8.dp, vertical = 6.dp), fontSize = 17.sp, color = KlubColors.Muted)
            if (editing) IconButton({ showIo = true }) { Icon(Icons.Default.UploadFile, "Import / vzor z Excelu", tint = KlubColors.Muted) }
            TextButton({ if (editing) { editing = false; open = null } else if (unlocked) editing = true else askUnlock = true },
                Modifier.tourTarget("menu_edit")) {
                Icon(if (editing) Icons.Default.Lock else Icons.Default.Edit, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (editing) "Hotovo" else "Upravit")
            }
        }

        val sections = Kind.entries.filter { k -> editing || menu[k].orEmpty().any { d -> d.servings.any { it.price > 0 } } }
        if (sections.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Lístek je prázdný. Klepni na Upravit.", color = KlubColors.Muted)
        }
        val density = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
            androidx.compose.ui.unit.Density(density.density, density.fontScale * scale)) {
        Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp, 4.dp, 24.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp)) {
          layout.forEachIndexed { ci, column ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
              column.filter { it in sections }.forEach { k ->
                val drinks = menu[k].orEmpty().filter { d -> editing || d.servings.any { it.price > 0 } }
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(k.label.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                            color = KlubColors.Gold)
                        if (editing) {
                            val ri = column.indexOf(k)
                            listOf(Triple("←", -1, 0) to (ci > 0), Triple("↑", 0, -1) to (ri > 0),
                                Triple("↓", 0, 1) to (ri < column.lastIndex), Triple("→", 1, 0) to (ci < layout.lastIndex)).forEach { (t, en) ->
                                Text(t.first, Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = en) { move(k, t.second, t.third) }
                                    .padding(horizontal = 7.dp, vertical = 2.dp), color = if (en) KlubColors.Muted else KlubColors.Muted.copy(alpha = 0.25f))
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 6.dp), color = KlubColors.Gold.copy(alpha = 0.35f))
                    drinks.forEach { d ->
                        val key = "${k.code}|${d.name}"
                        if (editing && open == key)
                            DrinkInlineEditor(k, d, onClose = { open = null }, onAdvanced = { advanced = it })
                        else MenuLines(d, editing, now, kegs.firstOrNull { it.kegGroup == d.name }) { open = key }
                    }
                    if (editing) {
                        if (open == "${k.code}|") DrinkInlineEditor(k, null, onClose = { open = null }, onAdvanced = { advanced = it })
                        else Text("+ přidat", Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = "${k.code}|" }
                            .padding(vertical = 8.dp, horizontal = 2.dp), color = KlubColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
              }
            }
          }
        }
        }
    }

    if (askUnlock) OwnerUnlockDialog(action = "Upravit nápojový lístek", onDismiss = { askUnlock = false }, onUnlocked = { askUnlock = false; editing = true })
    if (showIo) ImportExportSheet(onDismiss = { showIo = false })
    advanced?.let { p ->
        var fileFor by remember { mutableStateOf<Product?>(null) }
        var qrFor by remember { mutableStateOf<Product?>(null) }
        val pickFile = cz.klubpos.ui.components.rememberImagePicker { bytes -> fileFor?.let { repo.setProductImage(it.id, bytes) } }
        ProductDialog(p, onDismiss = { advanced = null }, onImage = { qrFor = p }, onImageFile = { fileFor = p; pickFile() }) { u ->
            scope.launch { repo.saveProduct(u) }; advanced = null
        }
        qrFor?.let { q -> cz.klubpos.ui.components.QrUploadDialog("Obrázek: ${q.menuTitle}", onImage = { repo.setProductImage(q.id, it) }) { qrFor = null } }
    }
}

/** One line per serving: volume · name (+ serving name) · dotted leader · price. */
@Composable
private fun MenuLines(d: Drink, editing: Boolean, now: java.time.LocalDateTime, keg: cz.klubpos.data.db.dao.KegStatus?, onEdit: () -> Unit) {
    val shown = if (editing) d.servings else d.servings.filter { it.price > 0 }
    val dim = d.kind == Kind.DRAUGHT && keg == null && !editing
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(enabled = editing, onClick = onEdit)) {
        shown.forEachIndexed { i, p ->
            val cur = p.currentPrice(now)
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Bottom) {
                Text(if (p.pourMl > 0) volumeLabel(p.pourMl) else "", Modifier.width(56.dp),
                    style = MaterialTheme.typography.bodyMedium, color = KlubColors.Muted)
                val title = buildString {
                    append(if (i == 0 || shown.size == 1) d.name else "")
                    p.servingName?.takeIf { it.isNotBlank() }?.let { if (isNotEmpty()) append("  "); append(it) }
                }
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (i == 0) FontWeight.Medium else FontWeight.Normal),
                    color = if (dim) KlubColors.Muted else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (i == 0 && dim) Text("  nenaraženo", style = MaterialTheme.typography.labelSmall, color = KlubColors.Muted)
                androidx.compose.foundation.Canvas(Modifier.weight(1f).height(10.dp).padding(horizontal = 6.dp)) {
                    var x = 0f; val step = 5.dp.toPx(); val r = 0.8.dp.toPx()
                    while (x < size.width) { drawCircle(KlubColors.Muted.copy(alpha = 0.6f), r, androidx.compose.ui.geometry.Offset(x, size.height - r)); x += step }
                }
                if (cur != p.price) {
                    Text("${p.price}", style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted, textDecoration = TextDecoration.LineThrough)
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (p.price > 0) Credits.format(cur) else "—", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (cur != p.price) KlubColors.Gold else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Inline editor inside the list: name, sizes (ml / name / price), save. Minimal – just a list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrinkInlineEditor(kind: Kind, drink: Drink?, onClose: () -> Unit, onAdvanced: (Product) -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(drink?.name ?: "") }
    var kegSize by remember { mutableStateOf<Double?>(null) }
    val rows = remember {
        mutableStateListOf<EditServing>().apply {
            drink?.servings?.forEach { add(EditServing(it.id, if (it.pourMl > 0) it.pourMl.toString() else "", it.servingName ?: "", it.price.toString())) }
            if (isEmpty()) add(EditServing(0, kind.volumes.lastOrNull()?.toString() ?: "", "", ""))
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val kegStock by repo.kegStock().collectAsState(initial = emptyList())
    LaunchedEffect(drink?.name, kegStock) { if (kegSize == null) kegSize = kegStock.firstOrNull { it.kegGroup == drink?.name }?.sizeLiters ?: 50.0 }
    val valid = name.isNotBlank() && rows.any { (it.price.toLongOrNull() ?: 0) > 0 }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
    val small = MaterialTheme.typography.bodyMedium

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(10.dp))
        .background(KlubColors.SurfaceHigh.copy(alpha = 0.5f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MiniField(name, { name = it }, "název, např. ${kind.example}", Modifier.fillMaxWidth(), MaterialTheme.typography.bodyLarge)
        rows.forEachIndexed { i, r ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniField(r.ml, { v -> rows[i] = r.copy(ml = v.filter(Char::isDigit).take(4)) }, "ml", Modifier.width(60.dp), small, num)
                MiniField(r.name, { v -> rows[i] = r.copy(name = v) }, "název míry", Modifier.weight(1f), small)
                MiniField(r.price, { v -> rows[i] = r.copy(price = v.filter(Char::isDigit).take(6)) }, "cena", Modifier.width(64.dp), small, num)
                Text("×", Modifier.clip(RoundedCornerShape(6.dp)).clickable { rows.removeAt(i) }.padding(6.dp), color = KlubColors.Muted)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            kind.volumes.filter { v -> rows.none { it.ml == v.toString() } }.forEach { v ->
                Text("+ ${volumeLabel(v)}", Modifier.clickable { rows += EditServing(0, v.toString(), "", "") }.padding(vertical = 4.dp), color = KlubColors.Muted, style = small)
            }
            Text("+ jiná míra", Modifier.clickable { rows += EditServing(0, "", "", "") }.padding(vertical = 4.dp), color = KlubColors.Muted, style = small)
        }
        if (kind == Kind.DRAUGHT) Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("sud", color = KlubColors.Muted, style = small)
            listOf(15.0, 20.0, 30.0, 50.0).forEach { s ->
                Text("${s.toInt()} l", Modifier.clickable { kegSize = s }.padding(vertical = 4.dp), style = small,
                    color = if (kegSize == s) KlubColors.Gold else KlubColors.Muted, fontWeight = if (kegSize == s) FontWeight.Bold else null)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (drink != null) TextButton({ confirmDelete = true }) { Text("Smazat", color = KlubColors.OldDebt) }
            if (drink != null) TextButton({ scope.launch { drink.servings.firstOrNull()?.let { repo.servingsOf(drink.name).firstOrNull()?.let(onAdvanced) } } }) {
                Text("Více…", color = KlubColors.Muted)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClose) { Text("Zrušit") }
            TextButton(enabled = valid, onClick = {
                val servings = rows.mapNotNull { r ->
                    val price = r.price.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                    ClubRepository.Serving(r.id, r.name.ifBlank { null }, r.ml.toIntOrNull() ?: 0, price)
                }
                scope.launch { repo.saveDrink(drink?.name, kind, name, servings, if (kind == Kind.DRAUGHT) kegSize else null) }
                onClose()
            }) { Text("Uložit", fontWeight = FontWeight.Bold) }
        }
    }
    if (confirmDelete && drink != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Smazat ${drink.name}?") },
        text = { Text("Zmizí z lístku i z objednávek. Historie prodejů zůstane.") },
        confirmButton = { TextButton({ scope.launch { repo.deleteDrink(drink.name) }; confirmDelete = false; onClose() }) { Text("Smazat", color = KlubColors.OldDebt) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Zpět") } },
    )
}

@Composable
private fun MiniField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier,
                      style: androidx.compose.ui.text.TextStyle, kb: KeyboardOptions = KeyboardOptions.Default) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 8.dp)) {
        if (value.isEmpty()) Text(hint, style = style, color = KlubColors.Muted.copy(alpha = 0.7f), maxLines = 1)
        androidx.compose.foundation.text.BasicTextField(value, onChange, singleLine = true, keyboardOptions = kb,
            textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(KlubColors.Gold), modifier = Modifier.fillMaxWidth())
    }
}

/** Editable row of the drink editor. */
private data class EditServing(val id: Long, val ml: String, val name: String, val price: String)

