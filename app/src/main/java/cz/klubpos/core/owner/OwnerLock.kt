package cz.klubpos.core.owner

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import cz.klubpos.core.crypto.DatabaseKeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tablet side of the owner key. While no owner phone is paired, management is open (setup mode).
 * After pairing, protected actions need an approval code from the owner's phone; a successful
 * approval unlocks management for [UNLOCK_MINUTES].
 */
class OwnerLock(context: Context) {
    companion object { const val UNLOCK_MINUTES = 10; private const val MAX_ATTEMPTS = 3 }

    private val prefs = EncryptedSharedPreferences.create(
        context, "owner_lock", DatabaseKeyManager.masterKey(context),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _paired = MutableStateFlow(prefs.contains("secret"))
    val paired: StateFlow<Boolean> = _paired
    private val _unlockedUntil = MutableStateFlow(0L)
    val unlockedUntil: StateFlow<Long> = _unlockedUntil

    private val clubId get() = prefs.getString("clubId", null)
    private val secret get() = prefs.getString("secret", null)?.let { OwnerCrypto.decodeSecret(it) }

    fun isUnlocked(now: Long = System.currentTimeMillis()) = !_paired.value || now < _unlockedUntil.value
    fun lock() { _unlockedUntil.value = 0 }

    // ---------- Pairing ----------
    data class PendingPair(val clubId: String, val secret: ByteArray, val payload: String)
    private var pending: PendingPair? = null

    /** New random secret each time; previous pairing stays valid until [confirmPairing] succeeds. */
    fun beginPairing(clubName: String): PendingPair {
        check(isUnlocked()) { "Nejdřív odemkni správu klíčem majitele." }
        val id = clubId ?: OwnerCrypto.newClubId()
        val s = OwnerCrypto.newSecret()
        return PendingPair(id, s, OwnerCrypto.pairPayload(id, s, clubName)).also { pending = it }
    }

    fun confirmPairing(code: String): Boolean {
        val p = pending ?: return false
        if (!OwnerCrypto.equalsConstantTime(code.trim(), OwnerCrypto.confirmCode(p.secret))) return false
        prefs.edit().putString("clubId", p.clubId).putString("secret", OwnerCrypto.encodeSecret(p.secret))
            .putLong("pairedAt", System.currentTimeMillis()).apply()
        pending = null
        _paired.value = true
        _unlockedUntil.value = System.currentTimeMillis() + UNLOCK_MINUTES * 60_000L
        return true
    }

    fun cancelPairing() { pending = null }

    /** Removes the owner key (management becomes open). Only while unlocked. */
    fun unpair() {
        check(isUnlocked())
        prefs.edit().clear().apply()
        _paired.value = false
    }

    // ---------- Approval ----------
    class Challenge(val clubId: String, val nonce: String, val action: String) {
        val payload get() = OwnerCrypto.requestPayload(clubId, nonce, action)
        var attempts = 0
    }

    fun newChallenge(action: String): Challenge? =
        clubId?.let { Challenge(it, OwnerCrypto.newNonce(), action) }

    sealed interface Result { data object Ok : Result; data class Wrong(val left: Int) : Result; data object Expired : Result }

    fun verify(ch: Challenge, code: String): Result {
        val s = secret ?: return Result.Expired
        if (ch.attempts >= MAX_ATTEMPTS) return Result.Expired
        ch.attempts++
        return if (OwnerCrypto.equalsConstantTime(code.trim(), OwnerCrypto.response(s, ch.clubId, ch.nonce, ch.action))) {
            _unlockedUntil.value = System.currentTimeMillis() + UNLOCK_MINUTES * 60_000L
            Result.Ok
        } else if (ch.attempts >= MAX_ATTEMPTS) Result.Expired else Result.Wrong(MAX_ATTEMPTS - ch.attempts)
    }

    /** Backup code from the owner's phone (if the phone is lost). */
    fun verifyRecovery(code: String): Boolean {
        val s = secret ?: return false
        val ok = OwnerCrypto.equalsConstantTime(code.uppercase().replace(" ", ""), OwnerCrypto.recoveryCode(s))
        if (ok) _unlockedUntil.value = System.currentTimeMillis() + UNLOCK_MINUTES * 60_000L
        return ok
    }
}
