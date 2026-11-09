package cz.klubpos.eet

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import cz.klubpos.KlubPosApp
import cz.klubpos.core.crypto.DatabaseKeyManager
import cz.klubpos.data.db.entity.EetRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Base64

/** EET settings (encrypted on the device). */
data class EetConfig(
    val enabled: Boolean = false,
    /** true = Finanční správa playground (testing, POK ends with "-ff"), false = production. */
    val playground: Boolean = true,
    /** true = verification mode: the server only checks the message, nothing is recorded. */
    val verification: Boolean = false,
    val idJednotky: Int = 0,
    val idPokl: String = "POKLADNA-1",
    val certB64: String = "",
    val certPassword: String = "",
    val nextSeq: Long = 1,
) {
    val hasCert get() = certB64.isNotEmpty()
    val url get() = if (playground) Eet.URL_PLAYGROUND else Eet.URL_PRODUCTION
}

/**
 * Evidence of payments: every payment → one record → signed message to EET (2 s limit),
 * failed sends are retried in the background with prvni_zaslani = false.
 */
class EetService(private val app: KlubPosApp) {
    private val prefs = EncryptedSharedPreferences.create(
        app, "eet_config", DatabaseKeyManager.masterKey(app),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val _config = MutableStateFlow(read())
    val config: StateFlow<EetConfig> = _config
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val dao get() = app.repository.eetDao()

    private fun read() = EetConfig(
        enabled = prefs.getBoolean("enabled", false),
        playground = prefs.getBoolean("playground", true),
        verification = prefs.getBoolean("verification", false),
        idJednotky = prefs.getInt("idJednotky", 0),
        idPokl = prefs.getString("idPokl", "POKLADNA-1") ?: "POKLADNA-1",
        certB64 = prefs.getString("cert", "") ?: "",
        certPassword = prefs.getString("certPassword", "") ?: "",
        nextSeq = prefs.getLong("nextSeq", 1),
    )

    fun update(f: (EetConfig) -> EetConfig) {
        val c = f(_config.value)
        prefs.edit().putBoolean("enabled", c.enabled).putBoolean("playground", c.playground)
            .putBoolean("verification", c.verification).putInt("idJednotky", c.idJednotky)
            .putString("idPokl", c.idPokl.trim()).putString("cert", c.certB64).putString("certPassword", c.certPassword)
            .putLong("nextSeq", c.nextSeq).commit()
        _config.value = c
    }

    /** Loads the certificate; throws with a readable message when the file or password is wrong. */
    fun loadCert(c: EetConfig = _config.value): Eet.Cert {
        if (!c.hasCert) error("Není nahraný pokladní certifikát (.p12)")
        return runCatching { Eet.Cert.fromPkcs12(Base64.getDecoder().decode(c.certB64), c.certPassword) }
            .getOrElse { throw IllegalStateException("Certifikát nejde otevřít – špatné heslo nebo soubor") }
    }

    /** Store a new .p12 after checking it opens. Returns the EIČ from the certificate. */
    fun importCert(bytes: ByteArray, password: String): String {
        val cert = Eet.Cert.fromPkcs12(bytes, password)
        val eic = cert.eic ?: error("V certifikátu není EIČ")
        update { it.copy(certB64 = Base64.getEncoder().encodeToString(bytes), certPassword = password) }
        return eic
    }

    private fun nextPoradCis(): String {
        val c = _config.value
        update { it.copy(nextSeq = c.nextSeq + 1) }
        return "${c.idPokl.take(12)}/${c.nextSeq.toString().padStart(6, '0')}".take(25)
    }

    /** Called by the repository after each payment. */
    fun recordPayment(paymentEntryId: Long, memberId: Long, amount: Long, at: Long) {
        val c = _config.value
        if (!c.enabled) return
        scope.launch {
            val id = lock.withLock {
                dao.insert(EetRecord(paymentEntryId = paymentEntryId, memberId = memberId, amount = amount,
                    datTrzby = Eet.dateTime(at), poradCis = nextPoradCis(), playground = c.playground, verification = c.verification))
            }
            send(id)
        }
    }

    /** Sends one record now; first try has prvni_zaslani = true. */
    suspend fun send(id: Long): Eet.Result? = withContext(Dispatchers.IO) {
        lock.withLock {
            val r = dao.get(id) ?: return@withLock null
            val c = _config.value
            val result = runCatching {
                val cert = loadCert(c)
                val t = Eet.Trzba(cert.eic ?: error("V certifikátu není EIČ"), c.idJednotky, c.idPokl, r.poradCis, r.datTrzby, Eet.amount(r.amount))
                t.validate().firstOrNull()?.let { error(it) }
                val msg = Eet.buildSigned(t, cert, prvniZaslani = r.attempts == 0, overeni = r.verification)
                Eet.send(if (r.playground) Eet.URL_PLAYGROUND else Eet.URL_PRODUCTION, msg)
            }.getOrElse { Eet.Result(false, errorText = it.message, network = true) }
            val status = when {
                result.ok -> "OK"
                result.errorCode == 0 -> "CHECKED"
                result.retry -> "PENDING"
                else -> "FAILED"
            }
            dao.update(r.copy(status = status, pok = result.pok, attempts = r.attempts + 1, lastTry = System.currentTimeMillis(),
                error = if (result.ok) null else listOfNotNull(result.errorCode?.let { "kód $it" }, result.errorText).joinToString(" – "),
                warnings = result.warnings.joinToString("\n").ifEmpty { null }))
            result
        }
    }

    /** Test button: one message of [amount] Kč with the current settings, not linked to any member. */
    suspend fun sendTest(amount: Long = 1, verification: Boolean = _config.value.verification): Eet.Result {
        val c = _config.value
        val id = lock.withLock {
            dao.insert(EetRecord(amount = amount, datTrzby = Eet.dateTime(System.currentTimeMillis()), poradCis = nextPoradCis(),
                playground = c.playground, verification = verification))
        }
        return send(id) ?: Eet.Result(false, errorText = "Záznam nenalezen")
    }

    suspend fun retryAll() { dao.pending().forEach { send(it.id) } }

    /** Background loop: every minute try what is still pending. */
    suspend fun retryLoop() {
        while (true) {
            delay(60_000)
            if (_config.value.enabled) runCatching { retryAll() }
        }
    }
}
