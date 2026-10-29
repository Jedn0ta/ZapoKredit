package cz.klubpos.ui.shopping

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * Shopping list.
 *  swipe →  bought (and received into stock)
 *  swipe ←  remove
 *  tap qty  change amount
 */
@Composable
fun ShoppingScreen() {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val items by repo.shoppingList().collectAsState(initial = emptyList())
    val open = items.filter { !it.done }
    val done = items.filter { it.done }
    var showAdd by remember { mutableStateOf(false) }
    var qtyFor by remember { mutableStateOf<ShoppingItem?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(20.dp, 12.dp, 8.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Nákup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(modifier = Modifier.tourTarget("autofill"), onClick = {
                    scope.launch {
                        val n = repo.generateShoppingList()
                        snackbar.showSnackbar(if (n > 0) "Doplněno ze skladu: $n" else "Nic dalšího nedochází")
                    }
                }) { Icon(Icons.Default.AutoFixHigh, "Doplnit podle skladu") }
                IconButton(enabled = open.isNotEmpty(), onClick = {
                    val text = open.joinToString("\n") { "• ${it.name} – ${fmtQty(it.quantity)} ${it.unit}" }
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Nákup:\n$text"), null))
                }) { Icon(Icons.Default.Share, "Sdílet") }
                IconButton({ showAdd = true }) { Icon(Icons.Default.Add, "Přidat") }
            }
            if (items.isEmpty()) Text("Prázdné", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(open, key = { _, it -> it.id }) { i, item ->
                    SwipeRow(
                        modifier = if (i == 0) Modifier.tourTarget("shopping_row") else Modifier,
                        start = SwipeAction(Icons.Default.Check, "Koupeno", KlubColors.PaidToday) {
                            scope.launch { repo.setShoppingDone(item, true, receive = true) }
                        },
                        end = SwipeAction(Icons.Default.Delete, "Smazat", KlubColors.OldDebt) {
                            scope.launch { repo.deleteShoppingItem(item.id) }
                        },
                    ) { ItemRow(item, onQty = { qtyFor = item }) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                }
                if (done.isNotEmpty()) item {
                    Row(Modifier.fillMaxWidth().padding(20.dp, 20.dp, 8.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Koupeno", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton({ scope.launch { repo.clearBought() } }) { Text("Vyčistit") }
                    }
                }
                items(done, key = { it.id }) { item ->
                    // Swipe back if ticked by mistake (stock is not reverted automatically).
                    SwipeRow(start = SwipeAction(Icons.AutoMirrored.Filled.Undo, "Vrátit", KlubColors.Amber) {
                        scope.launch { repo.setShoppingDone(item, false, receive = false) }
                    }) { ItemRow(item, onQty = {}) }
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
    if (showAdd) AddDialog(onDismiss = { showAdd = false }) { name ->
        scope.launch { repo.addShoppingItem(name, 1.0, "ks") }; showAdd = false
    }
}

@Composable
private fun ItemRow(item: ShoppingItem, onQty: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (item.done) Box(Modifier.size(8.dp).clip(CircleShape).background(KlubColors.PaidToday))
        Text(item.name, Modifier.weight(1f).padding(start = if (item.done) 12.dp else 0.dp, top = 16.dp, bottom = 16.dp),
            fontSize = 17.sp,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        Text("${fmtQty(item.quantity)} ${item.unit}",
            Modifier.clip(CircleShape).clickable(enabled = !item.done, onClick = onQty).padding(horizontal = 12.dp, vertical = 8.dp),
            fontWeight = FontWeight.Bold, fontSize = 17.sp,
            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AddDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Přidat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, placeholder = { Text("Např. ubrousky") }, singleLine = true)
                Text("Množství upravíš klepnutím v seznamu.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onAdd(name) }) { Text("Přidat") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}
