package cz.klubpos.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import cz.klubpos.data.db.entity.LedgerEntry
import cz.klubpos.data.db.entity.Member
import cz.klubpos.data.db.entity.Product
import cz.klubpos.data.db.entity.ShoppingItem
import cz.klubpos.data.db.entity.StockMovement
import cz.klubpos.data.db.entity.Visit
import kotlinx.coroutines.flow.Flow

/** Per-member aggregates needed to compute the medallion status in one query. */
data class MemberSummaryRow(
    val id: Long,
    val name: String,
    val nickname: String?,
    val photoId: String?,
    val balance: Long,              // total debt (all time)
    val chargesToday: Long,         // charges since business-day start
    val paymentsToday: Long,        // payments since business-day start (positive number)
    val openVisitId: Long?,
    val lastActivity: Long?,
)

@Dao
interface MemberDao {
    @Insert suspend fun insert(m: Member): Long
    @Update suspend fun update(m: Member)
    @Query("SELECT * FROM members WHERE id = :id") fun observe(id: Long): Flow<Member?>
    @Query("SELECT * FROM members WHERE id = :id") suspend fun get(id: Long): Member?
    @Query("SELECT COUNT(*) FROM members") suspend fun count(): Int

    @Query(
        """
        SELECT m.id, m.name, m.nickname, m.photoId,
          COALESCE((SELECT SUM(amount) FROM ledger l WHERE l.memberId = m.id), 0) AS balance,
          COALESCE((SELECT SUM(amount) FROM ledger l WHERE l.memberId = m.id
                    AND l.type != 'PAYMENT' AND l.createdAt >= :dayStart), 0) AS chargesToday,
          COALESCE((SELECT -SUM(amount) FROM ledger l WHERE l.memberId = m.id
                    AND l.type = 'PAYMENT' AND l.createdAt >= :dayStart), 0) AS paymentsToday,
          (SELECT v.id FROM visits v WHERE v.memberId = m.id AND v.closedAt IS NULL LIMIT 1) AS openVisitId,
          (SELECT MAX(createdAt) FROM ledger l WHERE l.memberId = m.id) AS lastActivity
        FROM members m
        WHERE m.active = 1
        ORDER BY m.name COLLATE NOCASE
        """
    )
    fun observeSummaries(dayStart: Long): Flow<List<MemberSummaryRow>>
}

@Dao
interface VisitDao {
    @Insert suspend fun insert(v: Visit): Long
    @Query("SELECT * FROM visits WHERE memberId = :memberId AND closedAt IS NULL LIMIT 1")
    suspend fun openFor(memberId: Long): Visit?
    @Query("UPDATE visits SET closedAt = :at WHERE id = :id") suspend fun close(id: Long, at: Long)
    @Query("UPDATE visits SET closedAt = :at WHERE closedAt IS NULL") suspend fun closeAll(at: Long)
}

@Dao
interface LedgerDao {
    @Insert suspend fun insert(e: LedgerEntry): Long
    @Query("SELECT * FROM ledger WHERE id = :id") suspend fun get(id: Long): LedgerEntry?
    @Query("SELECT * FROM ledger WHERE memberId = :memberId ORDER BY createdAt DESC LIMIT :limit")
    fun observeForMember(memberId: Long, limit: Int = 200): Flow<List<LedgerEntry>>
    @Query("SELECT COALESCE(SUM(amount),0) FROM ledger WHERE memberId = :memberId")
    suspend fun balance(memberId: Long): Long
    @Query("SELECT COUNT(*) FROM ledger WHERE voidsEntryId = :id") suspend fun voidCount(id: Long): Int
}

@Dao
interface ProductDao {
    @Insert suspend fun insert(p: Product): Long
    @Update suspend fun update(p: Product)
    @Query("SELECT * FROM products WHERE id = :id") suspend fun get(id: Long): Product?
    @Query("SELECT * FROM products WHERE active = 1 ORDER BY category, sortOrder, name COLLATE NOCASE")
    fun observeActive(): Flow<List<Product>>
    @Query("SELECT * FROM products ORDER BY active DESC, category, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Product>>
    @Query("UPDATE products SET stockQty = stockQty + :delta WHERE id = :id")
    suspend fun adjustStock(id: Long, delta: Double)
    @Query("SELECT * FROM products WHERE active = 1 AND trackStock = 1 AND stockQty <= minStock")
    suspend fun lowStock(): List<Product>
}

@Dao
interface StockDao {
    @Insert suspend fun insert(m: StockMovement): Long
    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY createdAt DESC LIMIT 100")
    fun observeFor(productId: Long): Flow<List<StockMovement>>
}

@Dao
interface ShoppingDao {
    @Insert suspend fun insert(i: ShoppingItem): Long
    @Update suspend fun update(i: ShoppingItem)
    @Query("DELETE FROM shopping_items WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM shopping_items WHERE done = 1") suspend fun clearDone()
    @Query("SELECT * FROM shopping_items ORDER BY done, createdAt")
    fun observeAll(): Flow<List<ShoppingItem>>
    @Query("SELECT * FROM shopping_items WHERE productId = :productId AND done = 0 LIMIT 1")
    suspend fun openForProduct(productId: Long): ShoppingItem?
}
