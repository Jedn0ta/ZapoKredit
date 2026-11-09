package cz.klubpos.platform

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.wifi.WifiManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.security.crypto.EncryptedSharedPreferences
import cz.klubpos.BuildConfig
import cz.klubpos.R
import cz.klubpos.core.crypto.DatabaseKeyManager
import cz.klubpos.upload.QrGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of the platform layer. The PC build has a file with the same name and API
 * (desktop/src/main/kotlin/cz/klubpos/platform/Platform.kt); everything else is shared code.
 */
object Platform {
    lateinit var app: Application
    const val isDesktop = false
    val appName get() = app.getString(R.string.app_name)
    val versionName: String get() = BuildConfig.VERSION_NAME
    val versionCode: Int get() = BuildConfig.VERSION_CODE
    val isDebug get() = BuildConfig.DEBUG
    val updateRepo: String get() = BuildConfig.UPDATE_REPO
}

private class AndroidKv(private val p: SharedPreferences) : KvStore {
    override fun getString(key: String, defValue: String?) = p.getString(key, defValue)
    override fun getBoolean(key: String, defValue: Boolean) = p.getBoolean(key, defValue)
    override fun getInt(key: String, defValue: Int) = p.getInt(key, defValue)
    override fun getLong(key: String, defValue: Long) = p.getLong(key, defValue)
    override fun getFloat(key: String, defValue: Float) = p.getFloat(key, defValue)
    override fun contains(key: String) = p.contains(key)
    override val all: Map<String, *> get() = p.all
    override fun edit(): KvStore.Editor = Ed(p.edit())
    private class Ed(val e: SharedPreferences.Editor) : KvStore.Editor {
        override fun putString(key: String, value: String?) = also { e.putString(key, value) }
        override fun putBoolean(key: String, value: Boolean) = also { e.putBoolean(key, value) }
        override fun putInt(key: String, value: Int) = also { e.putInt(key, value) }
        override fun putLong(key: String, value: Long) = also { e.putLong(key, value) }
        override fun putFloat(key: String, value: Float) = also { e.putFloat(key, value) }
        override fun remove(key: String) = also { e.remove(key) }
        override fun clear() = also { e.clear() }
        override fun apply() = e.apply()
        override fun commit() = e.commit()
    }
}

private val stores = HashMap<String, KvStore>()

/** Encrypted store (EncryptedSharedPreferences, key in the Android Keystore). */
@Synchronized
fun securePrefs(name: String): KvStore = stores.getOrPut(name) {
    AndroidKv(EncryptedSharedPreferences.create(Platform.app, name, DatabaseKeyManager.masterKey(Platform.app),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM))
}

fun decodeImage(bytes: ByteArray): ImageBitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

fun qrImage(text: String, size: Int = 640): ImageBitmap = QrGenerator.bitmap(text, size).asImageBitmap()

private fun start(i: Intent): Boolean = try {
    Platform.app.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (e: ActivityNotFoundException) { false }

/** tel:, smsto:, mailto:, https: … */
fun openUri(uri: String): Boolean = start(
    when {
        uri.startsWith("tel:") -> Intent(Intent.ACTION_DIAL, Uri.parse(uri))
        uri.startsWith("smsto:") || uri.startsWith("mailto:") -> Intent(Intent.ACTION_SENDTO, Uri.parse(uri))
        else -> Intent(Intent.ACTION_VIEW, Uri.parse(uri))
    })

/** Share sheet with plain text. Returns a message to show, or null. */
fun shareText(subject: String, text: String, title: String = "Odeslat"): String? {
    start(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text), title))
    return null
}

/** Share / send a generated file (PDF, Excel). Returns a message to show, or null. */
fun shareFile(bytes: ByteArray, fileName: String, mime: String, title: String): String? {
    val f = File(Platform.app.cacheDir, "exports").apply { mkdirs() }.resolve(fileName).apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(Platform.app, "${Platform.app.packageName}.fileprovider", f)
    start(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), title))
    return null
}

fun playNotificationSound() {
    runCatching {
        android.media.RingtoneManager.getRingtone(Platform.app,
            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))?.play()
    }
}

/** Open-file picker. Returns a function that opens it; [onPicked] gets file name and content. */
@Composable
fun rememberFilePicker(mimeTypes: Array<String>, onPicked: suspend (name: String, bytes: ByteArray) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val l = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = Platform.app.contentResolver
            val name = runCatching { r.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (c.moveToFirst() && i >= 0) c.getString(i) else null } }
                .getOrNull() ?: "soubor"
            val bytes = withContext(Dispatchers.IO) { r.openInputStream(uri)?.use { it.readBytes() } } ?: return@launch
            onPicked(name, bytes)
        }
    }
    return { l.launch(mimeTypes) }
}

/** Save-as dialog. Returns a function (suggested file name, content). */
@Composable
fun rememberFileSaver(mime: String, onSaved: (String) -> Unit = {}): (String, ByteArray) -> Unit {
    val scope = rememberCoroutineScope()
    val pending = androidx.compose.runtime.remember { arrayOfNulls<ByteArray>(1) }
    val l = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        val bytes = pending[0] ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) { Platform.app.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
            onSaved("Uloženo")
        }
    }
    return { name, bytes -> pending[0] = bytes; l.launch(name) }
}

@Composable
fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = androidx.activity.compose.BackHandler(enabled, onBack)

/** Lets the register hear discovery broadcasts from waiters / PC (Android filters them otherwise). */
fun multicastLock(): AutoCloseable? = (Platform.app.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
    ?.createMulticastLock("zapokredit")?.let { lock ->
        lock.setReferenceCounted(false); runCatching { lock.acquire() }
        AutoCloseable { runCatching { lock.release() } }
    }

/** Keep the LAN server running in the background (Android: foreground service). */
fun lanBackground(on: Boolean, start: () -> Unit, stop: () -> Unit) {
    if (on) cz.klubpos.lan.LanService.start(Platform.app) else cz.klubpos.lan.LanService.stop(Platform.app)
}
