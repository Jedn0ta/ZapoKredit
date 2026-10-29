@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package cz.klubpos.ui.member

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.klubpos.data.db.entity.EntryType
import cz.klubpos.data.db.entity.LedgerEntry
import cz.klubpos.data.db.entity.Product
import cz.klubpos.domain.BusinessDay
import cz.klubpos.domain.Credits
import cz.klubpos.domain.MemberStatus
import cz.klubpos.ui.components.EncryptedImage
import cz.klubpos.ui.components.SwipeAction
import cz.klubpos.ui.components.SwipeRow
import cz.klubpos.ui.components.categoryColor
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.ui.theme.Display
import cz.klubpos.ui.theme.glass
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import cz.klubpos.ui.components.rememberSettings
import cz.klubpos.ui.members.Segmented
import cz.klubpos.ui.tour.tourTarget
import cz.klubpos.domain.QrPayment
import cz.klubpos.upload.QrGenerator
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.itemsIndexed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Today's charges for one item, aggregated like a receipt line. */
private const val FAV = "\u0000fav"

private data class ReceiptLine(
    val key: String, val name: String, val productId: Long?, val unitPrice: Long,
    val qty: Double, val amount: Long, val entryIds: List<Long>,   // newest first
)

private fun buildReceipt(ledger: List<LedgerEntry>, dayStart: Long): Pair<List<ReceiptLine>, List<LedgerEntry>> {
    val voided = ledger.mapNotNull { it.voidsEntryId }.toSet()
    val today = ledger.filter { it.createdAt >= dayStart && it.voidsEntryId == null && it.id !in voided }
    val lines = today.filter { it.type == EntryType.CHARGE }
        .groupBy { "${it.productId ?: it.description}|${it.unitPrice}" }
        .map { (k, es) ->
            ReceiptLine(k, es.first().description, es.first().productId, es.first().unitPrice,
                es.sumOf { it.quantity }, es.sumOf { it.amount }, es.sortedByDescending { it.createdAt }.map { it.id })
        }
        .sortedByDescending { l -> today.first { it.id == l.entryIds.first() }.createdAt }
    val payments = today.filter { it.type == EntryType.PAYMENT }
    return lines to payments
}

/**
 * Order screen – the bartender's main workspace.
 *  product tap        → +1 on the tab (haptic + count badge)
 *  product long-press → quantity / different price
 *  receipt line swipe ← storno 1 ks, swipe → +1 ks
 *  Zaplatit tap       → payment sheet; long-press → pay everything at once
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(memberId: Long, onBack: () -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val dayStart = remember { BusinessDay.startMillis() }

    val member by repo.member(memberId).collectAsState(initial = null)
    val statuses by remember { repo.memberStatuses(dayStart) }.collectAsState(initial = emptyList())
    val st = statuses.firstOrNull { it.memberId == memberId }
    val products by repo.activeProducts().collectAsState(initial = emptyList())
    val ledger by repo.ledger(memberId).collectAsState(initial = emptyList())
    val (lines, payments) = remember(ledger) { buildReceipt(ledger, dayStart) }
    val countByProduct = remember(lines) { lines.filter { it.productId != null }.groupBy { it.productId!! }.mapValues { e -> e.value.sumOf { it.qty } } }

    var showPay by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showReceiptSheet by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var qtyFor by remember { mutableStateOf<Product?>(null) }
    var transferLine by remember { mutableStateOf<ReceiptLine?>(null) }
    val settings by rememberSettings().flow.collectAsState()
    // Favourites = what THIS member orders most often.
    val top by remember(memberId) { repo.memberFavorites(memberId) }.collectAsState(initial = emptyList())
    val favorites = remember(top, products) { top.mapNotNull { t -> products.firstOrNull { it.id == t.productId } } }
    var lastLabel by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(ledger) { lastLabel = repo.lastChargeLabel(memberId) }

    fun add(p: Product, qty: Double = 1.0, price: Long? = null) = scope.launch {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        repo.chargeProduct(memberId, p.id, qty, price)
    }
    fun voidWithUndo(entryId: Long, label: String) = scope.launch {
        repo.void(entryId)
        snackbar.currentSnackbarData?.dismiss()
        if (snackbar.showSnackbar("Storno: $label", actionLabel = "Vrátit") == SnackbarResult.ActionPerformed) {
            // Undo = charge the same thing again
            ledger.firstOrNull { it.id == entryId }?.let { e ->
                if (e.productId != null) repo.chargeProduct(memberId, e.productId, e.quantity, e.unitPrice)
                else repo.chargeCustom(memberId, e.description, e.amount)
            }
        }
    }
    fun repeat() = scope.launch {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        repo.repeatLast(memberId)?.let { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar("↻ $it") }
    }
    fun payAll() = scope.launch {
        val bal = st?.balance ?: 0
        if (bal > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            repo.pay(memberId, bal, closeVisitAfter = false, method = "CASH")
            snackbar.showSnackbar("Zaplaceno ${Credits.format(bal)}")
        }
    }

    val receipt: @Composable (Modifier) -> Unit = { mod ->
        Receipt(st, lines, payments, mod,
            onPlus = { l -> products.firstOrNull { it.id == l.productId }?.let { add(it, 1.0, l.unitPrice) } },
            onMinus = { l -> voidWithUndo(l.entryIds.first(), l.name) },
            onLong = { l -> transferLine = l })
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopRow(member?.nickname ?: member?.name ?: "", member?.photoId, st?.inClub == true,
                onBack = onBack, onName = { showEdit = true }, onHistory = { showHistory = true })
            val limit = settings.debtLimit
            if (limit > 0 && st != null && st.balance >= limit) {
                Text("Překročen limit dluhu ${Credits.format(limit)}",
                    Modifier.fillMaxWidth().background(KlubColors.OldDebt).padding(horizontal = 20.dp, vertical = 8.dp),
                    color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                if (maxWidth >= 720.dp) {
                    // Tablet: products | receipt
                    Row(Modifier.fillMaxSize()) {
                        ProductGrid(products, favorites, lastLabel, countByProduct, Modifier.weight(1f).fillMaxHeight(),
                            onTap = { add(it) }, onLong = { qtyFor = it }, onCustom = { showCustom = true }, onRepeat = { repeat() })
                        Column(Modifier.width(360.dp).fillMaxHeight().padding(12.dp).glass(RoundedCornerShape(28.dp))) {
                            receipt(Modifier.weight(1f).tourTarget("receipt"))
                            PayBar(st, expanded = true, onPay = { showPay = true }, onPayAll = { payAll() }, onOpen = {})
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        ProductGrid(products, favorites, lastLabel, countByProduct, Modifier.weight(1f),
                            onTap = { add(it) }, onLong = { qtyFor = it }, onCustom = { showCustom = true }, onRepeat = { repeat() })
                        PayBar(st, expanded = false, onPay = { showPay = true }, onPayAll = { payAll() },
                            onOpen = { showReceiptSheet = true })
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
    }

    if (showReceiptSheet) ModalBottomSheet(onDismissRequest = { showReceiptSheet = false },
        containerColor = MaterialTheme.colorScheme.surface) {
        receipt(Modifier.fillMaxWidth().height(420.dp))
    }
    if (showPay && st != null) PaySheet(st, member?.nickname ?: member?.name ?: "", settings.account, settings.clubName,
        onDismiss = { showPay = false }) { amount, leave, method ->
        scope.launch { repo.pay(memberId, amount, leave, method); snackbar.showSnackbar("Zaplaceno ${Credits.format(amount)}") }
        showPay = false; if (leave) onBack()
    }
    if (showCustom) CustomItemDialog(onDismiss = { showCustom = false }) { desc, price ->
        scope.launch { repo.chargeCustom(memberId, desc, price) }; showCustom = false
    }
    qtyFor?.let { p -> QtyPriceDialog(p, onDismiss = { qtyFor = null }) { q, pr -> add(p, q, pr); qtyFor = null } }
    transferLine?.let { l ->
        TransferSheet(statuses.filter { it.memberId != memberId }, l.name, onDismiss = { transferLine = null }) { to ->
            scope.launch { repo.transfer(l.entryIds.first(), to.memberId); snackbar.showSnackbar("${l.name} → ${to.nickname ?: to.name}") }
            transferLine = null
        }
    }
    if (showHistory) HistorySheet(ledger, onDismiss = { showHistory = false }) { e -> voidWithUndo(e.id, e.description) }
    if (showEdit) member?.let { m ->
        EditMemberDialog(m.name, m.nickname ?: "", m.note ?: "", onDismiss = { showEdit = false },
            onArchive = { scope.launch { repo.updateMember(m.copy(active = false)) }; showEdit = false; onBack() }) { n, nick, note ->
            scope.launch { repo.updateMember(m.copy(name = n, nickname = nick.ifBlank { null }, note = note.ifBlank { null })) }
            showEdit = false
        }
    }
}

@Composable
private fun TopRow(name: String, photoId: String?, inClub: Boolean, onBack: () -> Unit, onName: () -> Unit, onHistory: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") }
        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onName).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box {
                EncryptedImage(photoId, name, Modifier.size(40.dp).clip(CircleShape), textSize = 16)
                if (inClub) Box(Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape).background(KlubColors.InClub))
            }
            Spacer(Modifier.width(12.dp))
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onHistory, modifier = Modifier.tourTarget("history")) { Icon(Icons.Default.History, "Historie") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductGrid(
    products: List<Product>, favorites: List<Product>, lastLabel: String?, counts: Map<Long, Double>, modifier: Modifier,
    onTap: (Product) -> Unit, onLong: (Product) -> Unit, onCustom: () -> Unit, onRepeat: () -> Unit,
) {
    val sortAlpha = cz.klubpos.ui.components.rememberSettings().flow.collectAsState().value.productSort == "ALPHA"
    val products = remember(products, sortAlpha) {
        if (sortAlpha) products.sortedBy { it.name.lowercase() }
        else products.sortedWith(compareBy({ it.category.lowercase() }, { it.name.lowercase() }))
    }
    val categories = remember(products) { products.map { it.category }.distinct().sortedBy { it.lowercase() } }
    // Default view = everything; "★ Oblíbené" shows what this member usually has.
    var cat by remember { mutableStateOf<String?>(null) }
    Column(modifier) {
        if (lastLabel != null) Row(Modifier.padding(start = 12.dp, top = 6.dp).clip(RoundedCornerShape(18.dp))
            .glass(RoundedCornerShape(18.dp)).clickable(onClick = onRepeat).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("↻ To samé · $lastLabel", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (categories.size > 1 || favorites.isNotEmpty()) Row(Modifier.tourTarget("favorites").horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryPill("Vše", null, cat == null) { cat = null }
            if (favorites.isNotEmpty()) CategoryPill("★ Oblíbené", KlubColors.Amber, cat == FAV) { cat = FAV }
            categories.forEach { c -> CategoryPill(c, categoryColor(c), cat == c) { cat = c } }
        }
        LazyVerticalGrid(
            GridCells.Adaptive(112.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val list = when (cat) { null -> products; FAV -> favorites; else -> products.filter { it.category == cat } }
            itemsIndexed(list, key = { _, p -> p.id }) { i, p ->
                ProductKey(p, counts[p.id] ?: 0.0, if (i == 0) Modifier.tourTarget("product_key") else Modifier,
                    onTap = { onTap(p) }, onLong = { onLong(p) })
            }
            item {
                Box(Modifier.tourTarget("custom_amount").aspectRatio(1f).glass(RoundedCornerShape(22.dp), alpha = 0.03f)
                    .clickable(onClick = onCustom), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Částka", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(label: String, color: Color?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
        .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
        .clickable(onClick = onClick).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (color != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(8.dp)) }
        Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Big POS key: category colour, name, price. Count badge = how many today. Bounces on tap. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductKey(p: Product, count: Double, modifier: Modifier = Modifier, onTap: () -> Unit, onLong: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "keyBounce")
    val accent = categoryColor(p.category)
    val low = p.trackStock && p.stockQty <= p.minStock
    Box(
        modifier.aspectRatio(1f).scale(scale).glass(RoundedCornerShape(22.dp))
            // soft category glow from the top
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent), endY = 260f))
            .combinedClickable(
                interactionSource = interaction, indication = ripple(),
                onClick = onTap,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLong() },
            )
    ) {
        if (p.imageId != null) {
            EncryptedImage(p.imageId, p.name, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp, maxLines = 3,
                overflow = TextOverflow.Ellipsis, color = Color.White, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("${p.price}", style = Display.copy(fontSize = 22.sp), color = accent)
                if (low) Box(Modifier.size(8.dp).clip(CircleShape).background(KlubColors.OldDebt))
            }
        }
        if (count > 0) Text(fmtQty(count),
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                .padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 14.sp)
    }
}

@Composable
private fun Receipt(
    st: MemberStatus?, lines: List<ReceiptLine>, payments: List<LedgerEntry>, modifier: Modifier,
    onPlus: (ReceiptLine) -> Unit, onMinus: (ReceiptLine) -> Unit, onLong: (ReceiptLine) -> Unit,
) {
    LazyColumn(modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Text("Dnes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        if (lines.isEmpty()) item {
            Text("Klepni na položku", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        }
        items(lines, key = { it.key }) { l ->
            SwipeRow(
                start = if (l.productId != null) SwipeAction(Icons.Default.Add, "+1", KlubColors.PaidToday) { onPlus(l) } else null,
                end = SwipeAction(Icons.Default.Remove, "Storno", KlubColors.OldDebt) { onMinus(l) },
            ) {
                Row(Modifier.fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = { onLong(l) })
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtQty(l.qty), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(36.dp))
                    Text(l.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${l.amount}", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        items(payments, key = { "p${it.id}" }) { p ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("Zaplaceno", Modifier.weight(1f), color = KlubColors.PaidToday)
                Text("−${-p.amount}", color = KlubColors.PaidToday, fontWeight = FontWeight.SemiBold)
            }
        }
        if (st != null && st.olderDebt > 0) item {
            HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("Z minula", Modifier.weight(1f), color = KlubColors.OldDebt)
                Text("${st.olderDebt}", color = KlubColors.OldDebt, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Bottom bar: total + big pay button. Tap = choose amount, hold = pay everything. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PayBar(st: MemberStatus?, expanded: Boolean, onPay: () -> Unit, onPayAll: () -> Unit, onOpen: () -> Unit) {
    val total = st?.balance ?: 0
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).then(if (expanded) Modifier else Modifier.glass(RoundedCornerShape(26.dp))).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).then(if (expanded) Modifier else Modifier.tourTarget("receipt"))
            .clip(RoundedCornerShape(12.dp)).clickable(enabled = !expanded, onClick = onOpen).padding(8.dp)) {
            Text(if (expanded) "Celkem" else "Účet ▴", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (total < 0) "+${Credits.format(-total)}" else Credits.format(total),
                style = Display.copy(fontSize = 30.sp),
                color = when { total <= 0 -> KlubColors.PaidToday; (st?.todayUnpaid ?: 0) > 0 -> MaterialTheme.colorScheme.onSurface; else -> KlubColors.OldDebt })
        }
        val enabled = total > 0
        Box(Modifier.tourTarget("pay_button").height(60.dp).width(160.dp).clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Brush.linearGradient(listOf(KlubColors.PaidToday, Color(0xFF1FB57A))) else SolidColor(MaterialTheme.colorScheme.surfaceVariant))
            .combinedClickable(enabled = enabled, onClick = onPay, onLongClick = onPayAll),
            contentAlignment = Alignment.Center) {
            Text("Zaplatit", color = if (enabled) KlubColors.Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaySheet(
    st: MemberStatus, memberName: String, account: String, clubName: String,
    onDismiss: () -> Unit, onPay: (Long, Boolean, String) -> Unit,
) {
    var amount by remember { mutableStateOf(st.balance.toString()) }
    var leave by remember { mutableStateOf(st.inClub) }
    var method by remember { mutableStateOf("CASH") }
    var received by remember { mutableStateOf<Long?>(null) }   // cash handed over
    var editingReceived by remember { mutableStateOf(false) }
    val value = amount.toLongOrNull() ?: 0
    val iban = remember(account) { QrPayment.toIban(account) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(Credits.format(value), style = Display.copy(fontSize = 52.sp), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (st.todayUnpaid in 1 until st.balance) QuickAmount("Dnešek", Modifier.weight(1f)) { amount = st.todayUnpaid.toString(); received = null }
                QuickAmount("Vše", Modifier.weight(1f)) { amount = st.balance.toString(); received = null }
            }
            Segmented(listOf("Hotově", "QR platba"), if (method == "CASH") 0 else 1) { method = if (it == 0) "CASH" else "QR" }

            if (method == "CASH") {
                // Typical notes → change to give back
                Text("Dostal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAmount("Přesně", Modifier.weight(1f)) { received = value; editingReceived = false }
                    listOf(200L, 500L, 1000L, 2000L).filter { it >= value }.take(3).forEach { n ->
                        QuickAmount("$n", Modifier.weight(1f)) { received = n; editingReceived = false }
                    }
                    QuickAmount("…", Modifier.weight(0.6f)) { editingReceived = true; received = 0 }
                }
                Keypad(Modifier.fillMaxWidth(),
                    onDigit = { d ->
                        if (editingReceived) received = ((received ?: 0).toString().trimStart('0') + d).take(7).toLong()
                        else amount = ((if (amount == "0") "" else amount) + d).take(7)
                    },
                    onBack = {
                        if (editingReceived) received = (received ?: 0).toString().dropLast(1).toLongOrNull() ?: 0
                        else amount = amount.dropLast(1)
                    },
                    onClear = { if (editingReceived) received = 0 else amount = "" })
                received?.let { r ->
                    val change = r - value
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (change >= 0) KlubColors.PaidToday.copy(alpha = 0.18f) else KlubColors.OldDebt.copy(alpha = 0.18f))
                        .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Dostal ${Credits.format(r)}", Modifier.weight(1f))
                        Text(if (change >= 0) "Vrátit ${Credits.format(change)}" else "Chybí ${Credits.format(-change)}",
                            fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            color = if (change >= 0) KlubColors.PaidToday else KlubColors.OldDebt)
                    }
                }
            } else {
                if (iban == null) {
                    Text("Zadej číslo účtu v Nastavení → Platby, pak se tu zobrazí QR kód.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (value > 0) {
                    val payload = remember(iban, value, memberName) {
                        QrPayment.spayd(iban, value, "$clubName $memberName", variableSymbol = st.memberId)
                    }
                    val bmp = remember(payload) { QrGenerator.bitmap(payload, 720).asImageBitmap() }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Image(bmp, "QR platba",
                            Modifier.size(280.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp))
                    }
                    Text("Člen naskenuje kód bankovní aplikací. Po potvrzení platby v jeho telefonu klepni na Přijato.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { leave = !leave }) {
                Checkbox(leave, { leave = it }); Text("Odchází")
            }
            val canPay = value > 0 && (method == "CASH" || iban != null)
            Button(onClick = { onPay(value, leave, method) }, enabled = canPay,
                colors = ButtonDefaults.buttonColors(containerColor = KlubColors.PaidToday),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Text(if (method == "QR") "Přijato" else "Zaplatit", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun TransferSheet(members: List<MemberStatus>, itemName: String, onDismiss: () -> Unit, onPick: (MemberStatus) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Text("Přesunout „$itemName“ na…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyVerticalGrid(GridCells.Adaptive(96.dp), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.height(420.dp)) {
            items(members.sortedByDescending { it.inClub }, key = { it.memberId }) { m ->
                Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { onPick(m) }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        EncryptedImage(m.photoId, m.name, Modifier.size(72.dp).clip(CircleShape), textSize = 24)
                        if (m.inClub) Box(Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape).background(KlubColors.InClub))
                    }
                    Text(m.nickname ?: m.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickAmount(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(46.dp).glass(RoundedCornerShape(14.dp), alpha = 0.07f)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(label, fontWeight = FontWeight.SemiBold) }
}

/** POS-style numeric keypad. Long-press ⌫ clears. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Keypad(modifier: Modifier, onDigit: (String) -> Unit, onBack: () -> Unit, onClear: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", "⌫"))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.forEach { k ->
                    Box(Modifier.weight(1f).height(60.dp).glass(RoundedCornerShape(18.dp), alpha = 0.06f)
                        .combinedClickable(
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); if (k == "⌫") onBack() else onDigit(k) },
                            onLongClick = if (k == "⌫") ({ onClear() }) else null,
                        ), contentAlignment = Alignment.Center) {
                        Text(k, style = Display.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(entries: List<LedgerEntry>, onDismiss: () -> Unit, onVoid: (LedgerEntry) -> Unit) {
    val voided = remember(entries) { entries.mapNotNull { it.voidsEntryId }.toSet() }
    val df = remember { SimpleDateFormat("d.M. HH:mm", Locale("cs", "CZ")) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Text("Historie · táhni doleva pro storno", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().height(480.dp)) {
            items(entries.filter { it.voidsEntryId == null }, key = { it.id }) { e ->
                val isVoided = e.id in voided
                val row: @Composable () -> Unit = {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.description + if (e.quantity != 1.0) " ×${fmtQty(e.quantity)}" else "",
                                textDecoration = if (isVoided) TextDecoration.LineThrough else null,
                                color = if (isVoided) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            Text(df.format(Date(e.createdAt)), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (e.amount < 0) "−${-e.amount}" else "${e.amount}", fontWeight = FontWeight.SemiBold,
                            color = if (e.type == EntryType.PAYMENT) KlubColors.PaidToday else MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (isVoided) row() else SwipeRow(end = SwipeAction(Icons.Default.Undo, "Storno", KlubColors.OldDebt) { onVoid(e) }) { row() }
            }
        }
    }
}

@Composable
private fun CustomItemDialog(onDismiss: () -> Unit, onAdd: (String, Long) -> Unit) {
    var desc by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (price.isEmpty()) "Částka" else Credits.format(price.toLong()), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Keypad(Modifier.fillMaxWidth(), onDigit = { if (price.length < 7) price = (price + it).trimStart('0') },
                    onBack = { price = price.dropLast(1) }, onClear = { price = "" })
                OutlinedTextField(desc, { desc = it }, placeholder = { Text("Popis (nepovinné)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = (price.toLongOrNull() ?: 0) > 0, onClick = { onAdd(desc, price.toLong()) }) { Text("Zapsat") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}

@Composable
private fun QtyPriceDialog(p: Product, onDismiss: () -> Unit, onConfirm: (Double, Long) -> Unit) {
    var qty by remember { mutableStateOf(1) }
    var price by remember { mutableStateOf(p.price.toString()) }
    val pr = price.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton("−") { if (qty > 1) qty-- }
                    Text("$qty", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    StepButton("+") { qty++ }
                }
                OutlinedTextField(price, { price = it.filter(Char::isDigit) }, label = { Text("Cena / ks") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (pr != null) Text("= ${Credits.format(pr * qty)}", fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { TextButton(enabled = pr != null, onClick = { onConfirm(qty.toDouble(), pr!!) }) { Text("Zapsat") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Text(label, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
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
