package cz.klubpos.core.owner

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Owner key protocol (offline, shared by the POS tablet and the "Klíč" app on the owner's phone).
 *
 * Pairing  – tablet generates a random 256-bit secret S and a club id, shows them once as a QR:
 *            ZK1:PAIR:<clubId>:<S base64url>:<club name>
 *            The owner's phone stores S (Keystore-encrypted). The phone then shows a 6-digit
 *            confirmation code HMAC(S,"confirm") that the tablet checks – proving the scan worked.
 *
 * Approval – tablet shows a QR challenge with a fresh random nonce and the requested action:
 *            ZK1:REQ:<clubId>:<nonce base64url>:<action>
 *            The phone shows the action, and after the owner approves it displays an 8-digit
 *            response = HMAC-SHA256(S, clubId|nonce|action) truncated. The bartender types it on
 *            the tablet. Each nonce is single-use, so a code can't be reused or guessed offline.
 *
 * S never leaves either device after pairing and is never shown again.
 */
object OwnerCrypto {
    private val rnd = SecureRandom()
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val b64d = Base64.getUrlDecoder()

    fun newSecret(): ByteArray = ByteArray(32).also { rnd.nextBytes(it) }
    fun newClubId(): String = b64.encodeToString(ByteArray(6).also { rnd.nextBytes(it) })
    fun newNonce(): String = b64.encodeToString(ByteArray(12).also { rnd.nextBytes(it) })

    fun encodeSecret(s: ByteArray): String = b64.encodeToString(s)
    fun decodeSecret(s: String): ByteArray = b64d.decode(s)

    private fun hmac(secret: ByteArray, msg: String): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }.doFinal(msg.toByteArray())

    /** RFC 4226-style dynamic truncation to [digits] decimal digits. */
    private fun digits(h: ByteArray, digits: Int): String {
        val o = h.last().toInt() and 0x0f
        val bin = ((h[o].toInt() and 0x7f) shl 24) or ((h[o + 1].toInt() and 0xff) shl 16) or
            ((h[o + 2].toInt() and 0xff) shl 8) or (h[o + 3].toInt() and 0xff)
        var mod = 1; repeat(digits) { mod *= 10 }
        return (bin % mod).toString().padStart(digits, '0')
    }

    fun confirmCode(secret: ByteArray) = digits(hmac(secret, "confirm"), 6)
    fun response(secret: ByteArray, clubId: String, nonce: String, action: String) =
        digits(hmac(secret, "$clubId|$nonce|$action"), 8)

    /** Backup code the owner writes down; unlocks the tablet if the phone is lost. */
    fun recoveryCode(secret: ByteArray): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val h = hmac(secret, "recovery")
        return (0 until 16).map { alphabet[(h[it].toInt() and 0xff) % alphabet.length] }
            .chunked(4).joinToString("-") { it.joinToString("") }
    }

    fun equalsConstantTime(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    // ---- QR payloads ----
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

    fun pairPayload(clubId: String, secret: ByteArray, clubName: String) = "ZK1:PAIR:$clubId:${encodeSecret(secret)}:${enc(clubName)}"
    fun requestPayload(clubId: String, nonce: String, action: String) = "ZK1:REQ:$clubId:$nonce:${enc(action)}"

    sealed interface Parsed
    data class Pair(val clubId: String, val secret: ByteArray, val clubName: String) : Parsed
    data class Request(val clubId: String, val nonce: String, val action: String) : Parsed

    fun parse(text: String): Parsed? {
        val p = text.trim().split(":")
        if (p.size < 5 || p[0] != "ZK1") return null
        return runCatching {
            when (p[1]) {
                "PAIR" -> Pair(p[2], decodeSecret(p[3]), dec(p.drop(4).joinToString(":")))
                "REQ" -> Request(p[2], p[3], dec(p.drop(4).joinToString(":")))
                else -> null
            }
        }.getOrNull()
    }
}
