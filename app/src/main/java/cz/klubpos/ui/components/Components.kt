package cz.klubpos.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.klubpos.KlubPosApp
import cz.klubpos.data.repo.ClubRepository
import cz.klubpos.upload.PhotoUploadServer
import cz.klubpos.upload.QrGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun rememberRepo(): ClubRepository = (LocalContext.current.applicationContext as KlubPosApp).repository

/** Decrypts and shows an image from ImageStore; falls back to initials/placeholder. */
@Composable
fun EncryptedImage(imageId: String?, fallbackText: String, modifier: Modifier, textSize: Int = 28) {
    val repo = rememberRepo()
    val bmp by produceState<ImageBitmap?>(null, imageId) {
        value = imageId?.let { id ->
            withContext(Dispatchers.IO) {
                repo.images.load(id)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
            }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else Text(initials(fallbackText), fontSize = textSize.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


fun initials(name: String) = name.split(" ").filter { it.isNotBlank() }.take(2)
    .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/**
 * Shows a QR code; a phone on the same Wi-Fi scans it and uploads a photo.
 * [onImage] runs with the raw bytes (on a background thread) – store them via the repository.
 */
@Composable
fun QrUploadDialog(title: String, onImage: suspend (ByteArray) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Naskenuj QR kód telefonem (stejná Wi-Fi).") }
    var done by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val server = PhotoUploadServer(context, onImage = { bytes ->
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { onImage(bytes) } }
                    .onSuccess { status = "✓ Fotka uložena."; done = true }
                    .onFailure { status = "Chyba: ${it.message}" }
            }
        })
        url = runCatching { server.startServer() }.getOrNull()
        if (url == null) status = "Tablet není připojen k Wi-Fi."
        onDispose { server.stop() }
    }

    val qr = remember(url) { url?.let { QrGenerator.bitmap(it).asImageBitmap() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()) {
                if (qr != null && !done) Image(qr, "QR", Modifier.size(260.dp))
                Text(status, textAlign = TextAlign.Center)
                url?.let { if (!done) Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (done) "Hotovo" else "Zavřít") } },
    )
}

/** Simple numeric input dialog (price, quantity, payment amount). */
@Composable
fun NumberDialog(
    title: String,
    initial: String = "",
    label: String = "",
    decimal: Boolean = false,
    extra: (@Composable () -> Unit)? = null,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val value = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
                    textStyle = MaterialTheme.typography.headlineSmall,
                )
                extra?.invoke()
            }
        },
        confirmButton = { TextButton(enabled = value != null && value >= 0, onClick = { onConfirm(value!!) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } },
    )
}
