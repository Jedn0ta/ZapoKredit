package cz.klubpos.core.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Holds the random 256-bit passphrase for the SQLCipher database.
 *
 * The passphrase itself is stored in EncryptedSharedPreferences, whose key is wrapped by an
 * AES-256-GCM master key living in the Android Keystore (hardware-backed where available).
 * The raw key therefore never leaves the device and cannot be read from a backup or by
 * copying the app's data folder.
 *
 * TODO(later): optionally derive an extra layer from a staff PIN (PBKDF2/Argon2) so data is
 * unreadable even on an unlocked, stolen tablet.
 */
object DatabaseKeyManager {
    private const val PREFS = "klubpos_secure_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"

    fun masterKey(context: Context): MasterKey =
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS,
        masterKey(context),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    @Synchronized
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val p = prefs(context)
        p.getString(KEY_DB_PASSPHRASE, null)?.let { return Base64.decode(it, Base64.NO_WRAP) }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        p.edit().putString(KEY_DB_PASSPHRASE, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
        return bytes
    }
}
