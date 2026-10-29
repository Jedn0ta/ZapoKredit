package cz.klubpos.ui.products

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import cz.klubpos.core.io.ProductSheet
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.ui.theme.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Import products from Excel/CSV, save or send the template (= current catalogue). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }

    suspend fun template(): ByteArray = withContext(Dispatchers.IO) { ProductSheet.writeXlsx(repo.allProductsOnce()) }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            result = runCatching {
                val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && i >= 0) c.getString(i) else null
                } ?: uri.lastPathSegment ?: "soubor.xlsx"
                val rows = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { ProductSheet.read(it, name) }
                }
                val r = repo.importProducts(rows)
                buildString {
                    append("Nové: ${r.added}\nAktualizované: ${r.updated}")
                    if (r.skipped.isNotEmpty()) append("\nPřeskočené (chybí název nebo cena): ${r.skipped.joinToString()}")
                }
            }.getOrElse { "Soubor se nepodařilo načíst: ${it.message}" }
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = template()
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) } }
            result = "Vzorová tabulka uložena."
        }
    }
    fun share() = scope.launch {
        val bytes = template()
        val f = withContext(Dispatchers.IO) {
            File(context.cacheDir, "exports").apply { mkdirs() }.resolve("ZapoKredit-polozky.xlsx").apply { writeBytes(bytes) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(XLSX)
            .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, "ZapoKredit – položky")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Poslat tabulku položek"))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Položky a Excel", style = MaterialTheme.typography.titleLarge)
            Text("Vzorová tabulka obsahuje současnou nabídku. Uprav ji v Excelu nebo Google Tabulkách a nahraj zpět – " +
                "položky se spárují podle názvu (změní se ceny, přidají nové).", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            Action(Icons.Default.FileOpen, "Importovat z Excelu (.xlsx / .csv)") {
                openFile.launch(arrayOf(XLSX, "text/csv", "text/comma-separated-values", "application/vnd.ms-excel", "text/plain"))
            }
            Action(Icons.Default.Download, "Uložit vzorovou tabulku") { saveFile.launch("ZapoKredit-polozky.xlsx") }
            Action(Icons.Default.Share, "Poslat vzorovou tabulku (e-mail, WhatsApp…)") { share() }
            Text("Sloupce: ${ProductSheet.HEADERS.joinToString(" · ")}", style = MaterialTheme.typography.labelSmall,
                color = KlubColors.Muted, modifier = Modifier.padding(bottom = 16.dp))
        }
    }
    result?.let { r ->
        AlertDialog(onDismissRequest = { result = null }, title = { Text("Hotovo") }, text = { Text(r) },
            confirmButton = { TextButton({ result = null }) { Text("OK") } })
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = KlubColors.Gold); Spacer(Modifier.width(14.dp)); Text(label)
    }
}
