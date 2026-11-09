package cz.klubpos.net

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress

class LanTest {
    @Test fun json_roundtrip() {
        val v = mapOf("a" to 1L, "b" to "Pivo \"12°\"\nřádek", "c" to listOf(1.5, true, null), "d" to mapOf("x" to -3L))
        assertEquals(v, Json.parse(Json.write(v)))
        assertEquals(listOf(1L, 2L), Json.parse(" [ 1 , 2 ] "))
        assertEquals("č", Json.parse("\"\\u010d\""))
    }

    @Test fun crypto_roundtrip_and_wrong_key() {
        val k = Lan.newKey()
        val sealed = Lan.Crypto(k).seal("tajné 🍺")
        assertEquals("tajné 🍺", Lan.Crypto(k).open(sealed))
        assertNull(Lan.Crypto(Lan.newKey()).open(sealed))
        assertNull(Lan.Crypto(k).open(sealed.dropLast(4) + "AAAA"))
    }

    @Test fun key_format_and_code() {
        val k = Lan.newKey()
        assertTrue(Lan.isValidKey(k))
        assertEquals(k, Lan.normalizeKey(Lan.formatKey(k).lowercase()))
        val c = Lan.Code("192.168.1.20", 8770, k)
        assertEquals(c, Lan.Code.parse(c.text()))
        assertNull(Lan.Code.parse("nesmysl"))
        assertEquals(8, Lan.keyId(k).length)
    }

    @Test fun dto_roundtrip() {
        val o = OrderDto(5, "Stůl 3", 9, true, listOf(OrderItemDto(1, "Pivo 0,5 l", 2.0, 55, "bez pěny")), "rychle", "Petr", "NEW", 1, 2)
        assertEquals(o, OrderDto.from(Json.parse(Json.write(o.toMap())).obj()))
        assertEquals(110, o.total)
        val m = MemberDto(1, "Jan", null, true, true, 100, 50, 150, false, false, -1, 0)
        assertEquals(m, MemberDto.from(Json.parse(Json.write(m.toMap())).obj()))
    }

    /** Client ↔ minimal server over real HTTP: right key works, wrong key is refused. */
    @Test fun client_server_over_http() {
        val key = Lan.newKey()
        val crypto = Lan.Crypto(key)
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext(Lan.PATH) { ex ->
            val plain = crypto.open(ex.requestBody.readBytes().decodeToString())
            if (plain == null) { ex.sendResponseHeaders(403, -1); ex.close(); return@createContext }
            val req = Json.parse(plain).obj()
            val res = crypto.seal(Json.write(mapOf("ok" to true, "echo" to req.str("op"), "device" to req.str("device")))).toByteArray()
            ex.sendResponseHeaders(200, res.size.toLong()); ex.responseBody.use { it.write(res) }
        }
        srv.start()
        try {
            val port = srv.address.port
            val ok = LanClient(Lan.Code("127.0.0.1", port, key), "test").call("hello")
            assertEquals("hello", ok.str("echo")); assertEquals("test", ok.str("device"))
            try { LanClient(Lan.Code("127.0.0.1", port, Lan.newKey()), "x").call("hello"); fail() }
            catch (e: LanException) { assertNotNull(e.message) }
        } finally { srv.stop(0) }
    }
}
