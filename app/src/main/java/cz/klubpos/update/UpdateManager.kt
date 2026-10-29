package cz.klubpos.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import cz.klubpos.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val version: String,        // "1.2.3" (tag without leading v)
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String,
)

sealed interface UpdateCheck {
    data class Available(val release: ReleaseInfo) : UpdateCheck
    data class UpToDate(val latest: String) : UpdateCheck
    data class Error(val message: String) : UpdateCheck
}

/**
 * Self-update from GitHub Releases of [BuildConfig.UPDATE_REPO].
 *
 * A release must have a tag like `v1.2.3` and an attached `.apk` asset (the CI workflow in
 * `.github/workflows/release.yml` does both). The APK must be signed with the same key as the
 * installed app, otherwise Android rejects the update.
 */
class UpdateManager(private val context: Context) {

    val currentVersion: String = BuildConfig.VERSION_NAME
    val repo: String = BuildConfig.UPDATE_REPO
    val repoUrl get() = "https://github.com/$repo"

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ZapoKredit-updater")
                connectTimeout = 10_000; readTimeout = 15_000
            }
            when (conn.responseCode) {
                404 -> return@withContext UpdateCheck.Error("V repozitáři zatím není žádné vydání.")
                403 -> return@withContext UpdateCheck.Error("GitHub dočasně omezil dotazy, zkus to později.")
                !in 200..299 -> return@withContext UpdateCheck.Error("GitHub odpověděl chybou ${conn.responseCode}.")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val version = json.getString("tag_name").removePrefix("v").removePrefix("V")
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { a ->
                    val n = a.getString("name")
                    // Each app updates itself only: the key app takes the "-Klic-" APK, the tablet the other one.
                    n.endsWith(".apk", ignoreCase = true) && n.contains("Klic", ignoreCase = true) == BuildConfig.IS_KEY_APP
                }
                ?: return@withContext UpdateCheck.Error("Vydání $version neobsahuje APK soubor.")
            val info = ReleaseInfo(
                version = version,
                notes = json.optString("body").trim(),
                apkUrl = apk.getString("browser_download_url"),
                apkSize = apk.optLong("size"),
                pageUrl = json.optString("html_url"),
            )
            if (isNewer(version, currentVersion)) UpdateCheck.Available(info) else UpdateCheck.UpToDate(version)
        } catch (e: Exception) {
            UpdateCheck.Error("Nelze se spojit s GitHubem (${e.javaClass.simpleName}). Je tablet online?")
        }
    }

    /** Downloads the APK into app cache, reporting progress 0..1. Returns the file. */
    suspend fun download(release: ReleaseInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "ZapoKredit-${release.version}.apk")
        // GitHub redirects asset downloads to its CDN; HttpURLConnection follows same-protocol redirects.
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ZapoKredit-updater")
            connectTimeout = 15_000; readTimeout = 30_000
        }
        if (conn.responseCode !in 200..299) error("Stažení selhalo (${conn.responseCode})")
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    output.write(buf, 0, n); done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        out
    }

    /** Android 8+ requires the user to allow "install unknown apps" for this app once. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Hands the APK to the system installer; the user confirms. App data (encrypted DB) is kept. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        /** Numeric semver compare: "1.10.0" > "1.9.3". Non-numeric suffixes (-beta) are ignored. */
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
