package cz.klubpos.core.crypto

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Bartender PINs are never stored – only a salted PBKDF2-SHA256 hash. */
object Pin {
    private const val ITERATIONS = 60_000

    fun hash(pin: String, salt: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }): Pair<String, String> {
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)).encoded
        return Base64.encodeToString(key, Base64.NO_WRAP) to Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun verify(pin: String, hash: String, salt: String): Boolean {
        val (candidate, _) = hash(pin, Base64.decode(salt, Base64.NO_WRAP))
        return MessageDigest.isEqual(candidate.toByteArray(), hash.toByteArray())
    }
}
