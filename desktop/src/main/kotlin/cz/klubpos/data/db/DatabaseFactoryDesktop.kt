package cz.klubpos.data.db

import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

/** PC: same Room database as on the tablet, encrypted file (SQLite3 Multiple Ciphers, ChaCha20-Poly1305). */
fun createDesktopDatabase(file: File, key: ByteArray): AppDatabase =
    Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
        .setDriver(EncryptedJdbcDriver(key.joinToString("") { "%02x".format(it) }))
        .setQueryCoroutineContext(Dispatchers.IO)
        .fallbackToDestructiveMigrationOnDowngrade(true)
        .build()

/** Transaction helper (PC version; on Android it is Room's withTransaction). */
suspend fun <R> AppDatabase.tx(block: suspend () -> R): R = useWriterConnection { it.immediateTransaction { block() } }

/** androidx.sqlite driver on top of JDBC (sqlite-jdbc with encryption), so Room runs on the PC. */
class EncryptedJdbcDriver(private val hexKey: String) : SQLiteDriver {
    override fun open(fileName: String): SQLiteConnection {
        val c = DriverManager.getConnection("jdbc:sqlite:$fileName")
        c.createStatement().use { st ->
            st.execute("PRAGMA key = '$hexKey'")
            st.executeQuery("SELECT count(*) FROM sqlite_master").use { it.next() }   // fails here with a wrong key
        }
        return JdbcConnection(c)
    }
}

private class JdbcConnection(val c: Connection) : SQLiteConnection {
    @Volatile var inTx = false
    override fun prepare(sql: String): SQLiteStatement = JdbcStatement(this, sql)
    override fun close() = c.close()
}

private class JdbcStatement(private val conn: JdbcConnection, private val sql: String) : SQLiteStatement {
    private val ps: PreparedStatement = conn.c.prepareStatement(sql)
    private var rs: ResultSet? = null
    private var executed = false
    private val head = sql.trimStart().take(16).uppercase()

    override fun bindBlob(index: Int, value: ByteArray) = ps.setBytes(index, value)
    override fun bindDouble(index: Int, value: Double) = ps.setDouble(index, value)
    override fun bindLong(index: Int, value: Long) = ps.setLong(index, value)
    override fun bindText(index: Int, value: String) = ps.setString(index, value)
    override fun bindNull(index: Int) = ps.setNull(index, Types.NULL)

    private fun r(): ResultSet = rs ?: error("No row")
    override fun getBlob(index: Int): ByteArray = r().getBytes(index + 1) ?: ByteArray(0)
    override fun getDouble(index: Int): Double = r().getDouble(index + 1)
    override fun getLong(index: Int): Long = r().getLong(index + 1)
    override fun getText(index: Int): String = r().getString(index + 1) ?: ""
    override fun isNull(index: Int): Boolean = r().getObject(index + 1) == null

    override fun getColumnCount(): Int = (rs?.metaData ?: ps.metaData)?.columnCount ?: 0
    override fun getColumnName(index: Int): String = (rs?.metaData ?: ps.metaData)!!.getColumnName(index + 1)
    override fun getColumnType(index: Int): Int = when (r().getObject(index + 1)) {
        null -> 5; is Long, is Int, is Short, is Byte, is Boolean -> 1; is Double, is Float -> 2; is ByteArray -> 4; else -> 3
    }

    override fun step(): Boolean {
        if (!executed) {
            executed = true
            when {
                head.startsWith("BEGIN") -> conn.inTx = true
                head.startsWith("COMMIT") || head.startsWith("END") || (head.startsWith("ROLLBACK") && !head.contains(" TO")) -> conn.inTx = false
            }
            val hasRows = ps.execute()
            rs = if (hasRows) ps.resultSet else null
        }
        return rs?.next() ?: false
    }

    override fun reset() { rs?.close(); rs = null; executed = false }
    override fun clearBindings() = ps.clearParameters()
    override fun close() { rs?.close(); ps.close() }
}
