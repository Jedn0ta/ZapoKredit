package cz.klubpos.eet

import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

/**
 * EET 2.0 (rozhraní v4.1) – sestavení, podpis (WS-Security, Exclusive C14N, RSA-SHA256) a odeslání datové zprávy.
 * Čistý JVM kód bez Androidu, aby šel testovat (viz PaymentsTest / EetTest).
 *
 * Tělo SOAP i SignedInfo stavíme rovnou v kanonickém tvaru (exc-c14n), takže otisk i podpis
 * počítáme přímo z těchto řetězců a vkládáme je do obálky beze změny.
 */
object Eet {
    const val NS_SOAP = "http://schemas.xmlsoap.org/soap/envelope/"
    const val NS_EET = "http://fs.gov.cz/eet/schema/v4"
    const val NS_WSSE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
    const val NS_WSU = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
    const val NS_DS = "http://www.w3.org/2000/09/xmldsig#"
    const val C14N = "http://www.w3.org/2001/10/xml-exc-c14n#"
    const val SOAP_ACTION = "http://fs.gov.cz/eet/OdeslaniTrzby"

    const val URL_PLAYGROUND = "https://pg.trzbyeet.gov.cz/eet/services/EETServiceSOAP/v4"
    /** Produkční adresa zatím nebyla v podkladech – doplní se, až ji Finanční správa zveřejní. */
    const val URL_PRODUCTION = "https://prod.trzbyeet.gov.cz/eet/services/EETServiceSOAP/v4"

    private val PRAGUE = ZoneId.of("Europe/Prague")
    private val DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

    /** "2027-01-09T04:25:28+01:00" – lokální čas ČR s časovou zónou (SEČ/SELČ). */
    fun dateTime(epochMillis: Long): String =
        ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), PRAGUE).withNano(0).format(DT)

    /** Částka v Kč → "1234.50" (vždy 2 desetinná místa). */
    fun amount(kc: BigDecimal): String = kc.setScale(2, RoundingMode.HALF_UP).toPlainString()
    fun amount(kc: Long): String = amount(BigDecimal.valueOf(kc))

    private val STR20 = Regex("^[0-9a-zA-Z.,:;/#\\-_ ]{1,20}$")
    private val STR25 = Regex("^[0-9a-zA-Z.,:;/#\\-_ ]{1,25}$")
    private val EIC = Regex("^CZ[0-9]{8,10}$")

    /** Údaje jedné tržby. */
    data class Trzba(
        val eic: String,
        val idJednotky: Int,
        val idPokl: String,
        val poradCis: String,
        val datTrzby: String,
        val celkTrzba: String,
        val urcenoCerpZuct: String? = null,
        val cerpZuct: String? = null,
    ) {
        /** Kontroly podle XSD, ať chybu najdeme dřív než server. */
        fun validate(): List<String> = buildList {
            if (!EIC.matches(eic)) add("EIČ musí mít tvar CZ + 8 až 10 číslic")
            if (idJednotky !in 1..999_999_999) add("Číslo evidenční jednotky 1–999999999")
            if (!STR20.matches(idPokl)) add("Označení pokladny: 1–20 znaků (písmena bez diakritiky, číslice, . , : ; / # - _ mezera)")
            if (!STR25.matches(poradCis)) add("Pořadové číslo: 1–25 znaků")
            if (!Regex("^((0|-?[1-9]\\d{0,7})\\.\\d\\d|-0\\.(0[1-9]|[1-9]\\d))$").matches(celkTrzba)) add("Částka tržby má chybný formát")
        }
    }

    /** Pokladní certifikát s klíčem (z .p12). */
    class Cert(val key: PrivateKey, val cert: X509Certificate) {
        /** EIČ je v CN certifikátu. */
        val eic: String? get() = Regex("CN=(CZ[0-9]{8,10})").find(cert.subjectX500Principal.name)?.groupValues?.get(1)
        val validTo get() = cert.notAfter

        companion object {
            fun fromPkcs12(bytes: ByteArray, password: String): Cert {
                val ks = KeyStore.getInstance("PKCS12")
                ks.load(ByteArrayInputStream(bytes), password.toCharArray())
                val alias = ks.aliases().toList().first { ks.isKeyEntry(it) }
                return Cert(ks.getKey(alias, password.toCharArray()) as PrivateKey, ks.getCertificate(alias) as X509Certificate)
            }
        }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")
        .replace("\t", "&#x9;").replace("\n", "&#xA;").replace("\r", "&#xD;")

    /** Kanonický tvar <soapenv:Body> (exc-c14n): deklarace NS podle prefixu, pak atributy podle (NS URI, jméno). */
    fun canonicalBody(t: Trzba, uuid: String, datOdesl: String, prvniZaslani: Boolean, overeni: Boolean, bodyId: String): String {
        // atributy bez NS jsou řazeny abecedně
        val head = listOf("dat_odesl" to datOdesl, "overeni" to overeni.toString(), "prvni_zaslani" to prvniZaslani.toString(), "uuid_zpravy" to uuid)
        val data = listOfNotNull(
            "celk_trzba" to t.celkTrzba,
            t.cerpZuct?.let { "cerp_zuct" to it },
            "dat_trzby" to t.datTrzby,
            "eic_popl" to t.eic,
            "id_jednotky" to t.idJednotky.toString(),
            "id_pokl" to t.idPokl,
            "porad_cis" to t.poradCis,
            t.urcenoCerpZuct?.let { "urceno_cerp_zuct" to it },
        )
        fun attrs(l: List<Pair<String, String>>) = l.joinToString("") { (k, v) -> " $k=\"${esc(v)}\"" }
        return "<soapenv:Body xmlns:soapenv=\"$NS_SOAP\" xmlns:wsu=\"$NS_WSU\" wsu:Id=\"$bodyId\">" +
            "<eet:Trzba xmlns:eet=\"$NS_EET\">" +
            "<eet:Hlavicka${attrs(head)}></eet:Hlavicka>" +
            "<eet:Data${attrs(data)}></eet:Data>" +
            "</eet:Trzba></soapenv:Body>"
    }

    fun canonicalSignedInfo(bodyId: String, digestB64: String): String =
        "<ds:SignedInfo xmlns:ds=\"$NS_DS\">" +
            "<ds:CanonicalizationMethod Algorithm=\"$C14N\"></ds:CanonicalizationMethod>" +
            "<ds:SignatureMethod Algorithm=\"http://www.w3.org/2001/04/xmldsig-more#rsa-sha256\"></ds:SignatureMethod>" +
            "<ds:Reference URI=\"#$bodyId\">" +
            "<ds:Transforms><ds:Transform Algorithm=\"$C14N\"></ds:Transform></ds:Transforms>" +
            "<ds:DigestMethod Algorithm=\"http://www.w3.org/2001/04/xmlenc#sha256\"></ds:DigestMethod>" +
            "<ds:DigestValue>$digestB64</ds:DigestValue>" +
            "</ds:Reference></ds:SignedInfo>"

    data class Message(val uuid: String, val xml: String)

    /** Kompletní podepsaná SOAP zpráva. */
    fun buildSigned(t: Trzba, cert: Cert, prvniZaslani: Boolean, overeni: Boolean,
                    nowMillis: Long = System.currentTimeMillis(), uuid: String = UUID.randomUUID().toString()): Message {
        val bodyId = "id-" + uuid.replace("-", "").take(16)
        val tokId = "X509-" + uuid.replace("-", "").takeLast(16)
        val body = canonicalBody(t, uuid, dateTime(nowMillis), prvniZaslani, overeni, bodyId)
        val b64 = Base64.getEncoder()
        val digest = b64.encodeToString(MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8)))
        val signedInfo = canonicalSignedInfo(bodyId, digest)
        val sig = Signature.getInstance("SHA256withRSA").run {
            initSign(cert.key); update(signedInfo.toByteArray(Charsets.UTF_8)); b64.encodeToString(sign())
        }
        val certB64 = b64.encodeToString(cert.cert.encoded)
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<soapenv:Envelope xmlns:soapenv=\"$NS_SOAP\"><soapenv:Header>" +
            "<wsse:Security xmlns:wsse=\"$NS_WSSE\" xmlns:wsu=\"$NS_WSU\">" +
            "<wsse:BinarySecurityToken EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\" " +
            "ValueType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3\" wsu:Id=\"$tokId\">$certB64</wsse:BinarySecurityToken>" +
            "<ds:Signature xmlns:ds=\"$NS_DS\">$signedInfo" +
            "<ds:SignatureValue>$sig</ds:SignatureValue>" +
            "<ds:KeyInfo><wsse:SecurityTokenReference><wsse:Reference URI=\"#$tokId\" " +
            "ValueType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3\"/>" +
            "</wsse:SecurityTokenReference></ds:KeyInfo></ds:Signature>" +
            "</wsse:Security></soapenv:Header>$body</soapenv:Envelope>"
        return Message(uuid, xml)
    }

    /** Výsledek odeslání. */
    data class Result(
        val ok: Boolean,                 // POK přidělen
        val pok: String? = null,
        val errorCode: Int? = null,      // 0 = ověřovací mód v pořádku, -1 = dočasná chyba (zopakovat)
        val errorText: String? = null,
        val warnings: List<String> = emptyList(),
        val test: Boolean = false,       // odpověď z playgroundu
        val network: Boolean = false,    // odpověď nedorazila – zopakovat později
    ) {
        /** Má smysl zkusit znovu (výpadek spojení nebo dočasná chyba serveru). */
        val retry get() = network || (errorCode != null && errorCode < 0)
    }

    /** Rozbor odpovědi (potvrzovací i chybová zpráva). */
    fun parse(xml: String): Result {
        fun attr(el: String, name: String) =
            Regex("<(?:\\w+:)?$el\\b[^>]*\\b$name=\"([^\"]*)\"").find(xml)?.groupValues?.get(1)
        val warnings = Regex("<(?:\\w+:)?Varovani\\b[^>]*kod_varov=\"(\\d+)\"[^>]*>([^<]*)<").findAll(xml)
            .map { "${it.groupValues[1]}: ${it.groupValues[2].trim()}" }.toList()
        val pok = attr("Potvrzeni", "pok")
        if (pok != null) return Result(true, pok = pok, warnings = warnings, test = attr("Potvrzeni", "test") == "true")
        val code = attr("Chyba", "kod")?.toIntOrNull()
        val text = Regex("<(?:\\w+:)?Chyba\\b[^>]*>([^<]*)<").find(xml)?.groupValues?.get(1)?.trim()
        if (code != null) return Result(false, errorCode = code, errorText = text, warnings = warnings, test = attr("Chyba", "test") == "true")
        val fault = Regex("<faultstring>([^<]*)<").find(xml)?.groupValues?.get(1)
        return Result(false, errorCode = -1, errorText = fault ?: "Neznámá odpověď serveru")
    }

    /** Odeslání. [timeoutMs] – mezní doba odezvy; při překročení se tržba odešle dodatečně. */
    fun send(url: String, msg: Message, timeoutMs: Int = 2000): Result = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
        c.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
        c.setRequestProperty("SOAPAction", SOAP_ACTION)
        c.outputStream.use { it.write(msg.xml.toByteArray(Charsets.UTF_8)) }
        val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        if (text.isBlank()) Result(false, errorCode = -1, errorText = "HTTP ${c.responseCode}", network = true) else parse(text)
    } catch (e: Exception) {
        Result(false, errorText = e.javaClass.simpleName + ": " + (e.message ?: ""), network = true)
    }
}
