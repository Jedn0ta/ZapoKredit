package cz.klubpos.ui.keyapp

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import cz.klubpos.BuildConfig
import cz.klubpos.core.crypto.DatabaseKeyManager
import cz.klubpos.core.owner.OwnerCrypto
import cz.klubpos.ui.theme.Display
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.ui.theme.glass
import cz.klubpos.update.UpdateCheck
import cz.klubpos.update.UpdateManager
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Owner's key store: paired clubs with their secrets, Keystore-encrypted, never exported. */
private class KeyStoreLocal(context: Context) {
    data class Club(val id: String, val name: String, val secret: ByteArray, val pairedAt: Long)

    private val prefs = EncryptedSharedPreferences.create(
        context, "owner_key", DatabaseKeyManager.masterKey(context),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun clubs(): List<Club> {
        val arr = JSONArray(prefs.getString("clubs", "[]"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            Club(it.getString("id"), it.getString("name"), OwnerCrypto.decodeSecret(it.getString("secret")), it.getLong("at"))
        }
    }

    private fun save(list: List<Club>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("secret", OwnerCrypto.encodeSecret(it.secret)).put("at", it.pairedAt)) }
        prefs.edit().putString("clubs", arr.toString()).apply()
    }

    /** Re-pairing the same club replaces its secret (old key stops working on the tablet only after it confirms). */
    fun put(c: Club) = save(clubs().filter { it.id != c.id } + c)
    fun remove(id: String) = save(clubs().filter { it.id != id })
}

private sealed interface Screen {
    data object Home : Screen
    data class Paired(val club: KeyStoreLocal.Club) : Screen
    data class Approve(val club: KeyStoreLocal.Club, val req: OwnerCrypto.Request) : Screen
    data class Code(val club: KeyStoreLocal.Club, val action: String, val code: String) : Screen
}

/** The "ZapoKredit Klíč" app on the owner's phone. */
@Composable
fun KeyApp() {
    val context = LocalContext.current
    val store = remember { KeyStoreLocal(context) }
    var clubs by remember { mutableStateOf(store.clubs()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var error by remember { mutableStateOf<String?>(null) }
    var removeClub by remember { mutableStateOf<KeyStoreLocal.Club?>(null) }

    // Device lock (PIN / fingerprint) before revealing any code.
    val keyguard = remember { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    var afterAuth by remember { mutableStateOf<(() -> Unit)?>(null) }
    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) afterAuth?.invoke() else error = "Ověření zámku telefonu se nezdařilo."
        afterAuth = null
    }
    fun withDeviceAuth(block: () -> Unit) {
        if (!keyguard.isDeviceSecure) { block(); return }
        @Suppress("DEPRECATION")
        val intent = keyguard.createConfirmDeviceCredentialIntent("ZapoKredit Klíč", "Potvrď, že jsi majitel")
        if (intent == null) block() else { afterAuth = block; authLauncher.launch(intent) }
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        when (val p = OwnerCrypto.parse(text)) {
            is OwnerCrypto.Pair -> withDeviceAuth {
                val c = KeyStoreLocal.Club(p.clubId, p.clubName, p.secret, System.currentTimeMillis())
                store.put(c); clubs = store.clubs(); screen = Screen.Paired(c)
            }
            is OwnerCrypto.Request -> {
                val c = clubs.firstOrNull { it.id == p.clubId }
                if (c == null) error = "Tento tablet není s tímto klíčem spárovaný."
                else screen = Screen.Approve(c, p)
            }
            null -> error = "Tohle není kód ZapoKreditu."
        }
    }
    fun scan(prompt: String) = scanner.launch(ScanOptions().setPrompt(prompt).setBeepEnabled(false)
        .setOrientationLocked(false).setDesiredBarcodeFormats(ScanOptions.QR_CODE))

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(KlubColors.GoldBrush), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Key, null, tint = KlubColors.Ink)
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text("ZapoKredit Klíč", style = MaterialTheme.typography.headlineSmall)
                    Text("klíč majitele klubu", style = MaterialTheme.typography.labelMedium, color = KlubColors.Muted)
                }
            }

            when (val s = screen) {
                Screen.Home -> {
                    if (clubs.isEmpty()) {
                        Hero("Spáruj klíč s tabletem",
                            "Na tabletu otevři Nastavení → Klíč majitele → Spárovat telefon a naskenuj zobrazený kód.")
                        BigButton("Spárovat") { scan("Naskenuj párovací kód z tabletu") }
                    } else {
                        Hero("Schválit požadavek",
                            "Když tablet chce potvrzení majitele (ceny, barmani, platby…), ukáže QR kód. Naskenuj ho a nadiktuj číslo.")
                        BigButton("Naskenovat požadavek") { scan("Naskenuj kód z tabletu") }
                        Text("Spárované kluby", style = MaterialTheme.typography.labelLarge, color = KlubColors.Muted)
                        clubs.forEach { c ->
                            Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                TextButton({ removeClub = c }) { Text("Odebrat", color = KlubColors.OldDebt) }
                            }
                        }
                        TextButton({ scan("Naskenuj párovací kód z tabletu") }) { Text("+ Spárovat další tablet / nový klíč") }
                    }
                    UpdateRow()
                }
                is Screen.Paired -> {
                    Hero("Spárováno: ${s.club.name}", "Na tabletu zadej tento potvrzovací kód:")
                    BigCode(OwnerCrypto.confirmCode(s.club.secret))
                    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Záložní kód – opiš si ho na papír", fontWeight = FontWeight.Bold, color = KlubColors.OpenTab)
                        Text("Pokud telefon ztratíš, tímto kódem odemkneš tablet a spáruješ nový klíč. Nikomu ho nedávej.",
                            style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted)
                        Text(OwnerCrypto.recoveryCode(s.club.secret), style = Display.copy(fontSize = 22.sp, letterSpacing = 1.sp))
                    }
                    BigButton("Hotovo") { screen = Screen.Home }
                }
                is Screen.Approve -> {
                    Hero(s.club.name, "Tablet žádá o schválení:")
                    Text(s.req.action, style = MaterialTheme.typography.headlineSmall, color = KlubColors.Gold)
                    BigButton("Schválit") {
                        withDeviceAuth {
                            screen = Screen.Code(s.club, s.req.action,
                                OwnerCrypto.response(s.club.secret, s.req.clubId, s.req.nonce, s.req.action))
                        }
                    }
                    TextButton({ screen = Screen.Home }, Modifier.fillMaxWidth()) { Text("Odmítnout") }
                }
                is Screen.Code -> {
                    Hero("Schváleno", "Nadiktuj nebo zadej na tabletu:")
                    BigCode(s.code)
                    Text("Kód platí jen pro tento jeden požadavek.", style = MaterialTheme.typography.bodySmall,
                        color = KlubColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    BigButton("Hotovo") { screen = Screen.Home }
                }
            }
        }
    }

    error?.let { e ->
        AlertDialog(onDismissRequest = { error = null }, title = { Text("Pozor") }, text = { Text(e) },
            confirmButton = { TextButton({ error = null }) { Text("OK") } })
    }
    removeClub?.let { c ->
        AlertDialog(onDismissRequest = { removeClub = null }, title = { Text("Odebrat ${c.name}?") },
            text = { Text("Tento telefon už nebude moct schvalovat. Tablet zůstane zamčený, dokud nespáruješ nový klíč (záložním kódem).") },
            confirmButton = { TextButton({ withDeviceAuth { store.remove(c.id); clubs = store.clubs() }; removeClub = null }) { Text("Odebrat", color = KlubColors.OldDebt) } },
            dismissButton = { TextButton({ removeClub = null }) { Text("Zpět") } })
    }
}

@Composable
private fun Hero(title: String, text: String) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Text(text, color = KlubColors.Muted)
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(20.dp)).background(KlubColors.GoldBrush)
        .clickable(onClick = onClick), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.QrCodeScanner, null, tint = KlubColors.Ink)
        Spacer(Modifier.size(10.dp))
        Text(label, color = KlubColors.Ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    }
}


@Composable
private fun BigCode(code: String) {
    Text(code.chunked(4).joinToString(" "), style = Display.copy(fontSize = 52.sp, letterSpacing = 6.sp),
        modifier = Modifier.fillMaxWidth().glass().padding(vertical = 28.dp), textAlign = TextAlign.Center, color = KlubColors.Gold)
}

/** Version + self-update for the key app (takes the "-Klic-" APK from GitHub Releases). */
@Composable
private fun UpdateRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember { UpdateManager(context.applicationContext) }
    var status by remember { mutableStateOf("Verze ${BuildConfig.VERSION_NAME}") }
    Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(status, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted)
        TextButton({
            scope.launch {
                status = "Kontroluji…"
                when (val c = updater.check()) {
                    is UpdateCheck.UpToDate -> status = "Verze ${BuildConfig.VERSION_NAME} je nejnovější"
                    is UpdateCheck.Error -> status = c.message
                    is UpdateCheck.Available -> {
                        status = "Stahuji ${c.release.version}…"
                        runCatching { updater.download(c.release) {} }
                            .onSuccess { if (updater.canInstall()) updater.install(it) else updater.openInstallPermissionSettings(); status = "Verze ${c.release.version} stažena" }
                            .onFailure { status = "Stažení selhalo" }
                    }
                }
            }
        }) { Text("Aktualizovat") }
    }
}
