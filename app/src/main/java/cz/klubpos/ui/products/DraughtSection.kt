package cz.klubpos.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.inventory.KegGauge
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.ui.theme.glass
import kotlinx.coroutines.launch

/** Beers on tap: add a new one, end a keg ("bez sudu"), or remove the beer from the keg tracking. */
@Composable
fun DraughtSection(canEdit: Boolean, onRequestUnlock: () -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val groups by repo.kegGroups().collectAsState(initial = emptyList())
    val active by repo.activeKegs().collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).glass(RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🍺  Čepované", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            // keg with a plus = add a beer on tap
            androidx.compose.foundation.layout.Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable { if (canEdit) adding = true else onRequestUnlock() },
                contentAlignment = Alignment.Center) {
                KegGauge(1f, Modifier.size(24.dp, 30.dp))
                Text("+", Modifier.align(Alignment.TopEnd), color = KlubColors.Gold, fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium)
            }
        }
        if (groups.isEmpty()) Text("Žádné čepované pivo. Přidej ho ikonou sudu s plusem.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        groups.forEach { g ->
            val k = active.firstOrNull { it.kegGroup == g }
            Row(verticalAlignment = Alignment.CenterVertically) {
                KegGauge(if (k == null) 0f else ((k.sizeLiters - k.pouredLiters) / k.sizeLiters).toFloat(), Modifier.size(26.dp, 34.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(g, fontWeight = FontWeight.SemiBold)
                    Text(if (k == null) "bez sudu" else "naraženo", style = MaterialTheme.typography.bodySmall,
                        color = if (k == null) KlubColors.Muted else KlubColors.PaidToday)
                }
                if (k != null) TextButton({ scope.launch { repo.finishKeg(g) } }) { Text("Dopito – bez sudu") }
                TextButton({ if (canEdit) removing = g else onRequestUnlock() }) { Text("Odebrat", color = KlubColors.OldDebt) }
            }
        }
        Text("Nový sud narazíš ve Skladu. Položky čepovaného (velké / malé) jsou níže.", style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted)
    }

    if (adding) AddDraughtDialog(onDismiss = { adding = false }) { name, size, big, small ->
        scope.launch { repo.addDraughtBeer(name, size, big, small) }; adding = false
    }
    removing?.let { g ->
        AlertDialog(onDismissRequest = { removing = null },
            title = { Text("Odebrat „$g“ ze sudů?") },
            text = { Text("Zmizí ukazatel sudu na Home a záznam sudů ve sklepě. Položky tohoto piva zůstanou v nabídce jako běžné zboží – můžeš je pak skrýt nebo upravit.") },
            confirmButton = { TextButton({ scope.launch { repo.removeDraughtBeer(g) }; removing = null }) { Text("Odebrat", color = KlubColors.OldDebt) } },
            dismissButton = { TextButton({ removing = null }) { Text("Zpět") } })
    }
}

@Composable
private fun AddDraughtDialog(onDismiss: () -> Unit, onAdd: (String, Double, Long, Long?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(50.0) }
    var big by remember { mutableStateOf("") }
    var small by remember { mutableStateOf("") }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nové pivo ze sudu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Název (např. Plzeň 12°)") }, singleLine = true)
                Text("Obvyklá velikost sudu", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15.0, 30.0, 50.0).forEach { s -> FilterChip(size == s, { size = s }, label = { Text("${s.toInt()} l") }) }
                }
                OutlinedTextField(big, { big = it.filter(Char::isDigit) }, label = { Text("Cena velkého 0,5 l (${cz.klubpos.domain.Credits.SYMBOL})") }, singleLine = true, keyboardOptions = num)
                OutlinedTextField(small, { small = it.filter(Char::isDigit) }, label = { Text("Cena malého 0,3 l (prázdné = nečepuje se)") }, singleLine = true, keyboardOptions = num)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && big.toLongOrNull() != null, onClick = {
            onAdd(name, size, big.toLong(), small.toLongOrNull())
        }) { Text("Přidat") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}
