package cz.klubpos.ui.products

import cz.klubpos.ui.theme.glass
import cz.klubpos.domain.kindEnum
import cz.klubpos.domain.displayName
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.klubpos.data.db.entity.Product
import cz.klubpos.domain.Credits
import cz.klubpos.ui.components.EncryptedImage
import cz.klubpos.ui.components.QrUploadDialog
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.member.fmtQty
import kotlinx.coroutines.launch

/** Catalogue of sellable items (name, price, category, thumbnail, stock settings). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen() {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val products by repo.allProducts().collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Product?>(null) }
    var imageFor by remember { mutableStateOf<Product?>(null) }
    var fileFor by remember { mutableStateOf<Product?>(null) }
    val pickFile = cz.klubpos.ui.components.rememberImagePicker { bytes -> fileFor?.let { repo.setProductImage(it.id, bytes) }; fileFor = null }
    val unlocked = cz.klubpos.ui.owner.rememberOwnerUnlocked()
    var askUnlock by remember { mutableStateOf<Product?>(null) }
    var showIo by remember { mutableStateOf(false) }
    fun open(p: Product) { if (unlocked) editing = p else askUnlock = p }
    askUnlock?.let { p -> cz.klubpos.ui.owner.OwnerUnlockDialog(onDismiss = { askUnlock = null },
        onUnlocked = { askUnlock = null; if (p.id == -1L) showIo = true else if (p.id != -2L) editing = p }) }

    val settings = cz.klubpos.ui.components.rememberSettings()
    val cfg by settings.flow.collectAsState()
    val byType = cfg.productSort == "TYPE"
    val sorted = remember(products, cfg.productSort) {
        when (cfg.productSort) {
            "TYPE" -> products.sortedWith(compareBy({ !it.active }, { it.category.lowercase() }, { it.name.lowercase() }))
            "CUSTOM" -> products.sortedWith(compareBy({ !it.active }, { it.sortOrder }, { it.name.lowercase() }))
            else -> products.sortedWith(compareBy({ !it.active }, { it.name.lowercase() }))
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Nabídka") },
            actions = {
                val modes = listOf("ALPHA", "TYPE", "CUSTOM")
                cz.klubpos.ui.members.Segmented(listOf("A–Z", "Typ", "Vlastní"), modes.indexOf(cfg.productSort).coerceAtLeast(0)) { i ->
                    settings.update { it.copy(productSort = modes[i]) }
                }
                IconButton({ if (unlocked) showIo = true else askUnlock = Product(id = -1, name = "", price = 0) }) {
                    Icon(Icons.Default.UploadFile, "Import / vzor")
                }
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)) },
        floatingActionButton = {
            FloatingActionButton(onClick = { open(Product(name = "", price = 0)) }) { Icon(Icons.Default.Add, "Nová položka") }
        },
    ) { pad ->
        // Grid of cards: 1 column on a phone, 2–4 on a tablet.
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(320.dp),
            modifier = Modifier.padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 8.dp, end = 8.dp, bottom = 96.dp),
        ) {
            val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> androidx.compose.foundation.lazy.grid.GridItemSpan = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
            item(key = "draught", span = full) {
                DraughtSection(canEdit = unlocked, onRequestUnlock = { askUnlock = Product(id = -2, name = "", price = 0) })
            }
            // Sections: Čepované → Rozlévané alko → Rozlévané nealko → Lihoviny → Ostatní
            val groups: List<Pair<cz.klubpos.domain.Kind?, List<Product>>> =
                if (byType) cz.klubpos.domain.Kind.entries.map { k -> k to sorted.filter { p -> p.kindEnum == k } }
                    .filter { (k, l) -> l.isNotEmpty() || k != cz.klubpos.domain.Kind.DRAUGHT }
                else listOf(null to sorted)
            groups.forEach { (k, list) ->
                if (k != null && k != cz.klubpos.domain.Kind.DRAUGHT) item(key = "kind-${k.code}", span = full) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 18.dp, end = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${k.emoji}  ${k.label}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary)
                        IconButton({ open(Product(name = "", price = 0, kind = k.code, category = k.label,
                            pourMl = k.volumes.firstOrNull() ?: 0, trackStock = k == cz.klubpos.domain.Kind.OTHER)) }) {
                            Icon(Icons.Default.Add, "Přidat – ${k.label}", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (k != null && list.isEmpty()) item(key = "empty-${k.code}", span = full) {
                    Text("Zatím nic – přidej plusem.", Modifier.padding(start = 16.dp, bottom = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
              list.forEach { p ->
                item(key = p.id) {
                    Row(Modifier.fillMaxWidth().padding(6.dp).glass(RoundedCornerShape(18.dp)).clickable { open(p) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        EncryptedImage(p.imageId, p.name, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)), textSize = 16)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.displayName + if (!p.active) " (skryto)" else "", fontWeight = FontWeight.SemiBold)
                            Text(when {
                                p.kegGroup != null -> "ze sudu ${p.kegGroup}"
                                p.trackStock -> "sklad ${fmtQty(p.stockQty)} ${p.unit}" + (if (p.bottleMl > 0) " (${cz.klubpos.domain.volumeLabel(p.bottleMl)})" else "") + " · min ${fmtQty(p.minStock)}"
                                else -> "bez skladové evidence"
                            },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Credits.format(p.price), fontWeight = FontWeight.Bold)
                            if (p.salePrice != null) Text("AKCE ${p.salePrice}" + (p.saleLeft?.let { " · ${it.toInt()} ks" } ?: ""),
                                style = MaterialTheme.typography.labelSmall, color = cz.klubpos.ui.theme.KlubColors.OpenTab)
                            if (p.dealActive()) Text("${p.dealBuy}+${p.dealFree} zdarma",
                                style = MaterialTheme.typography.labelSmall, color = cz.klubpos.ui.theme.KlubColors.PaidToday)
                            if (p.promoPrice != null) Text("HH ${p.promoPrice} · ${cz.klubpos.ui.components.fmtTime(p.promoFrom)}–${cz.klubpos.ui.components.fmtTime(p.promoTo)}",
                                style = MaterialTheme.typography.labelSmall, color = cz.klubpos.ui.theme.KlubColors.Gold)
                        }
                    }
                }
              }
            }
        }
    }

    editing?.let { p ->
        ProductDialog(p, onDismiss = { editing = null }, onImage = { imageFor = p; editing = null },
            onImageFile = { fileFor = p; editing = null; pickFile() }) { updated ->
            scope.launch { repo.saveProduct(updated) }; editing = null
        }
    }
    if (showIo) ImportExportSheet(onDismiss = { showIo = false })
    imageFor?.let { p -> QrUploadDialog("Obrázek: ${p.name}", onImage = { repo.setProductImage(p.id, it) }) { imageFor = null } }
}

@Composable
private fun ProductDialog(p: Product, onDismiss: () -> Unit, onImage: () -> Unit, onImageFile: () -> Unit, onSave: (Product) -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var category by remember { mutableStateOf(p.category) }
    var price by remember { mutableStateOf(if (p.price == 0L) "" else p.price.toString()) }
    var track by remember { mutableStateOf(p.trackStock) }
    var unit by remember { mutableStateOf(p.unit) }
    var min by remember { mutableStateOf(fmtQty(p.minStock)) }
    var reorder by remember { mutableStateOf(fmtQty(p.reorderQty)) }
    var active by remember { mutableStateOf(p.active) }
    val num = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    fun d(s: String) = s.replace(',', '.').toDoubleOrNull() ?: 0.0
    var kind by remember { mutableStateOf(if (p.kegGroup != null) cz.klubpos.domain.Kind.DRAUGHT else cz.klubpos.domain.Kind.of(p.kind)) }
    val draught = kind == cz.klubpos.domain.Kind.DRAUGHT
    val poured = kind == cz.klubpos.domain.Kind.POURED_ALCO || kind == cz.klubpos.domain.Kind.POURED_SOFT || kind == cz.klubpos.domain.Kind.SPIRIT
    var bottleMl by remember { mutableStateOf(if (p.bottleMl > 0) p.bottleMl.toString() else "") }
    var kegGroup by remember { mutableStateOf(p.kegGroup ?: p.name.substringBefore(" velk").substringBefore(" mal")) }
    var pourMl by remember { mutableStateOf(if (p.pourMl > 0) p.pourMl.toString() else (kind.volumes.firstOrNull()?.toString() ?: "")) }
    var promoOn by remember { mutableStateOf(p.promoPrice != null) }
    var promoPrice by remember { mutableStateOf(p.promoPrice?.toString() ?: "") }
    var promoDays by remember { mutableStateOf(p.promoDays) }
    var promoFrom by remember { mutableStateOf(cz.klubpos.ui.components.fmtTime(p.promoFrom)) }
    var promoTo by remember { mutableStateOf(cz.klubpos.ui.components.fmtTime(p.promoTo)) }
    fun parseTime(t: String): Int? {
        val parts = t.trim().replace('.', ':').split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return if (h in 0..24 && m in 0..59) (h % 24) * 60 + m else null
    }
    // ---- sale (akční cena) ----
    val df = remember { java.text.SimpleDateFormat("d.M.yyyy", java.util.Locale("cs", "CZ")).apply { isLenient = false } }
    fun parseDate(t: String): Long? = runCatching { df.parse(t.trim().replace(" ", ""))?.time }.getOrNull()
    var saleOn by remember { mutableStateOf(p.salePrice != null) }
    var salePrice by remember { mutableStateOf(p.salePrice?.toString() ?: "") }
    var saleFrom by remember { mutableStateOf(df.format(java.util.Date(p.saleFrom ?: System.currentTimeMillis()))) }
    var saleTo by remember { mutableStateOf(p.saleTo?.let { df.format(java.util.Date(it - 1)) } ?: "") }
    var saleUntilSoldOut by remember { mutableStateOf(p.saleLeft != null) }
    var saleLeft by remember { mutableStateOf(p.saleLeft?.toInt()?.toString() ?: if (p.trackStock) p.stockQty.toInt().toString() else "") }
    val saleValid = !saleOn || (salePrice.toLongOrNull() != null && parseDate(saleFrom) != null &&
        (saleTo.isBlank() || parseDate(saleTo) != null) && (!saleUntilSoldOut || (saleLeft.toIntOrNull() ?: 0) > 0))
    // ---- deal X+Y ----
    var dealOn by remember { mutableStateOf(p.dealActive()) }
    var dealBuy by remember { mutableStateOf(if (p.dealBuy > 0) p.dealBuy.toString() else "3") }
    var dealFree by remember { mutableStateOf(if (p.dealFree > 0) p.dealFree.toString() else "1") }
    val dealValid = !dealOn || ((dealBuy.toIntOrNull() ?: 0) > 0 && (dealFree.toIntOrNull() ?: 0) > 0)
    val hhValid = !promoOn || (promoPrice.toLongOrNull() != null && parseTime(promoFrom) != null && parseTime(promoTo) != null && promoDays != 0)
    val promoValid = hhValid && saleValid && dealValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (p.id == 0L) "Nová položka" else "Upravit položku") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // ---- What it is ----
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cz.klubpos.domain.Kind.entries.forEach { k ->
                        androidx.compose.material3.FilterChip(kind == k, {
                            kind = k
                            if (pourMl.isBlank() || pourMl.toIntOrNull() !in k.volumes) pourMl = k.volumes.firstOrNull()?.toString() ?: ""
                            if (k == cz.klubpos.domain.Kind.DRAUGHT) track = false
                            if (category.isBlank() || cz.klubpos.domain.Kind.entries.any { it.label == category }) category = k.label
                        }, label = { Text("${k.emoji} ${k.label}") })
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text(if (draught) "Název (např. Plzeň 12° velké)" else "Název") }, singleLine = true)
                if (draught) OutlinedTextField(kegGroup, { kegGroup = it }, label = { Text("Sud (stejný název = stejný sud)") }, singleLine = true,
                    supportingText = { Text("Velké i malé 12° mají sud „Pivo 12°“") })
                // ---- offered volume + its price ----
                if (kind.volumes.isNotEmpty()) {
                    Text("Nabízený objem", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        kind.volumes.forEach { v ->
                            androidx.compose.material3.FilterChip(pourMl == v.toString(), { pourMl = v.toString() }, label = { Text(cz.klubpos.domain.volumeLabel(v)) })
                        }
                        OutlinedTextField(pourMl, { pourMl = it.filter(Char::isDigit).take(4) }, label = { Text("ml") }, singleLine = true,
                            modifier = Modifier.width(90.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
                OutlinedTextField(price, { price = it.filter(Char::isDigit) }, singleLine = true,
                    label = { Text("Cena" + (pourMl.toIntOrNull()?.takeIf { kind.volumes.isNotEmpty() && it > 0 }?.let { " za ${cz.klubpos.domain.volumeLabel(it)}" } ?: "") +
                        " (${cz.klubpos.domain.Credits.SYMBOL})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(category, { category = it }, label = { Text("Kategorie (barva tlačítka)") }, singleLine = true)
                if (!draught) Row(verticalAlignment = Alignment.CenterVertically) { Switch(track, { track = it }); Spacer(Modifier.width(8.dp)); Text("Sledovat sklad") }
                if (track && !draught) {
                    if (poured) {
                        Text("Velikost lahve – sklad se pak počítá v lahvích", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf(500, 700, 750, 1000).forEach { b ->
                                androidx.compose.material3.FilterChip(bottleMl == b.toString(), { bottleMl = b.toString(); unit = "lahev" }, label = { Text(cz.klubpos.domain.volumeLabel(b)) })
                            }
                            OutlinedTextField(bottleMl, { bottleMl = it.filter(Char::isDigit).take(5) }, label = { Text("ml") }, singleLine = true,
                                modifier = Modifier.width(90.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        val per = (bottleMl.toIntOrNull() ?: 0).let { b -> if (b > 0 && (pourMl.toIntOrNull() ?: 0) > 0) b / pourMl.toInt() else 0 }
                        if (per > 0) Text("≈ $per porcí z lahve", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(unit, { unit = it }, label = { Text("Jednotka (ks, lahev, kg…)") }, singleLine = true)
                    OutlinedTextField(min, { min = it }, label = { Text("Minimální stav (pod ním → nákup)") }, singleLine = true, keyboardOptions = num)
                    OutlinedTextField(reorder, { reorder = it }, label = { Text("Obvykle nakupuji (0 = automaticky)") }, singleLine = true, keyboardOptions = num)
                }
                // ---- Happy hour ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(promoOn, { promoOn = it }); Spacer(Modifier.width(8.dp)); Text("Akční cena (happy hour)")
                }
                if (promoOn) {
                    OutlinedTextField(promoPrice, { promoPrice = it.filter(Char::isDigit) }, label = { Text("Akční cena (${cz.klubpos.domain.Credits.SYMBOL})") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Po", "Út", "St", "Čt", "Pá", "So", "Ne").forEachIndexed { i, d ->
                            val on = promoDays and (1 shl i) != 0
                            androidx.compose.material3.FilterChip(on, { promoDays = promoDays xor (1 shl i) }, label = { Text(d) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(promoFrom, { promoFrom = it }, label = { Text("Od") }, singleLine = true, modifier = Modifier.weight(1f),
                            isError = parseTime(promoFrom) == null)
                        OutlinedTextField(promoTo, { promoTo = it }, label = { Text("Do") }, singleLine = true, modifier = Modifier.weight(1f),
                            isError = parseTime(promoTo) == null)
                    }
                    Text("Čas např. 17:00 – 20:00. Přes půlnoc (22:00 – 2:00) funguje taky.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // ---- Akční cena ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(saleOn, { saleOn = it }); Spacer(Modifier.width(8.dp)); Text("Akční cena (delší doba / do vyprodání)")
                }
                if (saleOn) {
                    OutlinedTextField(salePrice, { salePrice = it.filter(Char::isDigit) }, label = { Text("Akční cena (${cz.klubpos.domain.Credits.SYMBOL})") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(saleFrom, { saleFrom = it }, label = { Text("Od (d.m.rrrr)") }, singleLine = true,
                            modifier = Modifier.weight(1f), isError = parseDate(saleFrom) == null)
                        OutlinedTextField(saleTo, { saleTo = it }, label = { Text("Do (prázdné = bez konce)") }, singleLine = true,
                            modifier = Modifier.weight(1f), isError = saleTo.isNotBlank() && parseDate(saleTo) == null)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(7 to "+7 dní", 14 to "+14 dní", 30 to "+30 dní").forEach { (d, l) ->
                            androidx.compose.material3.AssistChip({
                                val start = parseDate(saleFrom) ?: System.currentTimeMillis()
                                saleTo = df.format(java.util.Date(start + (d - 1) * 86_400_000L))
                            }, label = { Text(l) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(saleUntilSoldOut, { saleUntilSoldOut = it })
                        Text("Do vyprodání –", Modifier.padding(end = 8.dp))
                        OutlinedTextField(saleLeft, { saleLeft = it.filter(Char::isDigit) }, label = { Text("ks za akční cenu") }, singleLine = true,
                            enabled = saleUntilSoldOut, modifier = Modifier.width(150.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
                // ---- Akce X + Y zdarma ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(dealOn, { dealOn = it }); Spacer(Modifier.width(8.dp)); Text("Akce X + Y zdarma")
                }
                if (dealOn) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(dealBuy, { dealBuy = it.filter(Char::isDigit).take(2) }, label = { Text("Kup") }, singleLine = true,
                        modifier = Modifier.width(90.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Text("+", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(dealFree, { dealFree = it.filter(Char::isDigit).take(2) }, label = { Text("zdarma") }, singleLine = true,
                        modifier = Modifier.width(90.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(active, { active = it }); Spacer(Modifier.width(8.dp)); Text("Aktivní (nabízet barmanovi)") }
                if (p.id != 0L) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImageFile) { Text("Obrázek ze souboru") }
                    OutlinedButton(onClick = onImage) { Text("Z mobilu (QR)") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && price.toLongOrNull() != null && (!draught || kegGroup.isNotBlank()) && promoValid, onClick = {
                onSave(p.copy(name = name.trim(), category = category.trim().ifBlank { kind.label }, price = price.toLong(),
                    kind = kind.code, bottleMl = if (poured && track) bottleMl.toIntOrNull() ?: 0 else 0,
                    trackStock = track && !draught, unit = unit.ifBlank { "ks" }, minStock = d(min), reorderQty = d(reorder), active = active,
                    kegGroup = if (draught) kegGroup.trim().ifBlank { null } else null,
                    pourMl = if (kind.volumes.isNotEmpty()) pourMl.toIntOrNull() ?: 0 else 0,
                    promoPrice = if (promoOn) promoPrice.toLongOrNull() else null,
                    promoDays = promoDays, promoFrom = parseTime(promoFrom) ?: p.promoFrom, promoTo = parseTime(promoTo) ?: p.promoTo,
                    salePrice = if (saleOn) salePrice.toLongOrNull() else null,
                    saleFrom = if (saleOn) parseDate(saleFrom) else null,
                    saleTo = if (saleOn) parseDate(saleTo)?.plus(86_400_000L) else null,   // "do" date is inclusive
                    saleLeft = if (saleOn && saleUntilSoldOut) saleLeft.toDoubleOrNull() else null,
                    dealBuy = if (dealOn) dealBuy.toIntOrNull() ?: 0 else 0,
                    dealFree = if (dealOn) dealFree.toIntOrNull() ?: 0 else 0))
            }) { Text("Uložit") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}
