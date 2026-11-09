package cz.klubpos.core.crypto

import cz.klubpos.platform.Aes
import cz.klubpos.platform.Platform
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO

/** PC: member photos and product images, downscaled and AES-GCM encrypted (same API as on Android). */
class ImageStore {
    private val dir = File(Platform.dataDir, "images").apply { mkdirs() }
    private val key = Platform.subKey("images")
    private fun file(id: String) = File(dir, "$id.bin")

    fun save(raw: ByteArray, maxSide: Int = 512): String {
        val src = ImageIO.read(raw.inputStream()) ?: throw IllegalArgumentException("Neplatný obrázek")
        val scale = minOf(1.0, maxSide.toDouble() / maxOf(src.width, src.height))
        val w = (src.width * scale).toInt().coerceAtLeast(1); val h = (src.height * scale).toInt().coerceAtLeast(1)
        val dst = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        dst.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            drawImage(src, 0, 0, w, h, null); dispose()
        }
        val out = ByteArrayOutputStream(); ImageIO.write(dst, "jpg", out)
        val id = UUID.randomUUID().toString()
        file(id).writeBytes(Aes.seal(key, out.toByteArray()))
        return id
    }

    fun load(id: String): ByteArray? = file(id).takeIf { it.exists() }?.let { runCatching { Aes.open(key, it.readBytes()) }.getOrNull() }

    fun delete(id: String?) { if (id != null) file(id).delete() }
}
