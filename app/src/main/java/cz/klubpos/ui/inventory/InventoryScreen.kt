package cz.klubpos.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.klubpos.data.db.entity.Product
import cz.klubpos.ui.components.NumberDialog
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.member.fmtQty
import cz.klubpos.ui.theme.KlubColors
import kotlinx.coroutines.launch

/** Stock overview: what's left, what's low, receive goods, physical count. */
@OptIn(ExperimentalMaterial3Api::class)
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

    val shown = tracked.filter { !onlyLow || it.stockQty <= it.minStock }
        .sortedWith(compareBy({ it.stockQty > it.minStock }, { it.category }, { it.name }))
    val lowCount = tracked.count { it.stockQty <= it.minStock }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Sklad") }, actions = {
                TextButton(onClick = {
                    scope.launch {
                        val n = repo.generateShoppingList()
                        snackbar.showSnackbar(if (n > 0) "Do nákupu přidáno: $n" else "Nákupní seznam je aktuální")
                    }
                }) { Text("→ Nákup") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!onlyLow, { onlyLow = false }, label = { Text("Vše (${tracked.size})") })
                FilterChip(onlyLow, { onlyLow = true }, label = { Text("Dochází ($lowCount)") })
            }
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.id }) { p ->
                    val low = p.stockQty <= p.minStock
                    Card(colors = CardDefaults.cardColors(
                        containerColor = if (low) KlubColors.OldDebt.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.SemiBold)
                                    Text(p.category, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${fmtQty(p.stockQty)} ${p.unit}", style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold, color = if (low) KlubColors.OldDebt else MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(Modifier.height(8.dp))
                            val target = (p.minStock * 2).coerceAtLeast(1.0)
                            LinearProgressIndicator(
                                progress = { (p.stockQty / target).toFloat().coerceIn(0f, 1f) },
                                color = if (low) KlubColors.OldDebt else KlubColors.PaidToday,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("minimum ${fmtQty(p.minStock)} ${p.unit}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton({ receiveFor = p }) { Text("+ Naskladnit") }
                                OutlinedButton({ countFor = p }) { Text("Inventura") }
                            }
                        }
                    }
                }
            }
        }
    }

    receiveFor?.let { p ->
        NumberDialog("Naskladnit: ${p.name}", label = "Přijaté množství (${p.unit})", decimal = true,
            onDismiss = { receiveFor = null }) { q -> scope.launch { repo.receiveStock(p.id, q) }; receiveFor = null }
    }
    countFor?.let { p ->
        NumberDialog("Inventura: ${p.name}", initial = fmtQty(p.stockQty), label = "Skutečný stav (${p.unit})", decimal = true,
            onDismiss = { countFor = null }) { q -> scope.launch { repo.setCountedStock(p.id, q) }; countFor = null }
    }
}

