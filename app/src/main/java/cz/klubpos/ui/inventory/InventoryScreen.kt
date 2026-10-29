package cz.klubpos.ui.inventory

import cz.klubpos.domain.displayName
import cz.klubpos.ui.theme.Display
import cz.klubpos.ui.components.EncryptedImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import cz.klubpos.ui.theme.glass
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import cz.klubpos.ui.tour.tourTarget
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.klubpos.data.db.entity.Product
import cz.klubpos.ui.components.SwipeAction
import cz.klubpos.ui.components.SwipeRow
import cz.klubpos.ui.components.categoryColor
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.member.Keypad
import cz.klubpos.ui.member.fmtQty
import cz.klubpos.ui.members.Segmented
import cz.klubpos.ui.theme.KlubColors
import kotlinx.coroutines.launch

/**
 * Stock list.
 *  swipe →  receive goods (naskladnit)
 *  swipe ←  add to shopping list
 *  tap      physical count (inventura)
 */
@Composable
fun InventoryScreen() {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val all by repo.activeProducts().collectAsState(initial = emptyList())
    val tracked = all.filter { it.trackStock }
    var onlyLow by remember { mutableStateOf(false) }
    var receiveFor by remember { mutableStateOf<Product?>(null) }
    var countFor by remember { mutableStateOf<Product?>(null) }

    val lowCount = tracked.count { it.stockQty <= it.minStock }
    val shown = tracked.filter { !onlyLow || it.stockQty <= it.minStock }
        .sortedWith(compareBy({ it.stockQty > it.minStock }, { it.category }, { it.name }))

    var actionsFor by remember { mutableStateOf<Product?>(null) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Game-style inventory header
            Row(Modifier.fillMaxWidth().padding(20.dp, 12.dp, 16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Inventář", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${tracked.size} předmětů · $lowCount dochází", style = MaterialTheme.typography.labelMedium,
                        color = if (lowCount > 0) KlubColors.OldDebt else KlubColors.Muted)
                }
                Segmented(listOf("Vše", "Dochází $lowCount"), if (onlyLow) 1 else 0) { onlyLow = it == 1 }
            }
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // "Equipment": kegs on tap
                if (!onlyLow) item(key = "kegs", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { KegsSection() }
                item(key = "bag", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text("🎒 Batoh", Modifier.padding(start = 4.dp, top = 6.dp), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                itemsIndexed(shown, key = { _, p -> p.id }) { i, p ->
                    InventorySlot(p, if (i == 0) Modifier.tourTarget("stock_row") else Modifier) { actionsFor = p }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    actionsFor?.let { p ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { actionsFor = null },
            title = { Text(p.displayName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stav: ${fmtQty(p.stockQty)} ${p.unit} · minimum ${fmtQty(p.minStock)}", color = KlubColors.Muted)
                    SlotAction("📦  Naskladnit") { receiveFor = p; actionsFor = null }
                    SlotAction("🔍  Inventura (skutečný stav)") { countFor = p; actionsFor = null }
                    SlotAction("🛒  Do nákupu") {
                        scope.launch {
                            val added = repo.addProductToShopping(p.id)
                            snackbar.showSnackbar(if (added) "${p.name} → nákup" else "${p.name} už v nákupu je")
                        }
                        actionsFor = null
                    }
                }
            },
            confirmButton = { TextButton({ actionsFor = null }) { Text("Zavřít") } },
        )
    }

    receiveFor?.let { p ->
        KeypadDialog("Naskladnit · ${p.name}", "", p.unit, onDismiss = { receiveFor = null }) { q ->
            scope.launch { repo.receiveStock(p.id, q) }; receiveFor = null
        }
    }
    countFor?.let { p ->
        KeypadDialog("Inventura · ${p.name}", fmtQty(p.stockQty), p.unit, onDismiss = { countFor = null }) { q ->
            scope.launch { repo.setCountedStock(p.id, q) }; countFor = null
        }
    }
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


@Composable
private fun SlotAction(label: String, onClick: () -> Unit) {
    Text(label, Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp),
        style = MaterialTheme.typography.titleSmall)
}

/**
 * RPG-style inventory slot: dark inset square, item art (photo or emoji), quantity in the corner,
 * rarity-like frame colour: red glow = running out, gold = well stocked, grey = normal.
 */
@Composable
private fun InventorySlot(p: Product, modifier: Modifier, onClick: () -> Unit) {
    val low = p.stockQty <= p.minStock
    val rich = p.minStock > 0 && p.stockQty >= p.minStock * 3
    val frame = when { low -> KlubColors.OldDebt; rich -> KlubColors.Gold; else -> Color(0xFF5A5566) }
    val shape = RoundedCornerShape(14.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)
            .shadow(if (low || rich) 14.dp else 0.dp, shape, ambientColor = frame, spotColor = frame)
            .clip(shape)
            .background(Brush.radialGradient(listOf(Color(0xFF2A2633), Color(0xFF121016))))
            .border(2.dp, Brush.linearGradient(listOf(frame, frame.copy(alpha = 0.35f), frame)), shape)
            .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            if (p.imageId != null) EncryptedImage(p.imageId, p.name, Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(8.dp)))
            else Text(slotEmoji(p), fontSize = 40.sp)
            // inner bevel
            Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.06f), shape))
            Text(fmtQty(p.stockQty), Modifier.align(Alignment.BottomEnd).padding(6.dp),
                style = Display.copy(fontSize = 20.sp), color = if (low) KlubColors.OldDebt else Color.White)
            if (low) Text("!", Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 4.dp),
                color = KlubColors.OldDebt, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
        Text(p.name, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
        Text(p.unit + if (p.bottleMl > 0) " ${cz.klubpos.domain.volumeLabel(p.bottleMl)}" else "", style = MaterialTheme.typography.labelSmall, color = KlubColors.Muted)
    }
}

private fun slotEmoji(p: Product): String {
    val n = p.name.lowercase() + " " + p.category.lowercase()
    return when {
        "pivo" in n || "beer" in n -> "🍺"
        "víno" in n || "vino" in n || "wine" in n -> "🍷"
        "rum" in n || "vodk" in n || "whisk" in n || "gin" in n || "slivov" in n || "fernet" in n || "becher" in n -> "🥃"
        "kofol" in n || "cola" in n || "limo" in n || "džus" in n || "dzus" in n -> "🥤"
        "minerál" in n || "voda" in n -> "💧"
        "chips" in n || "brambůr" in n -> "🥔"
        "utopen" in n || "klobás" in n || "párek" in n -> "🌭"
        "oříšk" in n || "arašíd" in n -> "🥜"
        "káva" in n || "kafe" in n -> "☕"
        else -> cz.klubpos.domain.Kind.of(p.kind).emoji
    }
}
