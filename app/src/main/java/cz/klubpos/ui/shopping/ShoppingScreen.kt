package cz.klubpos.ui.shopping

import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import cz.klubpos.ui.theme.glass
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import cz.klubpos.ui.tour.tourTarget
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.klubpos.data.db.entity.ShoppingItem
import cz.klubpos.ui.components.SwipeAction
import cz.klubpos.ui.components.SwipeRow
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.inventory.KeypadDialog
import cz.klubpos.ui.member.fmtQty
import cz.klubpos.ui.theme.KlubColors
import kotlinx.coroutines.launch

/**
 * Nákup – a plain checklist.
 *  type + Enter  add ("6 citrony" = 6 ks), suggestions from stock
 *  circle        bought (adds to stock)          swipe ←  remove
 *  − / +         change amount, tap number = keypad
 *  grouped by supplier, bought items folded at the bottom
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ShoppingScreen() {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val items by repo.shoppingList().collectAsState(initial = emptyList())
    val products by repo.allProducts().collectAsState(initial = emptyList())
    val suppliers by repo.suppliers().collectAsState(initial = emptyList())
    val stock by repo.stockItems().collectAsState(initial = emptyList())
    val open = items.filter { !it.done }
    val done = items.filter { it.done }
    var qtyFor by remember { mutableStateOf<ShoppingItem?>(null) }
    var order by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val supName = { id: Long? -> suppliers.firstOrNull { it.id == id }?.name }
    if (order) cz.klubpos.ui.suppliers.OrderContactsSheet("Objednat zboží", shareText(open, supName), onDismiss = { order = false })

    fun add(text: String) {
        val t = text.trim(); if (t.isEmpty()) return
        val m = Regex("^(\\d+(?:[.,]\\d+)?)\\s*(?:x|×|ks)?\\s+(.+)$").find(t)
        val qty = m?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: 1.0
        val name = m?.groupValues?.get(2) ?: t
        val si = stock.firstOrNull { it.name.equals(name, true) }
        scope.launch { repo.addShoppingItem(si?.name ?: name, qty, si?.unitLabel ?: "ks") }
        input = ""
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(20.dp, 10.dp, 6.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Nákup", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (open.isNotEmpty()) Text("${open.size}", color = KlubColors.Muted, modifier = Modifier.padding(end = 6.dp))
                IconButton(modifier = Modifier.tourTarget("autofill"), onClick = {
                    scope.launch {
                        val n = repo.generateShoppingList()
                        snackbar.showSnackbar(if (n > 0) "Doplněno do cílového stavu: $n" else "Sklad je na cílovém stavu")
                    }
                }) { Icon(Icons.Default.AutoFixHigh, "Doplnit do cílového stavu", tint = KlubColors.Muted) }
                IconButton(enabled = open.isNotEmpty(), onClick = { order = true }) { Icon(Icons.Default.Call, "Objednat u dodavatele", tint = KlubColors.Muted) }
                IconButton(enabled = open.isNotEmpty(), onClick = {
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Nákupní seznam")
                            .putExtra(Intent.EXTRA_TEXT, shareText(open, supName)), "Odeslat nákupní seznam"))
                }) { Icon(Icons.Default.Share, "Odeslat", tint = KlubColors.Muted) }
            }
            // quick add
            Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp, 16.dp, 0.dp).clip(RoundedCornerShape(12.dp))
                .background(KlubColors.SurfaceHigh.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = KlubColors.Muted)
                Box(Modifier.weight(1f).padding(start = 10.dp)) {
                    if (input.isEmpty()) Text("Přidat… (např. 6 citrony)", color = KlubColors.Muted)
                    androidx.compose.foundation.text.BasicTextField(input, { input = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(KlubColors.Gold),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { add(input) }),
                        modifier = Modifier.fillMaxWidth())
                }
                if (input.isNotBlank()) Text("Přidat", Modifier.clickable { add(input) }.padding(start = 8.dp), color = KlubColors.Gold, fontWeight = FontWeight.SemiBold)
            }
            val q = input.trim().replace(Regex("^\\d+\\s*"), "")
            val sugg = if (q.length < 2) emptyList() else stock.filter { it.name.contains(q, true) && open.none { o -> o.name.equals(it.name, true) } }.take(6)
            if (sugg.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(Modifier.padding(16.dp, 6.dp, 16.dp, 0.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                sugg.forEach { si -> Text(si.name, Modifier.clickable { scope.launch { repo.addShoppingItem(si.name, 1.0, si.unitLabel) }; input = "" }
                    .padding(vertical = 4.dp), color = KlubColors.Gold, style = MaterialTheme.typography.bodyMedium) }
            }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 24.dp)) {
                if (items.isEmpty()) item { Text("Seznam je prázdný.", Modifier.padding(12.dp), color = KlubColors.Muted) }
                open.groupBy { it.supplierId }.toList().sortedBy { (id, _) -> supName(id) ?: "\uFFFF" }.forEach { (sid, list) ->
                    item(key = "h$sid") {
                        Text((supName(sid) ?: "Bez dodavatele").uppercase(), Modifier.padding(12.dp, 12.dp, 12.dp, 2.dp),
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp), color = KlubColors.Muted)
                    }
                    itemsIndexed(list, key = { _, it -> it.id }) { i, item ->
                        SwipeRow(modifier = if (i == 0) Modifier.tourTarget("shopping_row") else Modifier,
                            end = SwipeAction(Icons.Default.Delete, "Smazat", KlubColors.OldDebt) { scope.launch { repo.deleteShoppingItem(item.id) } },
                        ) {
                            ItemRow(item, onToggle = { scope.launch { repo.setShoppingDone(item, true, receive = true) } },
                                onQty = { qtyFor = item },
                                onStep = { d -> scope.launch { repo.updateShoppingQty(item, (item.quantity + d).coerceAtLeast(1.0)) } })
                        }
                    }
                }
                if (done.isNotEmpty()) item(key = "done") {
                    Row(Modifier.fillMaxWidth().padding(12.dp, 14.dp, 4.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Koupeno ${done.size} ${if (showDone) "▴" else "▾"}", Modifier.weight(1f).clickable { showDone = !showDone }.padding(vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge, color = KlubColors.Muted)
                        TextButton({ scope.launch { repo.clearBought() } }) { Text("Vyčistit", color = KlubColors.Muted) }
                    }
                }
                if (showDone) items(done, key = { it.id }) { item ->
                    ItemRow(item, onToggle = { scope.launch { repo.setShoppingDone(item, false, receive = false) } }, onQty = {}, onStep = {})
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    qtyFor?.let { item ->
        KeypadDialog(item.name, fmtQty(item.quantity), item.unit, onDismiss = { qtyFor = null }) { q ->
            scope.launch { repo.updateShoppingQty(item, q) }; qtyFor = null
        }
    }
}

@Composable
private fun ItemRow(item: ShoppingItem, onToggle: () -> Unit, onQty: () -> Unit, onStep: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
            Box(Modifier.size(20.dp).clip(CircleShape)
                .then(if (item.done) Modifier.background(KlubColors.PaidToday)
                      else Modifier.border(1.5.dp, KlubColors.Muted, CircleShape)), contentAlignment = Alignment.Center) {
                if (item.done) Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = KlubColors.Ink)
            }
        }
        Text(item.name, Modifier.weight(1f).padding(start = 4.dp, top = 10.dp, bottom = 10.dp),
            textDecoration = if (item.done) TextDecoration.LineThrough else null, maxLines = 2,
            color = if (item.done) KlubColors.Muted else MaterialTheme.colorScheme.onSurface)
        if (!item.done) Text("−", Modifier.clip(CircleShape).clickable { onStep(-1.0) }.padding(horizontal = 10.dp, vertical = 6.dp), color = KlubColors.Muted, fontSize = 18.sp)
        Text("${fmtQty(item.quantity)} ${item.unit}", Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !item.done, onClick = onQty)
            .padding(horizontal = 4.dp, vertical = 6.dp), fontWeight = FontWeight.SemiBold,
            color = if (item.done) KlubColors.Muted else MaterialTheme.colorScheme.onSurface)
        if (!item.done) Text("+", Modifier.clip(CircleShape).clickable { onStep(1.0) }.padding(horizontal = 10.dp, vertical = 6.dp), color = KlubColors.Muted, fontSize = 18.sp)
    }
}

/** WhatsApp/SMS/e-mail friendly list grouped by supplier. */
private fun shareText(items: List<ShoppingItem>, supName: (Long?) -> String?): String {
    val date = java.text.SimpleDateFormat("d. M. yyyy", java.util.Locale("cs", "CZ")).format(java.util.Date())
    return buildString {
        append("Nákup – ").append(date).append("\n")
        items.groupBy { supName(it.supplierId) ?: "Ostatní" }.toSortedMap().forEach { (sup, list) ->
            append("\n").append(sup.uppercase()).append("\n")
            list.forEach { append("☐ ").append(it.name).append(" — ").append(fmtQty(it.quantity)).append(" ").append(it.unit).append("\n") }
        }
    }
}
