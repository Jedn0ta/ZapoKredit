package cz.klubpos.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cz.klubpos.eet.Eet
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.ui.theme.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** EET 2.0: settings, certificate, test send and the list of reported payments. */
@Composable
fun EetSettings() {
    val app = cz.klubpos.LocalServices.current
    val eet = app.eet
    val cfg by eet.config.collectAsState()
    val records by remember { app.repository.eetDao().observeRecent(30) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pickCert = cz.klubpos.platform.rememberFilePicker(arrayOf("application/x-pkcs12", "application/octet-stream", "*/*")) { _, bytes -> pending = bytes }
    val certInfo = remember(cfg.certB64, cfg.certPassword) { runCatching { eet.loadCert(cfg) }.getOrNull() }

    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Evidovat platby do EET", Modifier.weight(1f))
            Switch(cfg.enabled, { v -> eet.update { it.copy(enabled = v) } })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(cfg.playground, { eet.update { it.copy(playground = true) } }, label = { Text("Testovací prostředí") })
            FilterChip(!cfg.playground, { eet.update { it.copy(playground = false) } }, label = { Text("Ostrý provoz") })
        }
        Text(if (cfg.playground) "Zprávy jdou na testovací server Finanční správy (playground). Nic se neeviduje, kód POK končí „-ff“. Použij testovací certifikát."
             else "Ostrý provoz – platby se skutečně evidují. Použij svůj certifikát z portálu MOJE daně.",
            style = MaterialTheme.typography.bodySmall, color = if (cfg.playground) KlubColors.Muted else KlubColors.OpenTab)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ověřovací mód")
                Text("server jen zkontroluje zprávu, tržbu nezaeviduje", style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted)
            }
            Switch(cfg.verification, { v -> eet.update { it.copy(verification = v) } })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(if (cfg.idJednotky == 0) "" else cfg.idJednotky.toString(),
                { v -> eet.update { it.copy(idJednotky = v.filter(Char::isDigit).take(9).toIntOrNull() ?: 0) } },
                label = { Text("Evidenční jednotka") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(cfg.idPokl, { v -> eet.update { it.copy(idPokl = v.take(20)) } },
                label = { Text("Označení pokladny") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Text("Číslo evidenční jednotky přidělí portál MOJE daně (končí číslicí 1–4). Pro test lze použít třeba 303.",
            style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted)

        // certificate
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (certInfo != null) "Certifikát: ${certInfo.eic}" else "Certifikát není nahraný")
                if (certInfo != null) Text("platný do " + SimpleDateFormat("d. M. yyyy", Locale("cs", "CZ")).format(certInfo.validTo),
                    style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted)
            }
            OutlinedButton({ pickCert() }) { Text("Nahrát .p12") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy && certInfo != null, onClick = {
                busy = true
                scope.launch {
                    val r = eet.sendTest(1)
                    info = describe(r); busy = false
                }
            }) { Text(if (busy) "Odesílám…" else "Odeslat testovací tržbu 1 Kč") }
            if (records.any { it.status == "PENDING" }) TextButton({ scope.launch { withContext(Dispatchers.IO) { eet.retryAll() } } }) { Text("Odeslat čekající") }
        }

        // recent records
        if (records.isNotEmpty()) {
            Text("Poslední evidované platby", style = MaterialTheme.typography.labelLarge, color = KlubColors.Muted)
            records.take(12).forEach { r ->
                val color = when (r.status) { "OK", "CHECKED" -> KlubColors.PaidToday; "PENDING" -> KlubColors.OpenTab; else -> KlubColors.OldDebt }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                    info = "Pořadové číslo ${r.poradCis}\n${r.datTrzby}\n${r.amount} Kč\n" +
                        (r.pok?.let { "POK: $it\n" } ?: "") + (r.error?.let { "Chyba: $it\n" } ?: "") + (r.warnings?.let { "Varování: $it\n" } ?: "") +
                        "Pokusů: ${r.attempts}" + (if (r.playground) "\n(testovací prostředí)" else "") + (if (r.verification) "\n(ověřovací mód)" else "")
                }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(8.dp))
                    Text(SimpleDateFormat("d. M. HH:mm", Locale("cs", "CZ")).format(Date(r.createdAt)), Modifier.width(90.dp),
                        style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted)
                    Text("${r.amount} Kč", Modifier.width(70.dp), style = MaterialTheme.typography.bodySmall)
                    Text(when (r.status) { "OK" -> r.pok?.takeLast(6) ?: "OK"; "CHECKED" -> "ověřeno"; "PENDING" -> "čeká"; else -> "chyba" },
                        style = MaterialTheme.typography.bodySmall, color = color, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    pending?.let { bytes ->
        var pass by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { pending = null }, title = { Text("Heslo k certifikátu") },
            text = {
                Column {
                    OutlinedTextField(pass, { pass = it; err = null }, singleLine = true, label = { Text("Heslo") },
                        visualTransformation = PasswordVisualTransformation())
                    err?.let { Text(it, color = KlubColors.OldDebt, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton({
                runCatching { eet.importCert(bytes, pass) }
                    .onSuccess { eic -> pending = null; info = "Certifikát nahrán – EIČ $eic" }
                    .onFailure { err = "Certifikát nejde otevřít – zkontroluj heslo" }
            }) { Text("Nahrát") } },
            dismissButton = { TextButton({ pending = null }) { Text("Zrušit") } })
    }
    info?.let { msg ->
        AlertDialog(onDismissRequest = { info = null }, title = { Text("EET") }, text = { Text(msg) },
            confirmButton = { TextButton({ info = null }) { Text("OK") } })
    }
}

private fun describe(r: Eet.Result): String = when {
    r.ok -> "Přijato. POK:\n${r.pok}" + (if (r.test) "\n\n(testovací prostředí – kód není platný)" else "") +
        (if (r.warnings.isNotEmpty()) "\n\nVarování:\n" + r.warnings.joinToString("\n") else "")
    r.errorCode == 0 -> "Ověřovací mód: zpráva je v pořádku, spojení funguje." +
        (if (r.warnings.isNotEmpty()) "\n\nVarování:\n" + r.warnings.joinToString("\n") else "")
    r.network -> "Server neodpověděl (${r.errorText}). Platba počká a odešle se automaticky."
    else -> "Odmítnuto: kód ${r.errorCode} – ${r.errorText}"
}
