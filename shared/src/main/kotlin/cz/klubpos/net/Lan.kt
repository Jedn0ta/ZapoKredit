package cz.klubpos.net

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * LAN link between the main register (bar tablet = server) and its clients: waiter phones and the PC register.
 *
 * Security: every request/response body is AES-256-GCM encrypted with a key derived from the club code
 * (20 characters ≈ 100 bits) that is shown on the register as a QR code. Without the code nothing can be read or sent.
 */
object Lan {
    const val PORT = 8770
    const val DISCOVERY_PORT = 8771
    const val PATH = "/zk"
    const val VERSION = 1
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"   // no 0/O, 1/I
    /** Requests older/newer than this are refused (replay protection). */
    const val MAX_SKEW_MS = 15 * 60_000L

    fun newKey(rnd: SecureRandom = SecureRandom()): String = (1..20).map { ALPHABET[rnd.nextInt(ALPHABET.length)] }.joinToString("")

    /** "ABCD-EFGH-…" → "ABCDEFGH…", lenient about case, spaces and look-alike characters. */
    fun normalizeKey(s: String): String = s.uppercase().filter { it in ALPHABET }

    fun formatKey(k: String) = k.chunked(4).joinToString("-")
    fun isValidKey(k: String) = k.length == 20 && k.all { it in ALPHABET }

    /** Short public id of a key, used by discovery to find "our" register. */
    fun keyId(key: String) = sha256("zk-id|$key").take(4).joinToString("") { "%02x".format(it) }

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())

    /** Encryption for one club code. */
    class Crypto(key: String) {
        private val k = SecretKeySpec(sha256("zk-lan|$key"), "AES")
        private val rnd = SecureRandom()

        fun seal(plain: String): String {
            val iv = ByteArray(12).also(rnd::nextBytes)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, iv))
            c.updateAAD("ZK$VERSION".toByteArray())
            return Base64.getEncoder().encodeToString(iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)))
        }

        /** null = wrong key or tampered data. */
        fun open(sealed: String): String? = runCatching {
            val all = Base64.getDecoder().decode(sealed.trim())
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, all, 0, 12))
            c.updateAAD("ZK$VERSION".toByteArray())
            String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
        }.getOrNull()
    }

    /** What the register shows as QR: address + key. */
    data class Code(val host: String, val port: Int, val key: String) {
        fun text() = "ZK$VERSION;$host;$port;$key"
        companion object {
            fun parse(s: String): Code? {
                val p = s.trim().split(';')
                if (p.size != 4 || !p[0].startsWith("ZK")) return null
                val key = normalizeKey(p[3]); val port = p[2].toIntOrNull() ?: return null
                return if (isValidKey(key) && p[1].isNotBlank()) Code(p[1], port, key) else null
            }
        }
    }

    // ---------------- discovery (UDP broadcast) ----------------

    data class Found(val host: String, val port: Int, val keyId: String, val club: String)

    fun discoveryReply(port: Int, keyId: String, club: String) = "ZK!|$port|$keyId|${club.replace("|", " ")}"

    /** Asks all registers on the local network to answer. */
    fun discover(timeoutMs: Int = 1500): List<Found> {
        val out = LinkedHashMap<String, Found>()
        runCatching {
            DatagramSocket().use { s ->
                s.broadcast = true; s.soTimeout = 300
                val msg = "ZK?".toByteArray()
                val targets = buildSet {
                    add(InetAddress.getByName("255.255.255.255"))
                    NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().filter { it.isUp && !it.isLoopback }
                        .flatMap { it.interfaceAddresses }.mapNotNull { it.broadcast }.forEach { add(it) }
                }
                targets.forEach { runCatching { s.send(DatagramPacket(msg, msg.size, it, DISCOVERY_PORT)) } }
                val end = System.currentTimeMillis() + timeoutMs
                val buf = ByteArray(512)
                while (System.currentTimeMillis() < end) {
                    val p = DatagramPacket(buf, buf.size)
                    try { s.receive(p) } catch (_: java.net.SocketTimeoutException) { continue }
                    val parts = String(p.data, 0, p.length).split('|')
                    if (parts.size >= 4 && parts[0] == "ZK!") {
                        val host = p.address.hostAddress
                        out[host] = Found(host, parts[1].toIntOrNull() ?: PORT, parts[2], parts.drop(3).joinToString("|"))
                    }
                }
            }
        }
        return out.values.toList()
    }

    fun localIPv4(): String? = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        .filter { it.isUp && !it.isLoopback }.flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
}

/** Thrown by [LanClient] with a message for the user. */
class LanException(msg: String) : Exception(msg)

/** HTTP client used by the waiter app and the PC register. */
class LanClient(@Volatile var code: Lan.Code, private val device: String) {
    private var crypto = Lan.Crypto(code.key)

    fun update(c: Lan.Code) { code = c; crypto = Lan.Crypto(c.key) }

    fun call(op: String, args: Map<String, Any?> = emptyMap(), timeoutMs: Int = 6000): Map<String, Any?> {
        val req = Json.write(args + mapOf("op" to op, "ts" to System.currentTimeMillis(), "device" to device))
        val conn = try {
            (URI("http://${code.host}:${code.port}${Lan.PATH}").toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; connectTimeout = timeoutMs; readTimeout = timeoutMs
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                outputStream.use { it.write(crypto.seal(req).toByteArray()) }
            }
        } catch (e: Exception) { throw LanException("Pokladna nedostupná (${code.host}). Je zapnutá a na stejné Wi-Fi?") }
        val status = try { conn.responseCode } catch (e: Exception) { throw LanException("Pokladna neodpovídá") }
        if (status == 403) throw LanException("Špatný kód pokladny – naskenuj ho znovu")
        val body = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        val plain = crypto.open(body) ?: throw LanException("Neplatná odpověď pokladny (HTTP $status)")
        val res = Json.parse(plain).obj()
        if (res["ok"] != true) throw LanException(res.str("error") ?: "Chyba pokladny")
        return res
    }
}
