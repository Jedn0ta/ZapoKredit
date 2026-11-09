package cz.klubpos.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.klubpos.net.Lan
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.ui.theme.glass
import kotlinx.coroutines.delay

/** Register as a server: waiters' phones and the PC connect with the QR / code shown here. */
@Composable
fun LanSettings() {
    val lan = cz.klubpos.LocalServices.current.lan
    val cfg by lan.config.collectAsState()
    val running by lan.running.collectAsState()
    val devices by lan.devices.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(5_000); now = System.currentTimeMillis() } }
    val code = remember(cfg.key, running, now / 60_000) { lan.code() }
    var confirmNew by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Přijímat objednávky z mobilů a PC")
                Text(if (cfg.enabled && running) "Zapnuto – pokladna běží na pozadí i se zhasnutou obrazovkou"
                     else "Vypnuto", style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted)
            }
            Switch(cfg.enabled, { lan.setEnabled(it) })
        }
        if (cfg.enabled) {
            if (code == null) Text("Tablet není připojený k Wi-Fi.", color = KlubColors.OldDebt)
            else Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val bmp = remember(code) { cz.klubpos.platform.qrImage(code.text(), 480) }
                Image(bmp, "QR pro připojení", Modifier.size(170.dp).clip(RoundedCornerShape(12.dp)))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Číšník: v aplikaci ZapoKredit Číšník naskenuj tento QR.", style = MaterialTheme.typography.bodySmall)
                    Text("PC: zadej adresu a kód:", style = MaterialTheme.typography.bodySmall)
                    Text("${code.host}:${code.port}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text(Lan.formatKey(code.key), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = KlubColors.Gold)
                }
            }
            if (devices.isNotEmpty()) {
                Text("Připojená zařízení", style = MaterialTheme.typography.labelLarge, color = KlubColors.Muted)
                devices.entries.sortedByDescending { it.value }.forEach { (d, t) ->
                    val min = (now - t) / 60_000
                    Text("$d · " + if (min < 1) "teď" else "před $min min", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ showDownload = true }) { Text("Stáhnout aplikace") }
                TextButton({ confirmNew = true }) { Text("Nový kód", color = KlubColors.OldDebt) }
            }
        }
    }

    if (confirmNew) AlertDialog(onDismissRequest = { confirmNew = false }, title = { Text("Vytvořit nový kód?") },
        text = { Text("Všechny mobily a PC se odpojí a musí kód naskenovat / zadat znovu. Použij, když se ztratí telefon číšníka.") },
        confirmButton = { TextButton({ lan.newKey(); confirmNew = false }) { Text("Nový kód", color = KlubColors.OldDebt) } },
        dismissButton = { TextButton({ confirmNew = false }) { Text("Zpět") } })
    if (showDownload) AlertDialog(onDismissRequest = { showDownload = false }, title = { Text("Aplikace Číšník a PC pokladna") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val url = "https://github.com/${cz.klubpos.platform.Platform.updateRepo}/releases/latest"
                val bmp = remember { cz.klubpos.platform.qrImage(url, 480) }
                Image(bmp, null, Modifier.size(200.dp))
                Text("Naskenuj mobilem a stáhni ZapoKredit-Cisnik-….apk. Pro Windows stáhni ZapoKredit-PC-….msi.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ showDownload = false }) { Text("Zavřít") } })
}
