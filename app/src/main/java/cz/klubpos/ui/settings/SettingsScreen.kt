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
fun SettingsScreen() {
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

    Scaffold(topBar = { TopAppBar(title = { Text("Nastavení") }) }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------- Provoz ----------
            var confirmNight by remember { mutableStateOf(false) }
            val repo = cz.klubpos.ui.components.rememberRepo()
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ukončit večer", fontWeight = FontWeight.SemiBold)
                        Text("Všem zavře účet. Dluhy zůstanou.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { confirmNight = true }) { Text("Ukončit") }
                }
            }
            if (confirmNight) androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmNight = false },
                title = { Text("Ukončit večer?") },
                confirmButton = { TextButton({ scope.launch { repo.closeAllVisits() }; confirmNight = false }) { Text("Ukončit") } },
                dismissButton = { TextButton({ confirmNight = false }) { Text("Zpět") } },
            )

            // ---------- O aplikaci ----------
            Text("O aplikaci", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(Modifier.fillMaxWidth()) {
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
            Card(Modifier.fillMaxWidth()) {
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
