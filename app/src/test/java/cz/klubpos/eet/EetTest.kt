package cz.klubpos.eet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.validation.SchemaFactory

/**
 * EET 2.0: zpráva se ověří nezávislou implementací XMLDSig z JDK (kanonizace exc-c14n, SHA-256, RSA)
 * a obsah e-tržby se zvaliduje proti oficiálnímu XSD. Certifikát si test vyrobí sám (keytool).
 */
class EetTest {
    companion object {
        lateinit var cert: Eet.Cert

        @BeforeClass @JvmStatic fun makeCert() {
            val f = File.createTempFile("eet", ".p12").apply { delete() }
            val keytool = File(System.getProperty("java.home"), "bin/keytool").path
            val p = ProcessBuilder(keytool, "-genkeypair", "-alias", "pokladna", "-keyalg", "RSA", "-keysize", "2048",
                "-sigalg", "SHA256withRSA", "-dname", "CN=CZ00000019, C=CZ", "-validity", "30",
                "-storetype", "PKCS12", "-keystore", f.path, "-storepass", "test1234", "-keypass", "test1234")
                .redirectErrorStream(true).start()
            p.inputStream.readBytes(); assertEquals(0, p.waitFor())
            cert = Eet.Cert.fromPkcs12(f.readBytes(), "test1234")
        }
    }

    private fun trzba(amount: String = "120.00") =
        Eet.Trzba("CZ00000019", 303, "POKLADNA-1", "P1/000001", "2027-01-09T16:45:36+01:00", amount)

    /**
     * Core XMLDSig validation by the JDK (java.xml.crypto). Called through reflection because Android unit tests
     * compile against android.jar, which does not contain these classes – at runtime the JDK has them.
     */
    private fun jdkValidate(doc: org.w3c.dom.Document): Boolean {
        val body = doc.getElementsByTagNameNS(Eet.NS_SOAP, "Body").item(0) as org.w3c.dom.Element
        body.setIdAttributeNS(Eet.NS_WSU, "Id", true)
        val sigNode = doc.getElementsByTagNameNS(Eet.NS_DS, "Signature").item(0)
        val vcCls = Class.forName("javax.xml.crypto.dsig.XMLValidateContext")
        val ctx = Class.forName("javax.xml.crypto.dsig.dom.DOMValidateContext")
            .getConstructor(java.security.Key::class.java, org.w3c.dom.Node::class.java).newInstance(cert.cert.publicKey, sigNode)
        val facCls = Class.forName("javax.xml.crypto.dsig.XMLSignatureFactory")
        val fac = facCls.getMethod("getInstance", String::class.java).invoke(null, "DOM")
        val sig = facCls.getMethod("unmarshalXMLSignature", vcCls).invoke(fac, ctx)
        return Class.forName("javax.xml.crypto.dsig.XMLSignature").getMethod("validate", vcCls).invoke(sig, ctx) as Boolean
    }

    private fun parse(xml: String) =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(xml.byteInputStream())

    @Test fun eic_from_certificate() = assertEquals("CZ00000019", cert.eic)

    @Test fun signature_is_valid_by_jdk_xmldsig() =
        assertTrue(jdkValidate(parse(Eet.buildSigned(trzba(), cert, prvniZaslani = true, overeni = false).xml)))

    @Test fun tampered_message_fails() {
        val m = Eet.buildSigned(trzba(), cert, true, false)
        assertFalse(jdkValidate(parse(m.xml.replace("celk_trzba=\"120.00\"", "celk_trzba=\"1.00\""))))
    }

    @Test fun trzba_matches_official_xsd() {
        val schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(javaClass.classLoader!!.getResource("EETXMLSchema.xsd"))
        for (amount in listOf("120.00", "-50.00", "0.00", "99999999.99")) {
            val m = Eet.buildSigned(trzba(amount), cert, prvniZaslani = false, overeni = true)
            val trzbaEl = parse(m.xml).getElementsByTagNameNS(Eet.NS_EET, "Trzba").item(0)
            schema.newValidator().validate(DOMSource(trzbaEl))
        }
    }

    @Test fun message_under_12kb() = assertTrue(Eet.buildSigned(trzba(), cert, true, false).xml.toByteArray().size < 12 * 1024)

    @Test fun new_uuid_each_message() =
        assertTrue(Eet.buildSigned(trzba(), cert, true, false).uuid != Eet.buildSigned(trzba(), cert, false, false).uuid)

    @Test fun formats() {
        assertEquals("120.00", Eet.amount(120))
        assertEquals("-35.50", Eet.amount(java.math.BigDecimal("-35.5")))
        // zimní a letní čas ČR
        assertEquals("2027-01-09T04:25:28+01:00", Eet.dateTime(java.time.Instant.parse("2027-01-09T03:25:28Z").toEpochMilli()))
        assertEquals("2027-06-09T05:25:28+02:00", Eet.dateTime(java.time.Instant.parse("2027-06-09T03:25:28Z").toEpochMilli()))
    }

    @Test fun validation_catches_bad_input() {
        assertTrue(trzba().validate().isEmpty())
        assertFalse(trzba().copy(eic = "12345678").validate().isEmpty())
        assertFalse(trzba().copy(idPokl = "Pokladna č. 1").validate().isEmpty())   // diakritika
        assertFalse(trzba().copy(poradCis = "").validate().isEmpty())
    }

    @Test fun parse_confirmation_with_warning() {
        val r = Eet.parse("""<eet:Odpoved><eet:Hlavicka uuid_zpravy="u" dat_prij="d"/><eet:Potvrzeni pok="987a6be5-6af5-44f3-b4fc-987654321000-ff" test="true"/><eet:Varovani kod_varov="5">Datum a cas uskutecneni trzby je vyrazne v minulosti</eet:Varovani></eet:Odpoved>""")
        assertTrue(r.ok); assertTrue(r.test); assertEquals("987a6be5-6af5-44f3-b4fc-987654321000-ff", r.pok); assertEquals(1, r.warnings.size)
    }

    @Test fun parse_verification_mode_ok() {
        val r = Eet.parse("""<eet:Odpoved><eet:Hlavicka/><eet:Chyba kod="0" test="true">Datovou zpravu evidovane trzby v overovacim modu se podarilo zpracovat</eet:Chyba></eet:Odpoved>""")
        assertFalse(r.ok); assertEquals(0, r.errorCode); assertFalse(r.retry)
    }

    @Test fun parse_temporary_error_retries() {
        assertTrue(Eet.parse("""<eet:Chyba kod="-1">Docasna technicka chyba</eet:Chyba>""").retry)
        assertFalse(Eet.parse("""<eet:Chyba kod="4">Neplatny podpis SOAP zpravy</eet:Chyba>""").retry)
    }

    @Test fun network_error_retries() =
        assertTrue(Eet.send("https://127.0.0.1:9/nic", Eet.buildSigned(trzba(), cert, true, true), 500).retry)
}
