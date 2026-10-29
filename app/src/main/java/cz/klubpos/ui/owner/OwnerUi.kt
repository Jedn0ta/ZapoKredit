package cz.klubpos.ui.owner

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.klubpos.KlubPosApp
import cz.klubpos.core.owner.OwnerLock
import cz.klubpos.ui.member.Keypad
import cz.klubpos.ui.theme.Display
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.upload.QrGenerator
import kotlinx.coroutines.delay

@Composable
fun rememberOwnerLock(): OwnerLock = (LocalContext.current.applicationContext as KlubPosApp).ownerLock

/** True while management is unlocked (or no owner is paired yet). Re-evaluates every second. */
@Composable
fun rememberOwnerUnlocked(): Boolean {
    val lock = rememberOwnerLock()
    val paired by lock.paired.collectAsState()
    val until by lock.unlockedUntil.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(paired, until) { while (paired && now < until) { delay(1000); now = System.currentTimeMillis() } }
    return !paired || now < until
}

@Composable
private fun Qr(payload: String) {
    val bmp = remember(payload) { QrGenerator.bitmap(payload, 720).asImageBitmap() }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(bmp, null, Modifier.size(250.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp))
    }
}

@Composable
private fun CodeDisplay(code: String, length: Int) {
    Text((code + "•".repeat((length - code.length).coerceAtLeast(0))).chunked(4).joinToString(" "),
        style = Display.copy(fontSize = 34.sp, letterSpacing = 4.sp), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}

/**
 * Asks the owner to approve [action]: shows a QR challenge, the bartender types the 8-digit
 * answer from the owner's phone. [onUnlocked] runs once approved.
 */
@Composable
fun OwnerUnlockDialog(action: String = "Odemknout správu", onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    val lock = rememberOwnerLock()
    var challenge by remember { mutableStateOf(lock.newChallenge(action)) }
    var code by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var recovery by remember { mutableStateOf(false) }
    var recoveryText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (recovery) "Záložní kód majitele" else "Schválení majitelem") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val ch = challenge
                if (ch == null) Text("Klíč majitele není spárovaný.")
                else if (recovery) {
                    Text("Zadej záložní kód z aplikace Klíč (nebo z papíru).", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(recoveryText, { recoveryText = it.uppercase() }, singleLine = true,
                        placeholder = { Text("XXXX-XXXX-XXXX-XXXX") })
                } else {
                    Text("Majitel naskenuje kód aplikací ZapoKredit Klíč a nadiktuje 8 číslic.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Qr(ch.payload)
                    CodeDisplay(code, 8)
                    Keypad(Modifier.fillMaxWidth(),
                        onDigit = { d -> if (code.length < 8) code = (code + d).take(8) },
                        onBack = { code = code.dropLast(1) }, onClear = { code = "" })
                }
                msg?.let { Text(it, color = KlubColors.OldDebt) }
                TextButton({ recovery = !recovery; msg = null }) {
                    Text(if (recovery) "Zpět na QR" else "Ztracený telefon? Záložní kód")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = challenge != null && (if (recovery) recoveryText.length >= 16 else code.length == 8), onClick = {
                if (recovery) {
                    if (lock.verifyRecovery(recoveryText)) onUnlocked() else msg = "Neplatný záložní kód"
                } else when (val r = lock.verify(challenge!!, code)) {
                    OwnerLock.Result.Ok -> onUnlocked()
                    is OwnerLock.Result.Wrong -> { msg = "Špatný kód, zbývá pokusů: ${r.left}"; code = "" }
                    OwnerLock.Result.Expired -> { msg = "Moc pokusů – vygeneroval jsem nový QR."; challenge = lock.newChallenge(action); code = "" }
                }
            }) { Text("Ověřit") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}

/** Pairing: show secret QR once, then check the 6-digit confirmation from the owner's phone. */
@Composable
fun PairingDialog(clubName: String, onDismiss: () -> Unit, onPaired: () -> Unit) {
    val lock = rememberOwnerLock()
    val pending = remember { runCatching { lock.beginPairing(clubName) }.getOrNull() }
    var code by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { lock.cancelPairing(); onDismiss() },
        title = { Text("Spárovat klíč majitele") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pending == null) Text("Nejdřív odemkni správu současným klíčem.")
                else {
                    Text("1. Na telefonu majitele otevři ZapoKredit Klíč → Spárovat a naskenuj kód.\n" +
                        "2. Opiš 6 číslic, které telefon ukáže.\nKód nikomu neukazuj – kdo ho naskenuje, stane se majitelem.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Qr(pending.payload)
                    CodeDisplay(code, 6)
                    Keypad(Modifier.fillMaxWidth(),
                        onDigit = { d -> if (code.length < 6) code = (code + d).take(6) },
                        onBack = { code = code.dropLast(1) }, onClear = { code = "" })
                    msg?.let { Text(it, color = KlubColors.OldDebt) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = pending != null && code.length == 6, onClick = {
                if (lock.confirmPairing(code)) onPaired() else { msg = "Kód nesouhlasí – naskenuj znovu."; code = "" }
            }) { Text("Spárovat") }
        },
        dismissButton = { TextButton({ lock.cancelPairing(); onDismiss() }) { Text("Zrušit") } },
    )
}
