package cz.klubpos.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cz.klubpos.data.db.entity.Bartender
import cz.klubpos.domain.BusinessDay
import cz.klubpos.domain.Credits
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.components.rememberSettings
import cz.klubpos.ui.login.Avatar
import cz.klubpos.ui.member.fmtQty
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.ui.theme.glass
import kotlinx.coroutines.launch

/** Staff list, today's sales per bartender, auto-lock. Editing requires owner unlock ([canEdit]). */
@Composable
fun BartendersSection(canEdit: Boolean, onRequestUnlock: () -> Unit) {
    val repo = rememberRepo()
    val settings = rememberSettings()
    val cfg by settings.flow.collectAsState()
    val scope = rememberCoroutineScope()
    val all by repo.allBartenders().collectAsState(initial = emptyList())
    val dayStart = remember { BusinessDay.startMillis() }
    val sales by remember { repo.bartenderSales(dayStart) }.collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Bartender?>(null) }

    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (all.isEmpty()) Text("Zatím žádní barmani – aplikace nevyžaduje přihlášení. Přidej prvního a od té doby se " +
            "každý zápis a platba uloží pod jeho jménem.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        all.forEach { b ->
            val s = sales.firstOrNull { it.bartenderId == b.id }
            Row(Modifier.fillMaxWidth().clickable(enabled = canEdit) { editing = b }, verticalAlignment = Alignment.CenterVertically) {
                Avatar(b, 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.name + if (!b.active) " (neaktivní)" else "", fontWeight = FontWeight.SemiBold)
                    Text(if (s == null) "dnes nic" else "dnes ${fmtQty(s.items)} ks · přijato ${Credits.format(s.paid)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Credits.format(s?.charged ?: 0), style = MaterialTheme.typography.titleMedium, color = KlubColors.Gold)
            }
        }
        sales.firstOrNull { it.bartenderId == null && it.charged != 0L }?.let {
            Text("Bez přihlášení: ${Credits.format(it.charged)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Zamknout po nečinnosti", Modifier.weight(1f))
            listOf(0 to "Ne", 2 to "2 min", 5 to "5 min", 15 to "15").forEach { (m, l) ->
                TextButton({ settings.update { it.copy(autoLockMinutes = m) } }) {
                    Text(l, color = if (cfg.autoLockMinutes == m) KlubColors.Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (cfg.autoLockMinutes == m) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        OutlinedButton(onClick = { if (canEdit) adding = true else onRequestUnlock() }) {
            Text(if (canEdit) "+ Přidat barmana" else "🔒 Odemknout správu")
        }
    }

    if (adding) BartenderDialog(null, onDismiss = { adding = false }) { name, pin, _ ->
        scope.launch { repo.addBartender(name, pin!!) }; adding = false
    }
    editing?.let { b ->
        BartenderDialog(b, onDismiss = { editing = null }) { _, pin, active ->
            scope.launch {
                if (pin != null) repo.setBartenderPin(b, pin)
                if (active != b.active) repo.setBartenderActive(b, active)
            }
            editing = null
        }
    }
}

@Composable
private fun BartenderDialog(b: Bartender?, onDismiss: () -> Unit, onSave: (String, String?, Boolean) -> Unit) {
    var name by remember { mutableStateOf(b?.name ?: "") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(b?.active ?: true) }
    val pinOk = pin.length in 4..6 && pin == pin2
    val valid = if (b == null) name.isNotBlank() && pinOk else (pin.isEmpty() || pinOk)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(b?.name ?: "Nový barman") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (b == null) OutlinedTextField(name, { name = it }, label = { Text("Jméno") }, singleLine = true)
                val kb = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                OutlinedTextField(pin, { if (it.length <= 6) pin = it.filter(Char::isDigit) }, singleLine = true,
                    label = { Text(if (b == null) "PIN (4–6 číslic)" else "Nový PIN (nepovinné)") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = kb)
                OutlinedTextField(pin2, { if (it.length <= 6) pin2 = it.filter(Char::isDigit) }, singleLine = true,
                    label = { Text("PIN znovu") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = kb,
                    isError = pin2.isNotEmpty() && pin != pin2)
                if (b != null) TextButton({ active = !active }) {
                    Text(if (active) "Deaktivovat barmana" else "Znovu aktivovat", color = if (active) KlubColors.OldDebt else KlubColors.PaidToday)
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(name, pin.ifEmpty { null }, active) }) { Text("Uložit") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}
