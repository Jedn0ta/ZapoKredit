package cz.klubpos.upload

import android.content.Context
import android.net.wifi.WifiManager
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Base64

/**
 * Tiny one-shot HTTP server on the tablet, used to receive a photo from any phone on the same Wi-Fi.
 *
 * Flow: tablet starts the server -> shows a QR with http://<tablet-ip>:<port>/u/<token>
 * -> phone scans it, opens a page, takes/chooses a photo -> POST to the same URL
 * -> tablet gets the bytes via [onImage], stores them encrypted, and the server stops.
 *
 * Security: random 128-bit token in the URL, single use, expires after [ttlMillis],
 * 10 MB size cap, only listens while the QR dialog is open. The photo travels over local Wi-Fi
 * unencrypted (plain HTTP) – acceptable for a closed club network; see README for an HTTPS option.
 */
class PhotoUploadServer(
    private val context: Context,
    private val onImage: (ByteArray) -> Unit,
    private val ttlMillis: Long = 5 * 60_000,
) : NanoHTTPD(0) {

    val token: String = ByteArray(16).also { SecureRandom().nextBytes(it) }
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    private val createdAt = System.currentTimeMillis()
    @Volatile private var used = false

    fun startServer(): String? {
        start(SOCKET_READ_TIMEOUT, true)
        val ip = localIp() ?: return null
        return "http://$ip:$listeningPort/u/$token"
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.uri != "/u/$token" || used || System.currentTimeMillis() - createdAt > ttlMillis) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Odkaz vypršel.")
        }
        return when (session.method) {
            Method.GET -> html(PAGE)
            Method.POST -> receive(session)
            else -> newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "")
        }
    }

    private fun receive(session: IHTTPSession): Response {
        val len = session.headers["content-length"]?.toLongOrNull() ?: 0
        if (len > 10 * 1024 * 1024) return html(msg("Fotka je moc velká (max 10 MB)."))
        val files = HashMap<String, String>()
        return try {
            session.parseBody(files)
            val path = files["photo"] ?: return html(msg("Nebyla vybrána žádná fotka."))
            val bytes = File(path).readBytes()
            used = true
            onImage(bytes)
            html(msg("✓ Hotovo, fotka je na tabletu. Můžeš zavřít stránku."))
        } catch (e: Exception) {
            html(msg("Chyba: ${e.message}"))
        }
    }

    private fun html(body: String) = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

    private fun localIp(): String? {
        // Prefer the Wi-Fi interface; fall back to any site-local IPv4 address.
        @Suppress("DEPRECATION")
        val wifiIp = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.connectionInfo?.ipAddress ?: 0
        if (wifiIp != 0) {
            return "%d.%d.%d.%d".format(wifiIp and 0xff, wifiIp shr 8 and 0xff, wifiIp shr 16 and 0xff, wifiIp shr 24 and 0xff)
        }
        return NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    }

    private companion object {
        fun msg(text: String) = """<!doctype html><meta name=viewport content="width=device-width">
            <body style="font-family:sans-serif;background:#1b1a17;color:#f3e9d2;padding:24px;font-size:20px">$text</body>"""

        val PAGE = """<!doctype html><html lang=cs><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1">
<title>Nahrát fotku</title>
<body style="font-family:sans-serif;background:#1b1a17;color:#f3e9d2;padding:24px">
<h2>Fotka do klubu</h2>
<form method=post enctype=multipart/form-data>
<input type=file name=photo accept="image/*" capture=environment required
  style="font-size:18px;margin:16px 0;width:100%">
<button style="font-size:20px;padding:14px 24px;background:#d9a441;border:0;border-radius:10px;width:100%">Odeslat</button>
</form></body></html>"""
    }
}
