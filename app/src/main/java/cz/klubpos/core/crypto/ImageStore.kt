package cz.klubpos.core.crypto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.security.crypto.EncryptedFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Encrypted image storage for member photos and product thumbnails.
 * Each image is stored as an AES-256-GCM (streaming) EncryptedFile; the DB only keeps the id.
 * Images are downscaled before storing so the tablet grid stays fast.
 */
class ImageStore(private val context: Context) {

    private val dir = File(context.filesDir, "images").apply { mkdirs() }
    private val masterKey by lazy { DatabaseKeyManager.masterKey(context) }

    private fun file(id: String) = File(dir, "$id.bin")

    private fun encrypted(f: File) = EncryptedFile.Builder(
        context, f, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    /** Decode raw bytes (jpeg/png from camera or upload), downscale, encrypt, return new image id. */
    fun save(raw: ByteArray, maxSide: Int = 512): String {
        val src = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: throw IllegalArgumentException("Neplatný obrázek")
        val scale = maxSide.toFloat() / maxOf(src.width, src.height)
        val bmp = if (scale < 1f)
            Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
        else src
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)

        val id = UUID.randomUUID().toString()
        encrypted(file(id)).openFileOutput().use { it.write(out.toByteArray()) }
        return id
    }

    fun load(id: String): ByteArray? {
        val f = file(id)
        if (!f.exists()) return null
        return runCatching { encrypted(f).openFileInput().use { it.readBytes() } }.getOrNull()
    }

    fun delete(id: String?) {
        if (id != null) file(id).delete()
    }
}
