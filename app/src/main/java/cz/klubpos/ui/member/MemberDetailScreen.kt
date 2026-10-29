package cz.klubpos.ui.member

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import cz.klubpos.data.db.entity.EntryType
import cz.klubpos.data.db.entity.LedgerEntry
import cz.klubpos.data.db.entity.Product
import cz.klubpos.domain.Credits
import cz.klubpos.domain.MemberStatus
import cz.klubpos.ui.components.EncryptedImage
import cz.klubpos.ui.components.NumberDialog
import cz.klubpos.ui.components.QrUploadDialog
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.members.headlineColor
import cz.klubpos.ui.theme.KlubColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(memberId: Long, onBack: () -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val member by repo.member(memberId).collectAsState(initial = null)
    val status by remember { repo.memberStatuses() }.collectAsState(initial = emptyList())
    val st = status.firstOrNull { it.memberId == memberId }
    val products by repo.activeProducts().collectAsState(initial = emptyList())
    val ledger by repo.ledger(memberId).collectAsState(initial = emptyList())

    var showPay by remember { mutableStateOf(false) }
    var showPhotoQr by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var qtyFor by remember { mutableStateOf<Product?>(null) }
    var voidEntry by remember { mutableStateOf<LedgerEntry?>(null) }

    fun charge(p: Product, qty: Double = 1.0, price: Long? = null) = scope.launch {
        repo.chargeProduct(memberId, p.id, qty, price)
        snackbar.showSnackbar("+ ${p.name}" + if (qty != 1.0) " ×${fmtQty(qty)}" else "")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(member?.nickname ?: member?.name ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
                actions = { IconButton(onClick = { showEdit = true }) { Icon(Icons.Default.Edit, "Upravit") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
            val header: @Composable () -> Unit = {
                Header(member?.name ?: "", member?.photoId, st,
                    onPhoto = { showPhotoQr = true },
                    onToggleVisit = { scope.launch { if (st?.inClub == true) repo.closeVisit(memberId) else repo.openVisit(memberId) } },
                    onPay = { showPay = true })
            }
            val picker: @Composable (Modifier) -> Unit = { mod ->
                ProductPicker(products, mod, onTap = { charge(it) }, onLong = { qtyFor = it }, onCustom = { showCustom = true })
            }
            val history: @Composable (Modifier) -> Unit = { mod -> History(ledger, mod) { voidEntry = it } }

            if (maxWidth >= 720.dp) {
                // Tablet: account on the left, products on the right.
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(360.dp).fillMaxHeight()) {
                        header(); HorizontalDivider(); history(Modifier.weight(1f))
                    }
                    picker(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                var tab by remember { mutableIntStateOf(0) }
                Column(Modifier.fillMaxSize()) {
                    header()
                    TabRow(selectedTabIndex = tab) {
                        Tab(tab == 0, { tab = 0 }, text = { Text("Zapsat") })
                        Tab(tab == 1, { tab = 1 }, text = { Text("Účet") })
                    }
                    if (tab == 0) picker(Modifier.weight(1f)) else history(Modifier.weight(1f))
                }
            }
        }
    }

    if (showPay && st != null) PayDialog(st, onDismiss = { showPay = false }) { amount, leave ->
        scope.launch { repo.pay(memberId, amount, leave); snackbar.showSnackbar("Zaplaceno ${Credits.format(amount)}") }
        showPay = false
    }
    if (showPhotoQr) QrUploadDialog("Fotka člena", onImage = { repo.setMemberPhoto(memberId, it) }) { showPhotoQr = false }
    if (showCustom) CustomItemDialog(onDismiss = { showCustom = false }) { desc, price ->
        scope.launch { repo.chargeCustom(memberId, desc, price) }; showCustom = false
    }
    qtyFor?.let { p ->
        QtyPriceDialog(p, onDismiss = { qtyFor = null }) { qty, price -> charge(p, qty, price); qtyFor = null }
    }
    voidEntry?.let { e ->
        AlertDialog(
            onDismissRequest = { voidEntry = null },
            title = { Text("Stornovat?") },
            text = { Text("${e.description} · ${Credits.format(kotlin.math.abs(e.amount))}\nZapíše se opravná položka, skladu se vrátí množství.") },
            confirmButton = { TextButton({ scope.launch { repo.void(e.id) }; voidEntry = null }) { Text("Storno") } },
            dismissButton = { TextButton({ voidEntry = null }) { Text("Zpět") } },
        )
    }
    if (showEdit) member?.let { m ->
        EditMemberDialog(m.name, m.nickname ?: "", m.note ?: "", onDismiss = { showEdit = false },
            onArchive = { scope.launch { repo.updateMember(m.copy(active = false)) }; showEdit = false; onBack() }) { n, nick, note ->
            scope.launch { repo.updateMember(m.copy(name = n, nickname = nick.ifBlank { null }, note = note.ifBlank { null })) }
            showEdit = false
        }
    }
}

@Composable
private fun Header(
    name: String, photoId: String?, st: MemberStatus?,
    onPhoto: () -> Unit, onToggleVisit: () -> Unit, onPay: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        EncryptedImage(photoId, name, Modifier.size(84.dp).clip(CircleShape).clickable(onClick = onPhoto), textSize = 30)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (st != null) {
                Text(st.headline.label, color = headlineColor(st.headline), fontWeight = FontWeight.Bold)
                Text(
                    when {
                        st.balance > 0 -> "Na účtu ${Credits.format(st.balance)}"
                        st.balance < 0 -> "Přeplatek ${Credits.format(-st.balance)}"
                        else -> "Účet vyrovnán"
                    },
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                )
                if (st.todayUnpaid > 0 && st.olderDebt > 0) Text(
                    "dnes ${Credits.format(st.todayUnpaid)} · z minula ${Credits.format(st.olderDebt)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleVisit) { Text(if (st?.inClub == true) "Odchod" else "Příchod") }
                Button(onClick = onPay, enabled = (st?.balance ?: 0) > 0) { Text("Zaplatit") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductPicker(
    products: List<Product>, modifier: Modifier,
    onTap: (Product) -> Unit, onLong: (Product) -> Unit, onCustom: () -> Unit,
) {
    val categories = remember(products) { products.map { it.category }.distinct() }
    var cat by remember { mutableStateOf<String?>(null) }
    Column(modifier) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(cat == null, { cat = null }, label = { Text("Vše") })
            categories.forEach { c -> FilterChip(cat == c, { cat = c }, label = { Text(c) }) }
        }
        Text("Klepnutí = 1 ks · podržení = množství / jiná cena", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
        LazyVerticalGrid(
            GridCells.Adaptive(120.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(products.filter { cat == null || it.category == cat }, key = { it.id }) { p ->
                Card(Modifier.combinedClickable(onClick = { onTap(p) }, onLongClick = { onLong(p) })) {
                    EncryptedImage(p.imageId, p.name, Modifier.fillMaxWidth().aspectRatio(1.6f), textSize = 22)
                    Column(Modifier.padding(8.dp)) {
                        Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(Credits.format(p.price), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            if (p.trackStock) Text(fmtQty(p.stockQty), style = MaterialTheme.typography.labelSmall,
                                color = if (p.stockQty <= p.minStock) KlubColors.OldDebt else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Card(Modifier.clickable(onClick = onCustom)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.0f), contentAlignment = Alignment.Center) {
                        Text("+ Vlastní\npoložka", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun History(entries: List<LedgerEntry>, modifier: Modifier, onVoid: (LedgerEntry) -> Unit) {
    val voided = remember(entries) { entries.mapNotNull { it.voidsEntryId }.toSet() }
    val df = remember { SimpleDateFormat("d.M. HH:mm", Locale("cs", "CZ")) }
    if (entries.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Zatím žádné záznamy") }
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        items(entries, key = { it.id }) { e ->
            val isVoided = e.id in voided
            val canVoid = !isVoided && e.voidsEntryId == null
            Row(
                Modifier.fillMaxWidth().clickable(enabled = canVoid) { onVoid(e) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        e.description + if (e.quantity != 1.0) " ×${fmtQty(e.quantity)}" else "",
                        textDecoration = if (isVoided) TextDecoration.LineThrough else null,
                        color = if (isVoided) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(df.format(Date(e.createdAt)), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val payment = e.type == EntryType.PAYMENT
                Text(
                    (if (e.amount < 0) "−" else "+") + Credits.format(kotlin.math.abs(e.amount)),
                    fontWeight = FontWeight.Bold,
                    color = if (payment) KlubColors.PaidToday else MaterialTheme.colorScheme.onSurface,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@Composable
private fun PayDialog(st: MemberStatus, onDismiss: () -> Unit, onPay: (Long, Boolean) -> Unit) {
    var text by remember { mutableStateOf(st.balance.toString()) }
    var leave by remember { mutableStateOf(st.inClub) }
    val amount = text.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Platba") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Na účtu: ${Credits.format(st.balance)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (st.todayUnpaid > 0 && st.olderDebt > 0)
                        OutlinedButton({ text = st.todayUnpaid.toString() }) { Text("Jen dnešek") }
                    OutlinedButton({ text = st.balance.toString() }) { Text("Vše") }
                }
                OutlinedTextField(text, { text = it.filter(Char::isDigit) }, label = { Text("Kredity (1 kr = 1 Kč)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.headlineSmall)
                if (st.inClub) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(leave, { leave = it }); Text("Odchází z klubu (zavřít účet)")
                }
            }
        },
        confirmButton = { Button(enabled = amount != null && amount > 0, onClick = { onPay(amount!!, leave) }) { Text("Zaplatit") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}

@Composable
private fun CustomItemDialog(onDismiss: () -> Unit, onAdd: (String, Long) -> Unit) {
    var desc by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vlastní položka") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(desc, { desc = it }, label = { Text("Popis") }, singleLine = true)
                OutlinedTextField(price, { price = it.filter(Char::isDigit) }, label = { Text("Cena (kr)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = { TextButton(enabled = (price.toLongOrNull() ?: 0) > 0, onClick = { onAdd(desc, price.toLong()) }) { Text("Zapsat") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}

@Composable
private fun QtyPriceDialog(p: Product, onDismiss: () -> Unit, onConfirm: (Double, Long) -> Unit) {
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf(p.price.toString()) }
    val q = qty.replace(',', '.').toDoubleOrNull()
    val pr = price.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(qty, { qty = it }, label = { Text("Množství") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(price, { price = it.filter(Char::isDigit) }, label = { Text("Cena za kus (kr)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (q != null && pr != null) Text("Celkem ${Credits.format(Math.round(q * pr))}", fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { TextButton(enabled = q != null && q > 0 && pr != null, onClick = { onConfirm(q!!, pr!!) }) { Text("Zapsat") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}

@Composable
private fun EditMemberDialog(
    name0: String, nick0: String, note0: String,
    onDismiss: () -> Unit, onArchive: () -> Unit, onSave: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(name0) }
    var nick by remember { mutableStateOf(nick0) }
    var note by remember { mutableStateOf(note0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upravit člena") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Jméno") }, singleLine = true)
                OutlinedTextField(nick, { nick = it }, label = { Text("Přezdívka") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Poznámka") })
                TextButton(onClick = onArchive) { Text("Archivovat člena", color = KlubColors.OldDebt) }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, nick, note) }) { Text("Uložit") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}

fun fmtQty(q: Double): String = if (q % 1.0 == 0.0) q.toLong().toString() else "%.2f".format(q).trimEnd('0').trimEnd(',', '.')
