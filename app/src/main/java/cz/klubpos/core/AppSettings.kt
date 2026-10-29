package cz.klubpos.core

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import cz.klubpos.core.crypto.DatabaseKeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    /** Bank account for QR payments: IBAN (CZ65…) or Czech format 123456789/0800. Empty = QR off. */
    val account: String = "",
    /** Shown in the banking app as the payment message prefix. */
    val clubName: String = "Klub",
    /** Warn when a member's debt reaches this many credits. 0 = no limit. */
    val debtLimit: Long = 0,
    /** Keep the tablet screen on while the app is open. */
    val keepScreenOn: Boolean = true,
    /** Guided tour already shown once (auto-start only on first launch). */
    val tourDone: Boolean = false,
    /** Log the bartender out after this many minutes without touching the screen. 0 = never. */
    val autoLockMinutes: Int = 0,
    /** Product ordering: "ALPHA" = A–Z, "TYPE" = by category (jídlo / alko / limo…) then A–Z. */
    val productSort: String = "TYPE",
)

/** Small encrypted key-value store for app configuration. */
class AppSettings(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "klubpos_settings", DatabaseKeyManager.masterKey(context),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<Settings> = _flow.asStateFlow()
    val current get() = _flow.value

    private fun read() = Settings(
        account = prefs.getString("account", "") ?: "",
        clubName = prefs.getString("clubName", "Klub") ?: "Klub",
        debtLimit = prefs.getLong("debtLimit", 0),
        keepScreenOn = prefs.getBoolean("keepScreenOn", true),
        tourDone = prefs.getBoolean("tourDone", false),
        autoLockMinutes = prefs.getInt("autoLockMinutes", 0),
        productSort = prefs.getString("productSort", "TYPE") ?: "TYPE",
    )

    fun update(transform: (Settings) -> Settings) {
        val s = transform(current)
        prefs.edit()
            .putString("account", s.account.trim())
            .putString("clubName", s.clubName.trim())
            .putLong("debtLimit", s.debtLimit)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putBoolean("tourDone", s.tourDone)
            .putInt("autoLockMinutes", s.autoLockMinutes)
            .putString("productSort", s.productSort)
            .apply()
        _flow.value = s
    }
}
