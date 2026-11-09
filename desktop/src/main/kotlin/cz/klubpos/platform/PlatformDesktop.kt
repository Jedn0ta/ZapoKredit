package cz.klubpos.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import cz.klubpos.net.Json
import cz.klubpos.net.obj
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * PC (Windows) implementation of the platform layer – same API as the Android Platform.kt.
 * Data live in %APPDATA%\ZapoKredit, everything encrypted with a master key protected by Windows (DPAPI).
 */
object Platform {
    const val isDesktop = true
    val appName = "ZapoKredit"
    private val props = Properties().apply {
        runCatching { Platform::class.java.getResourceAsStream("/zapokredit-version.properties")?.use { load(it) } }
    }
    val versionName: String get() = props.getProperty("version", "0.0.0")
    val versionCode: Int get() = props.getProperty("build", "0").toIntOrNull() ?: 0
    val isDebug = false
    val updateRepo = "Jedn0ta/ZapoKredit"

    val dataDir: File by lazy {
        val base = System.getenv("APPDATA")?.let { File(it) } ?: File(System.getProperty("user.home"), ".config")
        File(base, "ZapoKredit").apply { mkdirs() }
    }

    /** 256-bit master key; on Windows stored encrypted by DPAPI (bound to the Windows user account). */
    val masterKey: ByteArray by lazy { MasterKey.load(File(dataDir, "master.key")) }

    /** Derived keys for separate purposes. */
    fun subKey(label: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(masterKey + label.toByteArray())
}

private object MasterKey {
    private val windows = System.getProperty("os.name").lowercase().contains("win")

    fun load(f: File): ByteArray {
        if (f.exists()) return unprotect(f.readBytes())
        val k = ByteArray(32).also(SecureRandom()::nextBytes)
        f.writeBytes(protect(k))
        return k
    }

    private fun protect(b: ByteArray): ByteArray = if (windows) com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(b) else b
    private fun unprotect(b: ByteArray): ByteArray = if (windows) com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(b) else b
}

/** AES-256-GCM helpers (iv | ciphertext). */
object Aes {
    private val rnd = SecureRandom()
    fun seal(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return iv + c.doFinal(plain)
    }
    fun open(key: ByteArray, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, data, 0, 12))
        return c.doFinal(data, 12, data.size - 12)
    }
}

/** Encrypted JSON file per store. */
private class FileKv(private val file: File) : KvStore {
    private val key = Platform.subKey("prefs")
    private val map: MutableMap<String, Any?> = runCatching {
        LinkedHashMap(Json.parse(String(Aes.open(key, file.readBytes()), Charsets.UTF_8)).obj())
    }.getOrElse { LinkedHashMap() }

    @Synchronized private fun save() {
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(Aes.seal(key, Json.write(map).toByteArray(Charsets.UTF_8)))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    override fun getString(key: String, defValue: String?) = synchronized(this) { map[key] as? String ?: defValue }
    override fun getBoolean(key: String, defValue: Boolean) = synchronized(this) { map[key] as? Boolean ?: defValue }
    override fun getInt(key: String, defValue: Int) = synchronized(this) { (map[key] as? Number)?.toInt() ?: defValue }
    override fun getLong(key: String, defValue: Long) = synchronized(this) { (map[key] as? Number)?.toLong() ?: defValue }
    override fun getFloat(key: String, defValue: Float) = synchronized(this) { (map[key] as? Number)?.toFloat() ?: defValue }
    override fun contains(key: String) = synchronized(this) { map.containsKey(key) }
    override val all: Map<String, *> get() = synchronized(this) { HashMap(map) }
    override fun edit(): KvStore.Editor = Ed()

    private inner class Ed : KvStore.Editor {
        private val changes = LinkedHashMap<String, Any?>()
        private val removed = HashSet<String>()
        override fun putString(key: String, value: String?) = also { if (value == null) removed += key else changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = also { changes[key] = value }
        override fun putInt(key: String, value: Int) = also { changes[key] = value.toLong() }
        override fun putLong(key: String, value: Long) = also { changes[key] = value }
        override fun putFloat(key: String, value: Float) = also { changes[key] = value.toDouble() }
        override fun remove(key: String) = also { removed += key }
        override fun apply() { commit() }
        override fun commit(): Boolean = synchronized(this@FileKv) {
            removed.forEach { map.remove(it) }; map.putAll(changes); runCatching { save() }.isSuccess
        }
    }
}

private val stores = HashMap<String, KvStore>()

@Synchronized
fun securePrefs(name: String): KvStore = stores.getOrPut(name) { FileKv(File(File(Platform.dataDir, "prefs").apply { mkdirs() }, "$name.bin")) }

fun decodeImage(bytes: ByteArray): ImageBitmap? =
    runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

fun qrImage(text: String, size: Int = 640): ImageBitmap {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size,
        mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until size) for (x in 0 until size) img.setRGB(x, y, if (m[x, y]) 0x000000 else 0xFFFFFF)
    return img.toComposeImageBitmap()
}

fun openUri(uri: String): Boolean = runCatching {
    val d = Desktop.getDesktop()
    if (uri.startsWith("mailto:") && d.isSupported(Desktop.Action.MAIL)) d.mail(URI(uri)) else d.browse(URI(uri))
    true
}.getOrDefault(false)

/** PC: no share sheet – the text goes to the clipboard. */
fun shareText(subject: String, text: String, title: String = "Odeslat"): String? = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    "Zkopírováno do schránky – vlož ho do e-mailu nebo zprávy (Ctrl+V)."
}.getOrDefault("Text se nepodařilo zkopírovat.")

private fun saveDialog(fileName: String): File? {
    val d = FileDialog(null as Frame?, "Uložit", FileDialog.SAVE)
    d.file = fileName; d.isVisible = true
    return d.file?.let { File(d.directory, it) }
}

/** PC: "share" = save the file and open it (PDF opens in the viewer → print). */
fun shareFile(bytes: ByteArray, fileName: String, mime: String, title: String): String? {
    val f = saveDialog(fileName) ?: return null
    f.writeBytes(bytes)
    runCatching { Desktop.getDesktop().open(f) }
    return "Uloženo: ${f.path}"
}

fun playNotificationSound() { runCatching { Toolkit.getDefaultToolkit().beep() } }

@Composable
fun rememberFilePicker(mimeTypes: Array<String>, onPicked: suspend (name: String, bytes: ByteArray) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        val d = FileDialog(null as Frame?, "Vybrat soubor", FileDialog.LOAD)
        d.isVisible = true
        val f = d.file?.let { File(d.directory, it) }
        if (f != null) scope.launch {
            val bytes = withContext(Dispatchers.IO) { f.readBytes() }
            onPicked(f.name, bytes)
        }
    }
}

@Composable
fun rememberFileSaver(mime: String, onSaved: (String) -> Unit = {}): (String, ByteArray) -> Unit = { name, bytes ->
    saveDialog(name)?.let { f -> f.writeBytes(bytes); onSaved("Uloženo: ${f.path}") }
}

@Composable
fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

fun multicastLock(): AutoCloseable? = null

/** PC: the server simply runs inside the app. */
fun lanBackground(on: Boolean, start: () -> Unit, stop: () -> Unit) { if (on) start() else stop() }
