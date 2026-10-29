package cz.klubpos.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.klubpos.BuildConfig
import cz.klubpos.R
import cz.klubpos.domain.BusinessDay
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.update.ReleaseInfo
import cz.klubpos.update.UpdateCheck
import cz.klubpos.update.UpdateManager
import cz.klubpos.domain.QrPayment
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.components.rememberSettings
import cz.klubpos.ui.tour.tourTarget
import cz.klubpos.ui.owner.OwnerUnlockDialog
import cz.klubpos.ui.owner.PairingDialog
import cz.klubpos.ui.owner.rememberOwnerLock
import cz.klubpos.ui.owner.rememberOwnerUnlocked
import cz.klubpos.core.owner.OwnerLock
import cz.klubpos.ui.theme.glass
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import java.io.File

private sealed interface UpdUi {
    data object Idle : UpdUi
    data object Checking : UpdUi
    data class Result(val check: UpdateCheck) : UpdUi
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdUi
    data class Ready(val release: ReleaseInfo, val apk: File) : UpdUi
    data class Failed(val message: String) : UpdUi
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onStartTour: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember { UpdateManager(context.applicationContext) }
    var state by remember { mutableStateOf<UpdUi>(UpdUi.Idle) }

    fun install(apk: File) {
        if (updater.canInstall()) updater.install(apk) else updater.openInstallPermissionSettings()
    }

    fun download(r: ReleaseInfo) = scope.launch {
        state = UpdUi.Downloading(r, 0f)
        runCatching { updater.download(r) { p -> state = UpdUi.Downloading(r, p) } }
            .onSuccess { apk -> state = UpdUi.Ready(r, apk); install(apk) }
            .onFailure { state = UpdUi.Failed(it.message ?: "Stažení selhalo") }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Nastavení") }, colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)) }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val repo = rememberRepo()
            val appSettings = rememberSettings()
            val cfg by appSettings.flow.collectAsState()
            val ownerLock = rememberOwnerLock()
            val paired by ownerLock.paired.collectAsState()
            val unlocked = rememberOwnerUnlocked()
            var askUnlock by remember { mutableStateOf(false) }
            var pairing by remember { mutableStateOf(false) }
            var confirmUnpair by remember { mutableStateOf(false) }
            if (askUnlock) OwnerUnlockDialog(onDismiss = { askUnlock = false }, onUnlocked = { askUnlock = false })
            if (pairing) PairingDialog(cfg.clubName, onDismiss = { pairing = false }, onPaired = { pairing = false })
            if (confirmUnpair) androidx.compose.material3.AlertDialog(onDismissRequest = { confirmUnpair = false },
                title = { Text("Zrušit klíč majitele?") },
                text = { Text("Správa (ceny, barmani, platby) pak bude přístupná komukoli u tabletu.") },
                confirmButton = { TextButton({ ownerLock.unpair(); confirmUnpair = false }) { Text("Zrušit klíč") } },
                dismissButton = { TextButton({ confirmUnpair = false }) { Text("Zpět") } })

            // ---------- Klíč majitele ----------
            Text("Klíč majitele", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Column(Modifier.fillMaxWidth().glass().tourTarget("owner_key").padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(when {
                    !paired -> "Není spárován. Správu může měnit kdokoli – doporučujeme spárovat telefon majitele."
                    unlocked -> "🔓 Správa odemčena (na ${OwnerLock.UNLOCK_MINUTES} min)."
                    else -> "🔒 Ceny, barmani a platby jsou zamčené. Odemkne je jen majitel svým telefonem."
                }, color = if (!paired) KlubColors.OpenTab else MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        !paired -> Button({ pairing = true }) { Text("Spárovat telefon") }
                        !unlocked -> Button({ askUnlock = true }) { Text("Odemknout") }
                        else -> {
                            OutlinedButton({ ownerLock.lock() }) { Text("Zamknout") }
                            OutlinedButton({ pairing = true }) { Text("Nový klíč") }
                            TextButton({ confirmUnpair = true }) { Text("Zrušit", color = KlubColors.OldDebt) }
                        }
                    }
                }
            }

            // ---------- Barmani ----------
            Text("Barmani", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            BartendersSection(canEdit = unlocked, onRequestUnlock = { askUnlock = true })

            // ---------- Průvodce ----------
            Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Průvodce aplikací", fontWeight = FontWeight.SemiBold)
                        Text("Ukáže všechny funkce přímo na obrazovce.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = onStartTour) { Text("Spustit") }
                }
            }

            // ---------- Uzávěrka ----------
            Text("Uzávěrka dne", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            DayCloseCard(Modifier.tourTarget("close_day"))

            // ---------- Platby ----------
            Text("Platby", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(Modifier.fillMaxWidth().tourTarget("payment_settings")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    var account by remember(cfg.account) { mutableStateOf(cfg.account) }
                    var club by remember(cfg.clubName) { mutableStateOf(cfg.clubName) }
                    var limit by remember(cfg.debtLimit) { mutableStateOf(if (cfg.debtLimit > 0) cfg.debtLimit.toString() else "") }
                    val iban = QrPayment.toIban(account)
                    OutlinedTextField(account, { account = it }, label = { Text("Účet pro QR platby") },
                        placeholder = { Text("123456789/0800 nebo IBAN") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text(when {
                                account.isBlank() -> "QR platby vypnuté"
                                iban != null -> "✓ $iban"
                                else -> "Neplatné číslo účtu"
                            }, color = if (account.isNotBlank() && iban == null) KlubColors.OldDebt else MaterialTheme.colorScheme.onSurfaceVariant)
                        })
                    OutlinedTextField(club, { club = it }, label = { Text("Název klubu (zpráva pro příjemce)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(limit, { limit = it.filter(Char::isDigit) }, label = { Text("Limit dluhu (kr, prázdné = bez limitu)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Nezhasínat obrazovku", Modifier.weight(1f))
                        androidx.compose.material3.Switch(cfg.keepScreenOn, { v -> appSettings.update { it.copy(keepScreenOn = v) } })
                    }
                    val dirty = account != cfg.account || club != cfg.clubName || (limit.toLongOrNull() ?: 0) != cfg.debtLimit
                    Button(enabled = dirty && (account.isBlank() || iban != null), onClick = {
                        if (!unlocked) askUnlock = true
                        else appSettings.update { it.copy(account = account, clubName = club.ifBlank { "Klub" }, debtLimit = limit.toLongOrNull() ?: 0) }
                    }) { Text(if (unlocked) "Uložit" else "🔒 Uložit") }
                }
            }

            // ---------- O aplikaci ----------
            Text("O aplikaci", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(context.getString(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    InfoRow("Verze", "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                    InfoRow("Typ sestavení", if (BuildConfig.DEBUG) "vývojové (debug)" else "produkční")
                    InfoRow("Repozitář", updater.repo)
                    InfoRow("Obchodní den začíná", "%02d:00".format(BusinessDay.CUTOFF_HOUR))
                    InfoRow("Data", "šifrovaná (SQLCipher + Android Keystore)")
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updater.repoUrl)))
                    }) { Text("Otevřít repozitář na GitHubu") }
                }
            }

            // ---------- Aktualizace ----------
            Text("Aktualizace", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (val s = state) {
                        UpdUi.Idle -> Text("Zkontroluje nejnovější vydání na GitHubu.")
                        UpdUi.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.padding(end = 12.dp)); Text("Kontroluji…")
                        }
                        is UpdUi.Result -> when (val c = s.check) {
                            is UpdateCheck.UpToDate -> Text("✓ Máš nejnovější verzi (${c.latest}).", color = KlubColors.PaidToday)
                            is UpdateCheck.Error -> Text(c.message, color = KlubColors.OldDebt)
                            is UpdateCheck.Available -> ReleaseCard(c.release) { download(c.release) }
                        }
                        is UpdUi.Downloading -> {
                            Text("Stahuji verzi ${s.release.version}… ${(s.progress * 100).toInt()} %")
                            LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                        }
                        is UpdUi.Ready -> {
                            Text("Verze ${s.release.version} je stažená.")
                            if (!updater.canInstall()) Text(
                                "Nejdřív povol této aplikaci „Instalovat neznámé aplikace“, pak se vrať a klepni na Instalovat.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { install(s.apk) }) { Text("Instalovat") }
                        }
                        is UpdUi.Failed -> Text(s.message, color = KlubColors.OldDebt)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    OutlinedButton(
                        enabled = state !is UpdUi.Checking && state !is UpdUi.Downloading,
                        onClick = { scope.launch { state = UpdUi.Checking; state = UpdUi.Result(updater.check()) } },
                    ) { Text("Zkontrolovat aktualizace") }
                    Text("Data v aplikaci zůstanou zachována. Před aktualizací doporučujeme ukončit večer.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Today's numbers + end-of-night button. */
@Composable
private fun DayCloseCard(modifier: Modifier) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val dayStart = remember { BusinessDay.startMillis() }
    var summary by remember { mutableStateOf<cz.klubpos.data.db.dao.DaySummary?>(null) }
    var outstanding by remember { mutableStateOf(0L) }
    var refresh by remember { mutableStateOf(0) }
    var confirmNight by remember { mutableStateOf(false) }
    val top by remember { repo.topProductsSince(dayStart, 5) }.collectAsState(initial = emptyList())
    val products by repo.allProducts().collectAsState(initial = emptyList())
    androidx.compose.runtime.LaunchedEffect(refresh) {
        summary = repo.daySummary(dayStart); outstanding = repo.totalOutstanding()
    }
    // Recompute when the screen is opened; cheap queries.
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val s = summary
            if (s != null) {
                Text(cz.klubpos.domain.Credits.format(s.charged), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("útrata dnes · ${s.customers} hostů", color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                InfoRow("Hotově", cz.klubpos.domain.Credits.format(s.paidCash))
                InfoRow("QR platby", cz.klubpos.domain.Credits.format(s.paidQr))
                if (s.paidOther > 0) InfoRow("Jiné", cz.klubpos.domain.Credits.format(s.paidOther))
                InfoRow("Dnes nezaplaceno", cz.klubpos.domain.Credits.format((s.charged - s.paidCash - s.paidQr - s.paidOther).coerceAtLeast(0)))
                InfoRow("Dluží celkem", cz.klubpos.domain.Credits.format(outstanding))
                if (top.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Text("Nejprodávanější", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    top.forEach { t ->
                        val name = products.firstOrNull { it.id == t.productId }?.name ?: "?"
                        InfoRow(name, "${cz.klubpos.ui.member.fmtQty(t.qty)}×")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { refresh++ }) { Text("Obnovit") }
                Button(onClick = { confirmNight = true }) { Text("Ukončit večer") }
            }
        }
    }
    if (confirmNight) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmNight = false },
        title = { Text("Ukončit večer?") },
        text = { Text("Všem se zavře účet (odchod z klubu). Dluhy zůstanou.") },
        confirmButton = { TextButton({ scope.launch { repo.closeAllVisits() }; confirmNight = false }) { Text("Ukončit") } },
        dismissButton = { TextButton({ confirmNight = false }) { Text("Zpět") } },
    )
}

@Composable
private fun ReleaseCard(r: ReleaseInfo, onDownload: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Je k dispozici verze ${r.version}", fontWeight = FontWeight.Bold, color = KlubColors.Amber)
        if (r.apkSize > 0) Text("Velikost %.1f MB".format(r.apkSize / 1_048_576.0), style = MaterialTheme.typography.bodySmall)
        if (r.notes.isNotBlank()) Text(r.notes.take(1500), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onDownload) { Text("Stáhnout a nainstalovat") }
    }
}

@Composable
private fun InfoRow(label: String, value: String) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
