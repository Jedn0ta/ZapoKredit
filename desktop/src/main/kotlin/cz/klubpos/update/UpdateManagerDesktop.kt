package cz.klubpos.update

import cz.klubpos.net.Json
import cz.klubpos.net.arr
import cz.klubpos.net.long
import cz.klubpos.net.obj
import cz.klubpos.net.str
import cz.klubpos.platform.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlin.system.exitProcess

data class ReleaseInfo(val version: String, val notes: String, val apkUrl: String, val apkSize: Long, val pageUrl: String)

sealed interface UpdateCheck {
    data class Available(val release: ReleaseInfo) : UpdateCheck
    data class UpToDate(val latest: String) : UpdateCheck
    data class Error(val message: String) : UpdateCheck
}

/** PC: update from GitHub Releases – downloads the new .msi and starts the installer (data stay). */
class UpdateManager {
    val currentVersion: String = Platform.versionName
    val repo: String = Platform.updateRepo
    val repoUrl get() = "https://github.com/$repo"

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        try {
            val conn = URI("https://api.github.com/repos/$repo/releases/latest").toURL().openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json"); conn.connectTimeout = 10_000; conn.readTimeout = 15_000
            if (conn.responseCode !in 200..299) return@withContext UpdateCheck.Error("GitHub odpověděl chybou ${conn.responseCode}.")
            val json = Json.parse(conn.inputStream.bufferedReader().use { it.readText() }).obj()
            val version = (json.str("tag_name") ?: "").removePrefix("v")
            val msi = json["assets"].arr().map { it.obj() }.firstOrNull { (it.str("name") ?: "").endsWith(".msi", true) }
                ?: return@withContext UpdateCheck.Error("Vydání $version neobsahuje instalátor pro PC.")
            val info = ReleaseInfo(version, json.str("body") ?: "", msi.str("browser_download_url") ?: "", msi.long("size") ?: 0, json.str("html_url") ?: repoUrl)
            if (isNewer(version, currentVersion)) UpdateCheck.Available(info) else UpdateCheck.UpToDate(version)
        } catch (e: Exception) {
            UpdateCheck.Error("Nelze se spojit s GitHubem (${e.javaClass.simpleName}).")
        }
    }

    suspend fun download(release: ReleaseInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val out = File(System.getProperty("java.io.tmpdir"), "ZapoKredit-${release.version}.msi")
        var url = release.apkUrl
        var conn: HttpURLConnection
        while (true) {
            conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            if (code in 300..399) { url = conn.getHeaderField("Location"); continue }
            break
        }
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
        conn.inputStream.use { input -> out.outputStream().use { o ->
            val buf = ByteArray(64 * 1024); var done = 0L
            while (true) { val n = input.read(buf); if (n < 0) break; o.write(buf, 0, n); done += n
                if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f)) }
        } }
        out
    }

    fun canInstall(): Boolean = true
    fun openInstallPermissionSettings() = Unit

    /** Starts the Windows installer and closes the app (the installer replaces the running files). */
    fun install(msi: File) {
        ProcessBuilder("msiexec", "/i", msi.absolutePath).start()
        exitProcess(0)
    }

    companion object {
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate); val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
