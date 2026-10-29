package cz.klubpos.ui.products

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Položky") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Product(name = "", price = 0) }) { Icon(Icons.Default.Add, "Nová položka") }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            var lastCat: String? = null
            products.forEach { p ->
                if (p.category != lastCat) {
                    lastCat = p.category
                    item(key = "cat-${p.category}-${p.id}") {
                        Text(p.category, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                    }
                }
                item(key = p.id) {
                    Row(Modifier.fillMaxWidth().clickable { editing = p }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        EncryptedImage(p.imageId, p.name, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)), textSize = 16)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name + if (!p.active) " (skryto)" else "", fontWeight = FontWeight.SemiBold)
                            Text(if (p.trackStock) "sklad ${fmtQty(p.stockQty)} ${p.unit} · min ${fmtQty(p.minStock)}" else "bez skladové evidence",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Credits.format(p.price), fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }

    editing?.let { p ->
        ProductDialog(p, onDismiss = { editing = null }, onImage = { imageFor = p; editing = null }) { updated ->
            scope.launch { repo.saveProduct(updated) }; editing = null
        }
    }
    imageFor?.let { p -> QrUploadDialog("Obrázek: ${p.name}", onImage = { repo.setProductImage(p.id, it) }) { imageFor = null } }
}

@Composable
private fun ProductDialog(p: Product, onDismiss: () -> Unit, onImage: () -> Unit, onSave: (Product) -> Unit) {
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (p.id == 0L) "Nová položka" else "Upravit položku") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Název") }, singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text("Kategorie") }, singleLine = true)
                OutlinedTextField(price, { price = it.filter(Char::isDigit) }, label = { Text("Cena (kr)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(track, { track = it }); Spacer(Modifier.width(8.dp)); Text("Sledovat sklad") }
                if (track) {
                    OutlinedTextField(unit, { unit = it }, label = { Text("Jednotka (ks, l, kg…)") }, singleLine = true)
                    OutlinedTextField(min, { min = it }, label = { Text("Minimální stav (pod ním → nákup)") }, singleLine = true, keyboardOptions = num)
                    OutlinedTextField(reorder, { reorder = it }, label = { Text("Obvykle nakupuji (0 = automaticky)") }, singleLine = true, keyboardOptions = num)
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(active, { active = it }); Spacer(Modifier.width(8.dp)); Text("Aktivní (nabízet barmanovi)") }
                if (p.id != 0L) OutlinedButton(onClick = onImage) { Text("Nahrát obrázek přes QR") }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && price.toLongOrNull() != null, onClick = {
                onSave(p.copy(name = name.trim(), category = category.trim().ifBlank { "Ostatní" }, price = price.toLong(),
                    trackStock = track, unit = unit.ifBlank { "ks" }, minStock = d(min), reorderQty = d(reorder), active = active))
            }) { Text("Uložit") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}
