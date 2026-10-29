package cz.klubpos.domain

import java.math.BigInteger
import java.text.Normalizer

/**
 * Czech "QR Platba" (SPAYD 1.0) – scanned by every Czech banking app.
 * Example: SPD*1.0*ACC:CZ6508000000192000145399*AM:345.00*CC:CZK*X-VS:12*MSG:KLUB HONZA
 */
object QrPayment {

    /** Accepts IBAN (CZ65 0800 …) or Czech domestic format [prefix-]number/bank. Returns IBAN or null. */
    fun toIban(input: String): String? {
        val s = input.replace(" ", "").uppercase()
        if (s.isEmpty()) return null
        if (s.matches(Regex("[A-Z]{2}\\d{2}[A-Z0-9]{10,30}"))) return s.takeIf { ibanValid(it) }
        val m = Regex("(?:(\\d{1,6})-)?(\\d{2,10})/(\\d{4})").matchEntire(s) ?: return null
        val prefix = (m.groupValues[1].ifEmpty { "0" }).padStart(6, '0')
        val number = m.groupValues[2].padStart(10, '0')
        val bban = m.groupValues[3] + prefix + number
        val check = 98 - mod97(bban + "CZ00")
        return "CZ" + check.toString().padStart(2, '0') + bban
    }

    private fun mod97(alnum: String): Int {
        val digits = alnum.map { c -> if (c.isLetter()) (c - 'A' + 10).toString() else c.toString() }.joinToString("")
        return BigInteger(digits).mod(BigInteger.valueOf(97)).toInt()
    }

    private fun ibanValid(iban: String) = mod97(iban.drop(4) + iban.take(4)) == 1

    fun spayd(iban: String, amount: Long, message: String, variableSymbol: Long? = null): String {
        val msg = Normalizer.normalize(message, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
            .uppercase().replace("*", "").take(60)
        return buildString {
            append("SPD*1.0*ACC:").append(iban)
            append("*AM:").append(amount).append(".00")
            append("*CC:CZK")
            if (variableSymbol != null) append("*X-VS:").append(variableSymbol)
            if (msg.isNotBlank()) append("*MSG:").append(msg)
        }
    }
}
