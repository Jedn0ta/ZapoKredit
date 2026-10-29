package cz.klubpos.ui.shopping

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cz.klubpos.data.db.entity.ShoppingItem
import cz.klubpos.ui.components.NumberDialog
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.member.fmtQty
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen() {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val items by repo.shoppingList().collectAsState(initial = emptyList())
    var receiveOnCheck by remember { mutableStateOf(true) }
    var showAdd by remember { mutableStateOf(false) }
    var qtyFor by remember { mutableStateOf<ShoppingItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Nákupní seznam") }, actions = {
                IconButton(onClick = {
                    scope.launch {
                        val n = repo.generateShoppingList()
                        snackbar.showSnackbar(if (n > 0) "Přidáno ze skladu: $n" else "Nic dalšího nedochází")
                    }
                }) { Icon(Icons.Default.AutoFixHigh, "Doplnit podle skladu") }
                IconButton(onClick = {
                    val text = items.filter { !it.done }.joinToString("\n") { "• ${it.name} – ${fmtQty(it.quantity)} ${it.unit}" }
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Nákup do klubu:\n$text"), "Sdílet"))
                }) { Icon(Icons.Default.Share, "Sdílet") }
                IconButton(onClick = { scope.launch { repo.clearBought() } }) { Icon(Icons.Default.Delete, "Smazat koupené") }
            })
        },
        floatingActionButton = { FloatingActionButton({ showAdd = true }) { Icon(Icons.Default.Add, "Přidat") } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(receiveOnCheck, { receiveOnCheck = it })
                Text("  Odškrtnutím rovnou naskladnit", style = MaterialTheme.typography.bodyMedium)
            }
            if (items.isEmpty()) Text("Seznam je prázdný. Ikonou ✨ doplníš vše, co dochází.",
                Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(items, key = { it.id }) { item ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.done, { checked ->
                            scope.launch { repo.setShoppingDone(item, checked, receiveOnCheck) }
                        })
                        Text(item.name, Modifier.weight(1f),
                            textDecoration = if (item.done) TextDecoration.LineThrough else null,
                            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        Text("${fmtQty(item.quantity)} ${item.unit}",
                            Modifier.clickable(enabled = !item.done) { qtyFor = item }.padding(12.dp),
                            color = MaterialTheme.colorScheme.primary)
                        IconButton({ scope.launch { repo.deleteShoppingItem(item.id) } }) { Icon(Icons.Default.Delete, "Odebrat") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }

    qtyFor?.let { item ->
        NumberDialog("Množství: ${item.name}", initial = fmtQty(item.quantity), label = item.unit, decimal = true,
            onDismiss = { qtyFor = null }) { q -> scope.launch { repo.updateShoppingQty(item, q) }; qtyFor = null }
    }
    if (showAdd) AddDialog(onDismiss = { showAdd = false }) { name, q, unit ->
        scope.launch { repo.addShoppingItem(name, q, unit) }; showAdd = false
    }
}

@Composable
private fun AddDialog(onDismiss: () -> Unit, onAdd: (String, Double, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("ks") }
    val q = qty.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Přidat do nákupu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Co (např. ubrousky)") }, singleLine = true)
                OutlinedTextField(qty, { qty = it }, label = { Text("Množství") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(unit, { unit = it }, label = { Text("Jednotka") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && q != null && q > 0, onClick = { onAdd(name, q!!, unit) }) { Text("Přidat") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}
