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
import androidx.compose.foundation.lazy.itemsIndexed as itemsIndexedList
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
import cz.klubpos.data.repo.ClubRepository
import cz.klubpos.domain.displayName
import androidx.compose.runtime.mutableStateListOf
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
import cz.klubpos.ui.components.reorderableGrid
import cz.klubpos.ui.components.reorderItem
import cz.klubpos.domain.QrPayment
import cz.klubpos.upload.QrGenerator
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.itemsIndexed
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.heightIn
import cz.klubpos.domain.kindEnum
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
    val debt by remember { repo.debtInfo() }.collectAsState(initial = emptyMap())
    val products by repo.activeProducts().collectAsState(initial = emptyList())
    val ledger by repo.ledger(memberId).collectAsState(initial = emptyList())
    val (lines, payments) = remember(ledger) { buildReceipt(ledger, dayStart) }

    // The order being put together – written to the tab only with "Na futro" (or before paying).
    val cart = remember { mutableStateListOf<CartLine>() }
    // X+Y deals: free pieces are not charged
    val cartTotal = cart.sumOf { Math.round(it.unitPrice * (it.qty - (it.product?.freeOf(it.qty) ?: 0))) }
    val cartCount = remember(cart.toList()) { cart.filter { it.product != null }.groupBy { it.product!!.id }.mapValues { e -> e.value.sumOf { it.qty } } }

    var showPay by remember { mutableStateOf(false) }
    var payPreset by remember { mutableStateOf<Long?>(null) }
    var showCustom by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showReceiptSheet by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var qtyFor by remember { mutableStateOf<Product?>(null) }
    var transferLine by remember { mutableStateOf<ReceiptLine?>(null) }
    var qtyLine by remember { mutableStateOf<CartLine?>(null) }
    var cartMenu by remember { mutableStateOf<CartLine?>(null) }
    var lineMenu by remember { mutableStateOf<ReceiptLine?>(null) }
    var transferAll by remember { mutableStateOf(false) }
    var cartTransfer by remember { mutableStateOf<CartLine?>(null) }
    val settings by rememberSettings().flow.collectAsState()
    // Favourites = what THIS member orders most often.
    val top by remember(memberId) { repo.memberFavorites(memberId) }.collectAsState(initial = emptyList())
    val favorites = remember(top, products) { top.mapNotNull { t -> products.firstOrNull { it.id == t.productId } } }
    var lastLabel by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(ledger) { lastLabel = repo.lastChargeLabel(memberId) }

    fun addToCart(p: Product?, qty: Double = 1.0, price: Long? = null, desc: String? = null) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val unit = price ?: p?.currentPrice() ?: 0
        val name = desc ?: p?.displayName ?: "Vlastní položka"
        // same thing again (also via "To samé") = only the count goes up
        val i = cart.indexOfFirst { it.unitPrice == unit && (if (p != null) it.product?.id == p.id else it.product == null && it.desc == name) }
        if (i >= 0) cart[i] = cart[i].copy(qty = cart[i].qty + qty)
        else cart += CartLine(p, name, unit, qty)
    }
    /** Set the exact count of a product in the order (0 = remove it). Price always comes from Položky. */
    fun setQty(p: Product, q: Int) {
        cart.removeAll { it.product?.id == p.id }
        if (q > 0) cart += CartLine(p, p.displayName, p.currentPrice(), q.toDouble())
    }
    fun removeOne(l: CartLine) {
        val i = cart.indexOf(l); if (i < 0) return
        if (l.qty > 1) cart[i] = l.copy(qty = l.qty - 1) else cart.removeAt(i)
    }
    suspend fun commitCart() {
        if (cart.isEmpty()) return
        val items = cart.map { ClubRepository.CartItem(it.product?.id, it.desc, it.unitPrice, it.qty) }
        cart.clear()
        repo.chargeCart(memberId, items)
    }
    /** "Budiž": write the order to the tab and go back to the members. */
    fun futro() = scope.launch { haptic.performHapticFeedback(HapticFeedbackType.LongPress); commitCart(); onBack() }
    fun pay() = scope.launch { payPreset = null; commitCart(); showPay = true }
    /** Comes, buys, pays right away: write just this order and pay exactly its amount. */
    fun payNow() = scope.launch { payPreset = cartTotal; commitCart(); showPay = true }
    /** "Na futro": write the order as debt, the member leaves the club (visit closed), back to members. */
    fun futroLeave() = scope.launch {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        commitCart()
        repo.closeVisit(memberId)
        onBack()
    }
    fun voidWithUndo(entryId: Long, label: String) = scope.launch {
        repo.void(entryId)
        snackbar.currentSnackbarData?.dismiss()
        if (snackbar.showSnackbar("Storno: $label", actionLabel = "Vrátit") == SnackbarResult.ActionPerformed) {
            ledger.firstOrNull { it.id == entryId }?.let { e ->
                if (e.productId != null) repo.chargeProduct(memberId, e.productId, e.quantity, e.unitPrice)
                else repo.chargeCustom(memberId, e.description, e.amount)
            }
        }
    }
    fun repeat() = scope.launch {
        repo.lastChargeEntry(memberId)?.let { e ->
            val p = products.firstOrNull { it.id == e.productId }
            addToCart(p, e.quantity, p?.currentPrice() ?: e.unitPrice, if (p != null) null else e.description)
        }
    }

    // Leaving with an unsaved order → ask.
    androidx.activity.compose.BackHandler(enabled = cart.isNotEmpty()) { confirmLeave = true }
    val back: () -> Unit = { if (cart.isNotEmpty()) confirmLeave = true else onBack() }

    val receipt: @Composable (Modifier, Boolean) -> Unit = { mod, cartOnly ->
        Receipt(st, cart, lines, payments, mod, lastLabel, cartOnly, onRepeat = { repeat() }, onPayNow = { payNow() },
            onCartPlus = { l -> addToCart(l.product, 1.0, l.unitPrice, l.desc) },
            onCartMinus = { l -> removeOne(l) },
            onCartLong = { l -> cartMenu = l },
            onPlus = { l -> products.firstOrNull { it.id == l.productId }?.let { addToCart(it) } },
            onMinus = { l -> voidWithUndo(l.entryIds.first(), l.name) },
            onLong = { l -> lineMenu = l })
    }
    val bar: @Composable (Boolean) -> Unit = { expanded ->
        OrderBar(st, cartTotal, cart.sumOf { it.qty }, expanded,
            onPay = { pay() }, onFutro = { futroLeave() }, onBudiz = { futro() }, onOpen = { showReceiptSheet = true })
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopRow(member?.nickname ?: member?.name ?: "", member?.photoId, st, member?.avatar, debt[memberId],
                onBack = back, onName = { showEdit = true }, onHistory = { showHistory = true })
            val limit = settings.debtLimit
            if (limit > 0 && st != null && st.balance >= limit) {
                Text("Překročen limit dluhu ${Credits.format(limit)}",
                    Modifier.fillMaxWidth().background(KlubColors.OldDebt).padding(horizontal = 20.dp, vertical = 8.dp),
                    color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                if (maxWidth >= 720.dp) {
                    Row(Modifier.fillMaxSize()) {
                        ProductGrid(products, favorites, lastLabel, cartCount, Modifier.weight(1f).fillMaxHeight(),
                            onTap = { addToCart(it) }, onLong = { qtyFor = it }, onCustom = { showCustom = true }, onRepeat = { repeat() },
                            onMinus = { p -> cart.firstOrNull { it.product?.id == p.id }?.let { removeOne(it) } })
                        Column(Modifier.width(380.dp).fillMaxHeight().padding(12.dp).glass(RoundedCornerShape(28.dp))) {
                            receipt(Modifier.weight(1f).tourTarget("receipt"), false)
                            bar(true)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        ProductGrid(products, favorites, lastLabel, cartCount, Modifier.weight(1f),
                            onTap = { addToCart(it) }, onLong = { qtyFor = it }, onCustom = { showCustom = true }, onRepeat = { repeat() },
                            onMinus = { p -> cart.firstOrNull { it.product?.id == p.id }?.let { removeOne(it) } })
                        // phone: the new order is always visible right above the buttons
                        receipt(Modifier.fillMaxWidth().padding(horizontal = 12.dp).glass(RoundedCornerShape(20.dp))
                            .heightIn(max = 210.dp), true)
                        bar(false)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp))
    }

    if (showReceiptSheet) ModalBottomSheet(onDismissRequest = { showReceiptSheet = false },
        containerColor = MaterialTheme.colorScheme.surface) {
        receipt(Modifier.fillMaxWidth().height(460.dp), false)
    }
    if (showPay && st != null) PaySheet(st, member?.nickname ?: member?.name ?: "", settings.account, settings.clubName, payPreset,
        onDismiss = { showPay = false }) { amount, leave, method ->
        scope.launch { repo.pay(memberId, amount, leave, method) }
        showPay = false; onBack()
    }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text("Neuložená objednávka") },
        text = { Text("${fmtQty(cart.sumOf { it.qty })} položek za ${Credits.format(cartTotal)}. Připsat na účet?") },
        confirmButton = { TextButton({ confirmLeave = false; futro() }) { Text("Budiž – připsat") } },
        dismissButton = {
            Row {
                TextButton({ confirmLeave = false; cart.clear(); onBack() }) { Text("Zahodit", color = KlubColors.OldDebt) }
                TextButton({ confirmLeave = false }) { Text("Zpět") }
            }
        },
    )
    if (showCustom) CustomItemDialog(onDismiss = { showCustom = false }) { desc, price ->
        addToCart(null, 1.0, price, desc.ifBlank { "Vlastní položka" }); showCustom = false
    }
    qtyFor?.let { p ->
        val current = cart.filter { it.product?.id == p.id }.sumOf { it.qty }
        QtyDialog(p.name, current, onDismiss = { qtyFor = null }) { q -> setQty(p, q); qtyFor = null }
    }
    qtyLine?.let { l ->
        QtyDialog(l.desc, l.qty, onDismiss = { qtyLine = null }) { q ->
            val i = cart.indexOf(l)
            if (i >= 0) { if (q <= 0) cart.removeAt(i) else cart[i] = l.copy(qty = q.toDouble()) }
            qtyLine = null
        }
    }
    transferLine?.let { l ->
        TransferSheet(statuses.filter { it.memberId != memberId }, l.name, onDismiss = { transferLine = null }) { to ->
            val ids = if (transferAll) l.entryIds else listOf(l.entryIds.first())
            scope.launch { ids.forEach { repo.transfer(it, to.memberId) }; snackbar.showSnackbar("${l.name} → ${to.nickname ?: to.name}") }
            transferLine = null
        }
    }
    cartTransfer?.let { l ->
        TransferSheet(statuses.filter { it.memberId != memberId }, l.desc, onDismiss = { cartTransfer = null }) { to ->
            cart.remove(l)
            scope.launch {
                repo.chargeCart(to.memberId, listOf(ClubRepository.CartItem(l.product?.id, l.desc, l.unitPrice, l.qty)))
                snackbar.showSnackbar("${fmtQty(l.qty)}× ${l.desc} → ${to.nickname ?: to.name}")
            }
            cartTransfer = null
        }
    }
    // tap on a line of the new order
    cartMenu?.let { l ->
        LineActions(l.desc, fmtQty(l.qty), onDismiss = { cartMenu = null }, actions = listOf(
            "Změnit počet" to { qtyLine = l },
            "Přesunout někomu jinému" to { cartTransfer = l },
            "Odebrat z objednávky" to { cart.remove(l); Unit },
        ))
    }
    // tap on a line of today's account
    lineMenu?.let { l ->
        LineActions(l.name, fmtQty(l.qty), onDismiss = { lineMenu = null }, actions = listOfNotNull(
            (if (l.productId != null) "Přidat 1 ks" to { products.firstOrNull { it.id == l.productId }?.let { p ->
                scope.launch { repo.chargeProduct(memberId, p.id, 1.0, l.unitPrice) } }; Unit } else null),
            "Storno 1 položky" to { voidWithUndo(l.entryIds.first(), l.name); Unit },
            (if (l.entryIds.size > 1) "Storno celého řádku" to { scope.launch { l.entryIds.forEach { repo.void(it) } }; Unit } else null),
            "Přesunout 1 položku někomu" to { transferAll = false; transferLine = l },
            (if (l.entryIds.size > 1) "Přesunout celý řádek někomu" to { transferAll = true; transferLine = l } else null),
        ))
    }
    if (showHistory) HistorySheet(ledger, onDismiss = { showHistory = false }) { e -> voidWithUndo(e.id, e.description) }
    if (showEdit) member?.let { m ->
        EditMemberDialog(m.name, m.nickname ?: "", m.note ?: "", m.vip, onDismiss = { showEdit = false },
            onArchive = { scope.launch { repo.updateMember(m.copy(active = false)) }; showEdit = false; onBack() }) { n, nick, note, vip ->
            scope.launch { repo.updateMember(m.copy(name = n, nickname = nick.ifBlank { null }, note = note.ifBlank { null }, vip = vip)) }
            showEdit = false
        }
    }
}

/** A line of the order being prepared (not written yet). */
private data class CartLine(val product: Product?, val desc: String, val unitPrice: Long, val qty: Double)

@Composable
private fun TopRow(name: String, photoId: String?, st: MemberStatus?, avatar: String?, debt: ClubRepository.DebtInfo?, onBack: () -> Unit, onName: () -> Unit, onHistory: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") }
        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onName).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box {
                cz.klubpos.ui.components.MemberAvatar(photoId, avatar, name, null, Modifier.size(44.dp).clip(CircleShape), textSize = 16)
                if (st?.inClub == true) Box(Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape).background(KlubColors.InClub))
            }
            Spacer(Modifier.width(12.dp))
            Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Today / total debt – tap for the full history (what and when)
        Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onHistory).tourTarget("history")
            .padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.End) {
            Text(if ((st?.olderDebt ?: 0) > 0 && debt != null)
                    "dluh od " + SimpleDateFormat("d. M.", Locale("cs", "CZ")).format(Date(debt.since)) + (debt.bartender?.let { " · $it" } ?: "")
                else "dnes ${st?.todaySpend ?: 0} · dluh", style = MaterialTheme.typography.labelSmall, color = KlubColors.Muted)
            Text(Credits.format((st?.balance ?: 0).coerceAtLeast(0)), style = Display.copy(fontSize = 20.sp),
                color = when { (st?.balance ?: 0) <= 0 -> KlubColors.PaidToday; (st?.olderDebt ?: 0) > 0 -> KlubColors.OldDebt; else -> KlubColors.OpenTab })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductGrid(
    products: List<Product>, favorites: List<Product>, lastLabel: String?, counts: Map<Long, Double>, modifier: Modifier,
    onTap: (Product) -> Unit, onLong: (Product) -> Unit, onCustom: () -> Unit, onRepeat: () -> Unit,
    onMinus: (Product) -> Unit,
) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val appSettings = cz.klubpos.ui.components.rememberSettings()
    val sort = appSettings.flow.collectAsState().value.productSort
    val products = remember(products, sort) {
        when (sort) {
            "ALPHA" -> products.sortedBy { it.name.lowercase() }
            "CUSTOM" -> products.sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))
            else -> products.sortedWith(compareBy({ it.category.lowercase() }, { it.name.lowercase() }))
        }
    }
    // pills = sections of the drink menu that have something in them
    val categories = remember(products) { cz.klubpos.domain.Kind.entries.filter { k -> products.any { it.kindEnum == k } }.map { it.label } }
    val now = cz.klubpos.ui.components.rememberMinuteClock()
    // Default view = everything; "★ Oblíbené" shows what this member usually has.
    var cat by remember { mutableStateOf<String?>(null) }
    Column(modifier) {
        if (categories.size > 1 || favorites.isNotEmpty()) Row(Modifier.tourTarget("favorites").horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryPill("Vše", null, cat == null) { cat = null }
            if (favorites.isNotEmpty()) CategoryPill("★ Oblíbené", KlubColors.Amber, cat == FAV) { cat = FAV }
            categories.forEach { c -> CategoryPill(c, null, cat == c) { cat = c } }
        }
        val list = when (cat) { null -> products; FAV -> favorites; else -> products.filter { it.kindEnum.label == cat } }
        // Hold & drag a key to move it (in "Vše"); hold without moving → exact quantity.
        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        val reorder = cz.klubpos.ui.components.rememberGridReorderState(gridState)
        val local = remember { mutableStateListOf<Product>() }
        androidx.compose.runtime.LaunchedEffect(list) { if (reorder.draggingKey == null) { local.clear(); local.addAll(list) } }
        LazyVerticalGrid(
            GridCells.Adaptive(112.dp),
            state = gridState,
            modifier = Modifier.reorderableGrid(reorder,
                canDrag = { k -> cat == null && k is Long },
                onMove = { from, to ->
                    val a = local.indexOfFirst { it.id == from }; val b = local.indexOfFirst { it.id == to }
                    if (a >= 0 && b >= 0) local.add(b, local.removeAt(a))
                },
                onDrop = {
                    val ids = local.map { it.id }
                    scope.launch { repo.saveProductOrder(ids) }
                    if (sort != "CUSTOM") appSettings.update { it.copy(productSort = "CUSTOM") }
                },
                onLongPress = { k -> local.firstOrNull { it.id == k }?.let(onLong) }),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(local, key = { _, p -> p.id }) { i, p ->
                ProductKey(p, counts[p.id] ?: 0.0, promo = p.currentPrice(now) != p.price,
                    (if (i == 0) Modifier.tourTarget("product_key") else Modifier).reorderItem(reorder, p.id),
                    onTap = { onTap(p) }, onMinus = { onMinus(p) })
            }
            item(key = "custom") {
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
private fun ProductKey(p: Product, count: Double, promo: Boolean = false, modifier: Modifier = Modifier, onTap: () -> Unit, onMinus: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "keyBounce")
    val accent = categoryColor(p.category)
    val low = p.trackStock && p.stockQty <= p.minStock
    val badge = when { p.promoActive() -> "HH"; p.saleActive() -> "AKCE"; p.dealActive() -> "${p.dealBuy}+${p.dealFree}"; else -> null }
    Box(
        modifier.aspectRatio(1f).scale(scale).glass(RoundedCornerShape(22.dp))
            // soft category glow from the top
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent), endY = 260f))
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onTap)
    ) {
        if (p.imageId != null) {
            EncryptedImage(p.imageId, p.name, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(p.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp, maxLines = 3,
                overflow = TextOverflow.Ellipsis, color = Color.White, modifier = Modifier.padding(top = 4.dp, end = if (badge != null) 34.dp else 0.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                if (promo) Row(verticalAlignment = Alignment.Bottom) {
                    Text("${p.currentPrice()}", style = Display.copy(fontSize = 22.sp), color = KlubColors.Gold)
                    Text(" ${p.price}", style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted,
                        textDecoration = TextDecoration.LineThrough)
                } else Text("${p.price}", style = Display.copy(fontSize = 22.sp), color = accent)
                if (count > 0) Row(Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onMinus).padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Remove, "Ubrat", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(fmtQty(count), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                } else if (low) Box(Modifier.size(8.dp).clip(CircleShape).background(KlubColors.OldDebt))
            }
        }
        if (badge != null) Text(badge, Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(6.dp))
            .background(KlubColors.GoldBrush).padding(horizontal = 5.dp, vertical = 1.dp),
            color = KlubColors.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
    }
}

@Composable
private fun Receipt(
    st: MemberStatus?, cart: List<CartLine>, lines: List<ReceiptLine>, payments: List<LedgerEntry>, modifier: Modifier,
    lastLabel: String?, cartOnly: Boolean, onRepeat: () -> Unit, onPayNow: () -> Unit,
    onCartPlus: (CartLine) -> Unit, onCartMinus: (CartLine) -> Unit, onCartLong: (CartLine) -> Unit,
    onPlus: (ReceiptLine) -> Unit, onMinus: (ReceiptLine) -> Unit, onLong: (ReceiptLine) -> Unit,
) {
    @Composable
    fun header(t: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
        Text(t, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

    LazyColumn(modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { header("Nová objednávka", KlubColors.Gold) }
                if (cart.isNotEmpty()) Text("Zaplatit hned", Modifier.padding(end = 8.dp).clip(RoundedCornerShape(14.dp))
                    .background(KlubColors.PaidToday.copy(alpha = 0.18f)).clickable(onClick = onPayNow).padding(horizontal = 12.dp, vertical = 6.dp),
                    color = KlubColors.PaidToday, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                if (lastLabel != null) Text("↻ To samé", Modifier.clip(RoundedCornerShape(14.dp)).glass(RoundedCornerShape(14.dp))
                    .clickable(onClick = onRepeat).padding(horizontal = 12.dp, vertical = 6.dp),
                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            }
        }
        itemsIndexedList(cart, key = { i, _ -> "cart$i" }) { _, l ->
            SwipeRow(
                start = SwipeAction(Icons.Default.Add, "+1", KlubColors.PaidToday) { onCartPlus(l) },
                end = SwipeAction(Icons.Default.Remove, "−1", KlubColors.OldDebt) { onCartMinus(l) },
            ) {
                Row(Modifier.fillMaxWidth().combinedClickable(onClick = { onCartLong(l) }, onLongClick = { onCartLong(l) })
                    .padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ onCartMinus(l) }) { Icon(Icons.Default.Remove, "Ubrat", tint = KlubColors.Muted) }
                    Text(fmtQty(l.qty), style = Display.copy(fontSize = 18.sp), color = KlubColors.Gold, modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.Center)
                    IconButton({ onCartPlus(l) }) { Icon(Icons.Default.Add, "Přidat", tint = KlubColors.Muted) }
                    val free = l.product?.freeOf(l.qty) ?: 0
                    Column(Modifier.weight(1f)) {
                        Text(l.desc, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (free > 0) Text("$free zdarma", color = KlubColors.PaidToday, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("${Math.round(l.unitPrice * (l.qty - free))}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
        if (cartOnly) return@LazyColumn
        if (lines.isNotEmpty() || payments.isNotEmpty()) item {
            HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
            header("Dnes už má na účtu")
        }
        items(lines, key = { it.key }) { l ->
            SwipeRow(
                start = if (l.productId != null) SwipeAction(Icons.Default.Add, "+1", KlubColors.PaidToday) { onPlus(l) } else null,
                end = SwipeAction(Icons.Default.Remove, "Storno", KlubColors.OldDebt) { onMinus(l) },
            ) {
                Row(Modifier.fillMaxWidth()
                    .combinedClickable(onClick = { onLong(l) }, onLongClick = { onLong(l) })
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtQty(l.qty), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
                    Text(l.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${l.amount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(payments, key = { "p${it.id}" }) { p ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(p.description, Modifier.weight(1f), color = KlubColors.PaidToday)
                Text("−${-p.amount}", color = KlubColors.PaidToday, fontWeight = FontWeight.SemiBold)
            }
        }
        if (st != null && st.olderDebt > 0) item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Dluh z minula", Modifier.weight(1f), color = KlubColors.OldDebt)
                Text("${st.olderDebt}", color = KlubColors.OldDebt, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Bottom bar:
 *  Zaplatit – write the order, then pay
 *  Na futro – write the order as debt, member leaves the club, back to members
 *  Budiž    – write the order, member stays in the club, back to members
 */
@Composable
private fun OrderBar(
    st: MemberStatus?, cartTotal: Long, cartQty: Double, expanded: Boolean,
    onPay: () -> Unit, onFutro: () -> Unit, onBudiz: () -> Unit, onOpen: () -> Unit,
) {
    val hasCart = cartQty > 0
    val canPay = hasCart || (st?.balance ?: 0) > 0
    val canFutro = hasCart || st?.inClub == true
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)
        .then(if (expanded) Modifier else Modifier.glass(RoundedCornerShape(26.dp))).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = !expanded, onClick = onOpen).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (!expanded) Text("účet dnes ▴", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (hasCart) "+ ${Credits.format(cartTotal)}" else "dluh ${Credits.format((st?.balance ?: 0).coerceAtLeast(0))}",
                    style = Display.copy(fontSize = 26.sp), color = if (hasCart) KlubColors.Gold else MaterialTheme.colorScheme.onSurface)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BarButton("Zaplatit", Modifier.weight(1f).tourTarget("pay_button"), canPay, KlubColors.PaidToday, filled = false, onClick = onPay)
            BarButton("Na futro", Modifier.weight(1f).tourTarget("futro_button"), canFutro, KlubColors.OpenTab, filled = false, onClick = onFutro)
            BarButton("Budiž", Modifier.weight(1.2f).tourTarget("budiz_button"), true, KlubColors.Gold, filled = true, onClick = onBudiz)
        }
    }
}

@Composable
private fun BarButton(label: String, modifier: Modifier, enabled: Boolean, color: Color, filled: Boolean, onClick: () -> Unit) {
    Box(modifier.height(58.dp)
        .then(if (filled && enabled) Modifier.clip(RoundedCornerShape(16.dp)).background(KlubColors.GoldBrush)
              else Modifier.glass(RoundedCornerShape(16.dp), alpha = if (enabled) 0.08f else 0.02f))
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = when { !enabled -> MaterialTheme.colorScheme.onSurfaceVariant; filled -> KlubColors.Ink; else -> color },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp), maxLines = 1)
    }
}

@Composable
private fun PaySheet(
    st: MemberStatus, memberName: String, account: String, clubName: String, preset: Long?,
    onDismiss: () -> Unit, onPay: (Long, Boolean, String) -> Unit,
) {
    // null = not typed yet → default follows live status (today's spend, or the whole debt).
    var amountText by remember { mutableStateOf<String?>(null) }
    // which quick choice is active: ORDER / TODAY / ALL, null = typed by hand
    var choice by remember { mutableStateOf<String?>(if (preset != null) "ORDER" else if (st.todayUnpaid > 0) "TODAY" else "ALL") }
    val amount = amountText ?: when (choice) {
        "ORDER" -> preset ?: 0; "TODAY" -> st.todayUnpaid; else -> st.balance.coerceAtLeast(0)
    }.toString()
    var leave by remember { mutableStateOf(st.inClub && preset == null) }
    var method by remember { mutableStateOf("CASH") }
    var received by remember { mutableStateOf<Long?>(null) }   // cash handed over
    var editingReceived by remember { mutableStateOf(false) }
    val value = amount.toLongOrNull() ?: 0
    val iban = remember(account) { QrPayment.toIban(account) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(Credits.format(value), style = Display.copy(fontSize = 52.sp), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text("Dnes ${Credits.format(st.todayUnpaid)} · dluh celkem ${Credits.format(st.balance.coerceAtLeast(0))} · méně zadej na klávesnici",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (preset != null) QuickAmount("Objednávka $preset", Modifier.weight(1f), choice == "ORDER") { choice = "ORDER"; amountText = null; received = null }
                if (st.todayUnpaid > 0) QuickAmount("Dnešek ${st.todayUnpaid}", Modifier.weight(1f), choice == "TODAY") { choice = "TODAY"; amountText = null; received = null }
                if (st.balance > st.todayUnpaid) QuickAmount("Celý dluh ${st.balance}", Modifier.weight(1f), choice == "ALL") { choice = "ALL"; amountText = null; received = null }
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
                        else { amountText = ((if (amountText == null) "" else amount) + d).trimStart('0').take(7); choice = null }
                    },
                    onBack = {
                        if (editingReceived) received = (received ?: 0).toString().dropLast(1).toLongOrNull() ?: 0
                        else { amountText = amount.dropLast(1); choice = null }
                    },
                    onClear = { if (editingReceived) received = 0 else { amountText = ""; choice = null } })
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineActions(title: String, qty: String, onDismiss: () -> Unit, actions: List<Pair<String, () -> Unit>>) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("$qty× $title", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            actions.forEach { (label, act) ->
                Text(label, Modifier.fillMaxWidth().clickable { onDismiss(); act() }.padding(horizontal = 24.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (label.startsWith("Storno") || label.startsWith("Odebrat")) KlubColors.OldDebt else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun QuickAmount(label: String, modifier: Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Box(modifier.height(46.dp)
        .then(if (selected) Modifier.clip(RoundedCornerShape(14.dp)).background(KlubColors.GoldBrush) else Modifier.glass(RoundedCornerShape(14.dp), alpha = 0.07f))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = if (selected) KlubColors.Ink else MaterialTheme.colorScheme.onSurface)
    }
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
                        Text(if (e.type == EntryType.CHARGE) fmtQty(e.quantity) else "", Modifier.width(32.dp),
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(e.description,
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

/** Exact quantity: stepper + keypad. 0 removes the item. */
@Composable
private fun QtyDialog(title: String, current: Double, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var qty by remember { mutableStateOf(current.toInt()) }
    var fresh by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton("−") { if (qty > 0) qty--; fresh = false }
                    Text("$qty", style = Display.copy(fontSize = 44.sp))
                    StepButton("+") { qty++; fresh = false }
                }
                Keypad(Modifier.fillMaxWidth(),
                    onDigit = { d -> qty = ((if (fresh) "" else qty.toString()) + d).trimStart('0').take(3).toIntOrNull() ?: 0; fresh = false },
                    onBack = { qty = qty.toString().dropLast(1).toIntOrNull() ?: 0; fresh = false },
                    onClear = { qty = 0; fresh = false })
                if (qty == 0) Text("0 = odebrat z objednávky", color = KlubColors.OldDebt, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ onConfirm(qty) }) { Text("OK") } },
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
    name0: String, nick0: String, note0: String, vip0: Boolean,
    onDismiss: () -> Unit, onArchive: () -> Unit, onSave: (String, String, String, Boolean) -> Unit,
) {
    var vip by remember { mutableStateOf(vip0) }
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
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { vip = !vip }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("♛  VIP člen (zlatý rám)", Modifier.weight(1f), color = if (vip) KlubColors.Gold else MaterialTheme.colorScheme.onSurface)
                    androidx.compose.material3.Switch(vip, { vip = it })
                }
                TextButton(onClick = onArchive) { Text("Archivovat člena", color = KlubColors.OldDebt) }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, nick, note, vip) }) { Text("Uložit") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}

fun fmtQty(q: Double): String = if (q % 1.0 == 0.0) q.toLong().toString() else "%.2f".format(q).trimEnd('0').trimEnd(',', '.')
