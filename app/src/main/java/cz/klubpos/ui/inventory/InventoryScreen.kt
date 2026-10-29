package cz.klubpos.ui.inventory

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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(20.dp, 12.dp, 16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sklad", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Segmented(listOf("Vše", "Dochází $lowCount"), if (onlyLow) 1 else 0) { onlyLow = it == 1 }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(shown, key = { _, p -> p.id }) { i, p ->
                    val low = p.stockQty <= p.minStock
                    SwipeRow(
                        modifier = if (i == 0) Modifier.tourTarget("stock_row") else Modifier,
                        start = SwipeAction(Icons.Default.MoveToInbox, "Naskladnit", KlubColors.PaidToday) { receiveFor = p },
                        end = SwipeAction(Icons.Default.AddShoppingCart, "Do nákupu", KlubColors.Amber) {
                            scope.launch {
                                val added = repo.addProductToShopping(p.id)
                                snackbar.showSnackbar(if (added) "${p.name} → nákup" else "${p.name} už v nákupu je")
                            }
                        },
                    ) {
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).clickable { countFor = p }
                            .padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColor(p.category)))
                            Spacer(Modifier.width(14.dp))
                            Text(p.name, Modifier.weight(1f), fontSize = 17.sp)
                            Text(fmtQty(p.stockQty), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                color = if (low) KlubColors.OldDebt else MaterialTheme.colorScheme.onSurface)
                            Text(" ${p.unit}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
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
