package cz.klubpos.desktop

import cz.klubpos.core.crypto.ImageStore
import cz.klubpos.data.db.createDesktopDatabase
import cz.klubpos.data.repo.ClubRepository
import cz.klubpos.domain.BusinessDay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The PC database: the same Room schema works on the encrypted JDBC driver; the file is unreadable without the key. */
class DesktopDbTest {
    @Test fun register_flow_on_encrypted_database() = runBlocking {
        val dir = Files.createTempDirectory("zk").toFile()
        val file = File(dir, "klubpos.db")
        val key = ByteArray(32) { it.toByte() }
        val db = createDesktopDatabase(file, key)
        val repo = ClubRepository(db, ImageStore())
        val id = repo.addMember("Jan Test", "Honza")
        repo.chargeCart(id, listOf(ClubRepository.CartItem(null, "Pivo", 55, 2.0)))
        var st = repo.memberStatuses(BusinessDay.startMillis()).first().first { it.memberId == id }
        assertEquals(110, st.balance); assertEquals(110, st.todayUnpaid); assertTrue(st.inClub)
        repo.pay(id, 110, closeVisitAfter = true)
        st = repo.memberStatuses(BusinessDay.startMillis()).first().first { it.memberId == id }
        assertEquals(0, st.balance); assertFalse(st.inClub); assertTrue(st.paidToday)
        // tables work too
        val t = repo.tableMember(5)
        assertTrue(repo.memberStatuses(BusinessDay.startMillis()).first().first { it.memberId == t }.isTable)
        db.close()

        val header = file.readBytes().copyOf(16).decodeToString()
        assertFalse("database file must be encrypted", header.startsWith("SQLite format 3"))
        // wrong key cannot open it
        val wrong = runCatching {
            val d2 = createDesktopDatabase(file, ByteArray(32) { 7 })
            ClubRepository(d2, ImageStore()).memberStatuses(BusinessDay.startMillis()).first()
        }
        assertTrue(wrong.isFailure)
    }
}
