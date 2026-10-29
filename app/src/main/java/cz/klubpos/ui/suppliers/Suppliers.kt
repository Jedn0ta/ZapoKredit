package cz.klubpos.ui.suppliers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.klubpos.data.db.entity.Supplier
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.ui.theme.glass
import kotlinx.coroutines.launch

/** Settings section: list + edit of supplier contacts. */
@Composable
fun SuppliersSection() {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val list by repo.suppliers().collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Supplier?>(null) }
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (list.isEmpty()) Text("Kontakty na pivovar, velkoobchod… Pak je najdeš v Nákupu a u sudu pod tlačítkem Objednat.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.forEach { s ->
            Column(Modifier.fillMaxWidth().clickable { editing = s }.padding(vertical = 4.dp)) {
                Text(s.name, fontWeight = FontWeight.SemiBold)
                Text(listOf(s.phone, s.email, s.note).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton({ editing = Supplier(name = "") }) { Text("+ Přidat dodavatele") }
    }
    editing?.let { s ->
        SupplierDialog(s, onDismiss = { editing = null },
            onDelete = if (s.id != 0L) ({ scope.launch { repo.deleteSupplier(s.id) }; editing = null }) else null) { u ->
            scope.launch { repo.saveSupplier(u) }; editing = null
        }
    }
}

@Composable
private fun SupplierDialog(s: Supplier, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (Supplier) -> Unit) {
    var name by remember { mutableStateOf(s.name) }
    var phone by remember { mutableStateOf(s.phone) }
    var email by remember { mutableStateOf(s.email) }
    var note by remember { mutableStateOf(s.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (s.id == 0L) "Nový dodavatel" else s.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Název (např. Pivovar Plzeň)") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Telefon") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(note, { note = it }, label = { Text("Poznámka (co dodává, kdy vozí…)") })
                if (onDelete != null) TextButton(onDelete) { Text("Smazat", color = KlubColors.OldDebt) }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            onSave(s.copy(name = name.trim(), phone = phone.trim(), email = email.trim(), note = note.trim()))
        }) { Text("Uložit") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}

/**
 * "Objednat" sheet: pick a supplier and call / SMS / e-mail them.
 * [message] is prefilled into SMS and e-mail (e.g. the shopping list or "sud Plzeň 50 l").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderContactsSheet(title: String, message: String, onDismiss: () -> Unit) {
    val repo = rememberRepo()
    val context = LocalContext.current
    val list by repo.suppliers().collectAsState(initial = emptyList())
    fun go(i: Intent) = runCatching { context.startActivity(i) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (list.isEmpty()) Text("Zatím žádní dodavatelé. Přidej je v Nastavení → Dodavatelé.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.forEach { s ->
                Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontWeight = FontWeight.SemiBold)
                        if (s.note.isNotBlank()) Text(s.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (s.phone.isNotBlank()) {
                        IconButton({ go(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${s.phone}"))) }) { Icon(Icons.Default.Call, "Zavolat", tint = KlubColors.PaidToday) }
                        IconButton({ go(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${s.phone}")).putExtra("sms_body", message)) }) {
                            Icon(Icons.Default.Sms, "SMS", tint = KlubColors.InClub)
                        }
                    }
                    if (s.email.isNotBlank()) IconButton({
                        go(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${s.email}"))
                            .putExtra(Intent.EXTRA_SUBJECT, "Objednávka").putExtra(Intent.EXTRA_TEXT, message))
                    }) { Icon(Icons.Default.Email, "E-mail", tint = KlubColors.Gold) }
                }
            }
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        }
    }
}
