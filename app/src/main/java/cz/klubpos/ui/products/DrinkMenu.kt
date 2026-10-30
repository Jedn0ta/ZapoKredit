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
 * Nabídka = the drink menu card.
 *  View: only what is filled in (sections without priced drinks are hidden).
 *  Edit (unlock → "Upravit", back with "Zamknout"): every section, add / edit / delete drinks.
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
    var editor by remember { mutableStateOf<Pair<Kind, Drink?>?>(null) }
    var showIo by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf<Product?>(null) }
    val now = rememberMinuteClock()

    Column(Modifier.fillMaxSize()) {
        // ---- header ----
        Row(Modifier.fillMaxWidth().padding(20.dp, 12.dp, 12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Nápojový lístek", style = MaterialTheme.typography.headlineSmall)
                Text(if (editing) "Úpravy – klepni na nápoj nebo přidej plusem" else "ceny v kreditech ${Credits.SYMBOL}",
                    style = MaterialTheme.typography.labelMedium, color = if (editing) KlubColors.Gold else KlubColors.Muted)
            }
            if (editing) IconButton({ showIo = true }) { Icon(Icons.Default.UploadFile, "Import / vzor z Excelu") }
            Row(Modifier.clip(RoundedCornerShape(16.dp))
                .then(if (editing) Modifier.background(KlubColors.GoldBrush) else Modifier.glass(RoundedCornerShape(16.dp)))
                .clickable { if (editing) editing = false else if (unlocked) editing = true else askUnlock = true }
                .tourTarget("menu_edit").padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(if (editing) Icons.Default.Lock else Icons.Default.Edit, null,
                    tint = if (editing) KlubColors.Ink else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (editing) "Zamknout" else "Upravit", fontWeight = FontWeight.SemiBold,
                    color = if (editing) KlubColors.Ink else MaterialTheme.colorScheme.onSurface)
            }
        }

        val sections = Kind.entries.filter { k ->
            if (editing) true else menu[k].orEmpty().any { d -> d.servings.any { it.price > 0 } }
        }
        if (sections.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Lístek je prázdný. Klepni na Upravit a přidej nápoje.", color = KlubColors.Muted)
        }
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(400.dp),
            contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp,
        ) {
            items(sections, key = { it.code }) { k ->
                val drinks = menu[k].orEmpty().filter { d -> editing || d.servings.any { it.price > 0 } }
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(k.emoji, fontSize = 24.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(k.label, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        if (editing) IconButton({ editor = k to null }) { Icon(Icons.Default.Add, "Přidat – ${k.label}", tint = KlubColors.Gold) }
                    }
                    if (drinks.isEmpty()) Text("Zatím nic – přidej plusem. Prázdná sekce se v lístku nezobrazí.",
                        style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted)
                    drinks.forEachIndexed { i, d ->
                        if (i > 0) HorizontalDivider(color = KlubColors.Hairline)
                        DrinkRow(d, editing, now, kegs.firstOrNull { it.kegGroup == d.name }) { editor = k to d }
                    }
                }
            }
        }
    }

    if (askUnlock) OwnerUnlockDialog(action = "Upravit nápojový lístek", onDismiss = { askUnlock = false }, onUnlocked = { askUnlock = false; editing = true })
    editor?.let { (k, d) ->
        DrinkEditorSheet(k, d, onDismiss = { editor = null }, onAdvanced = { p -> advanced = p; editor = null })
    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrinkRow(d: Drink, editing: Boolean, now: java.time.LocalDateTime, keg: cz.klubpos.data.db.dao.KegStatus?, onEdit: () -> Unit) {
    val shown = if (editing) d.servings else d.servings.filter { it.price > 0 }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled = editing, onClick = onEdit).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (d.kind == Kind.DRAUGHT) {
                KegGauge(if (keg == null) 0f else ((keg.sizeLiters - keg.pouredLiters) / keg.sizeLiters).toFloat(), Modifier.size(18.dp, 24.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(d.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (d.kind == Kind.DRAUGHT && keg == null) Text("nenaraženo", style = MaterialTheme.typography.labelSmall, color = KlubColors.Muted)
            if (editing) Icon(Icons.Default.Edit, "Upravit", tint = KlubColors.Muted, modifier = Modifier.padding(start = 8.dp).size(18.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { p ->
                val cur = p.currentPrice(now)
                Row(Modifier.clip(RoundedCornerShape(12.dp)).background(KlubColors.SurfaceHigh.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    val lbl = p.servingLabel.ifEmpty { "ks" }
                    Text(lbl, style = MaterialTheme.typography.bodyMedium, color = KlubColors.Muted)
                    Spacer(Modifier.width(10.dp))
                    if (cur != p.price) {
                        Text("${p.price}", style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted,
                            textDecoration = TextDecoration.LineThrough)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (p.price > 0) Credits.format(cur) else "bez ceny", style = Display.copy(fontSize = 18.sp),
                        color = if (p.price <= 0) KlubColors.OldDebt else if (cur != p.price) KlubColors.Gold else KlubColors.Cream)
                }
            }
        }
    }
}

/** Editable row of the drink editor. */
private data class EditServing(val id: Long, val ml: String, val name: String, val price: String)

/**
 * Drink editor: section, name, keg size (draught) and any number of sizes with optional name + price.
 * Sizes without a price are not saved (and so never appear on the card).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DrinkEditorSheet(kind0: Kind, drink: Drink?, onDismiss: () -> Unit, onAdvanced: (Product) -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(kind0) }
    var name by remember { mutableStateOf(drink?.name ?: "") }
    var kegSize by remember { mutableStateOf<Double?>(null) }
    val rows = remember {
        mutableStateListOf<EditServing>().apply {
            drink?.servings?.forEach { add(EditServing(it.id, if (it.pourMl > 0) it.pourMl.toString() else "", it.servingName ?: "", it.price.toString())) }
            if (isEmpty()) kind0.volumes.lastOrNull()?.let { add(EditServing(0, it.toString(), "", "")) } ?: add(EditServing(0, "", "", ""))
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val kegStock by repo.kegStock().collectAsState(initial = emptyList())
    LaunchedEffect(drink?.name, kegStock) {
        if (kegSize == null) kegSize = kegStock.firstOrNull { it.kegGroup == drink?.name }?.sizeLiters ?: 50.0
    }
    val valid = name.isNotBlank() && rows.any { (it.price.toLongOrNull() ?: 0) > 0 }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (drink == null) "Nový nápoj" else "Upravit nápoj", style = MaterialTheme.typography.headlineSmall)

            // 1) section
            Text("Sekce lístku", style = MaterialTheme.typography.labelLarge, color = KlubColors.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Kind.entries.forEach { k -> FilterChip(kind == k, { kind = k }, label = { Text("${k.emoji} ${k.label}") }) }
            }

            // 2) name
            OutlinedTextField(name, { name = it }, label = { Text("Název nápoje") }, placeholder = { Text("např. ${kind.example}") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.titleMedium)

            // 3) keg size for draught
            if (kind == Kind.DRAUGHT) {
                Text("Obvyklá velikost sudu", style = MaterialTheme.typography.labelLarge, color = KlubColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15.0, 20.0, 30.0, 50.0).forEach { s -> FilterChip(kegSize == s, { kegSize = s }, label = { Text("${s.toInt()} l") }) }
                }
            }

            // 4) sizes & prices
            Text("Míry a ceny", style = MaterialTheme.typography.labelLarge, color = KlubColors.Muted)
            rows.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(r.ml, { v -> rows[i] = r.copy(ml = v.filter(Char::isDigit).take(4)) },
                        label = { Text(if (kind.volumes.isEmpty()) "ml (nepov.)" else "ml") }, singleLine = true,
                        supportingText = { Text(volumeLabel(r.ml.toIntOrNull() ?: 0).ifEmpty { "kus" }) },
                        modifier = Modifier.width(110.dp), keyboardOptions = num)
                    OutlinedTextField(r.name, { v -> rows[i] = r.copy(name = v) }, label = { Text("Název míry") },
                        placeholder = { Text(if (kind == Kind.DRAUGHT) "šnyt" else "nepovinné") }, singleLine = true,
                        supportingText = { Text(" ") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(r.price, { v -> rows[i] = r.copy(price = v.filter(Char::isDigit).take(6)) },
                        label = { Text("Cena ${Credits.SYMBOL}") }, singleLine = true,
                        supportingText = { Text(if (r.price.isBlank()) "nezobrazí se" else " ") },
                        modifier = Modifier.width(110.dp), keyboardOptions = num, textStyle = MaterialTheme.typography.titleMedium)
                    Column {
                        IconButton({ rows.removeAt(i) }) { Icon(Icons.Default.Close, "Odebrat míru", tint = KlubColors.OldDebt) }
                        if (r.id != 0L) IconButton({
                            scope.launch { repo.servingsOf(drink!!.name).firstOrNull { it.id == r.id }?.let(onAdvanced) }
                        }) { Icon(Icons.Default.Settings, "Sklad, akce, obrázek", tint = KlubColors.Muted) }
                    }
                }
            }
            // quick add
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                kind.volumes.filter { v -> rows.none { it.ml == v.toString() } }.forEach { v ->
                    AssistChip({ rows += EditServing(0, v.toString(), "", "") }, label = { Text("+ ${volumeLabel(v)}") })
                }
                if (kind == Kind.DRAUGHT && rows.none { it.name.equals("šnyt", true) })
                    AssistChip({ rows += EditServing(0, "200", "šnyt", "") }, label = { Text("+ šnyt") })
                AssistChip({ rows += EditServing(0, "", "", "") }, label = { Text("+ vlastní míra") })
            }
            Text("Vlastní míra: zadej objem v ml (např. 400 = 0,4 l) a případně název. Míry bez ceny se neuloží a v lístku nebudou.",
                style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted)

            HorizontalDivider(color = KlubColors.Hairline)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (drink != null) TextButton({ confirmDelete = true }) { Text("Smazat nápoj", color = KlubColors.OldDebt) }
                Spacer(Modifier.weight(1f))
                TextButton(onDismiss) { Text("Zrušit") }
                Button(enabled = valid, onClick = {
                    val servings = rows.mapNotNull { r ->
                        val price = r.price.toLongOrNull() ?: return@mapNotNull null
                        if (price <= 0) return@mapNotNull null
                        ClubRepository.Serving(r.id, r.name.ifBlank { null }, r.ml.toIntOrNull() ?: 0, price)
                    }
                    scope.launch { repo.saveDrink(drink?.name, kind, name, servings, if (kind == Kind.DRAUGHT) kegSize else null) }
                    onDismiss()
                }) { Text("Uložit") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmDelete && drink != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Smazat ${drink.name}?") },
        text = { Text("Zmizí z lístku i z objednávek. Historie prodejů zůstane." + if (drink.kind == Kind.DRAUGHT) " U točeného piva se ukončí i sledování sudu." else "") },
        confirmButton = { TextButton({ scope.launch { repo.deleteDrink(drink.name) }; confirmDelete = false; onDismiss() }) {
            Text("Smazat", color = KlubColors.OldDebt)
        } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Zpět") } },
    )
}
