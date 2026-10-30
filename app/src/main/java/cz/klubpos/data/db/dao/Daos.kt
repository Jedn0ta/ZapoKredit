package cz.klubpos.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import cz.klubpos.data.db.entity.LedgerEntry
import cz.klubpos.data.db.entity.Bartender
import cz.klubpos.data.db.entity.Keg
import cz.klubpos.data.db.entity.Supplier
import cz.klubpos.data.db.entity.KegStock
import cz.klubpos.data.db.entity.StockItem
import cz.klubpos.data.db.entity.Member
import cz.klubpos.data.db.entity.Product
import cz.klubpos.data.db.entity.ShoppingItem
import cz.klubpos.data.db.entity.StockMovement
import cz.klubpos.data.db.entity.Visit
import kotlinx.coroutines.flow.Flow

/** Per-member aggregates needed to compute the medallion status in one query. */
data class MemberSummaryRow(
    val id: Long,
    val sortOrder: Int,
    val vip: Boolean,
    val page: Int,
    val tileColor: Int,
    val elder: Boolean,
    val avatar: String?,
    val jedi: Int,
    val createdAt: Long,
    val name: String,
    val nickname: String?,
    val photoId: String?,
    val balance: Long,              // total debt (all time)
    val chargesToday: Long,         // charges since business-day start
    val paymentsToday: Long,        // payments since business-day start (positive number)
    val openVisitId: Long?,
    val visitedToday: Boolean,
    val lastActivity: Long?,
)

@Dao
interface MemberDao {
    @Insert suspend fun insert(m: Member): Long
    @Update suspend fun update(m: Member)
    @Query("SELECT * FROM members WHERE id = :id") fun observe(id: Long): Flow<Member?>
    @Query("SELECT * FROM members WHERE id = :id") suspend fun get(id: Long): Member?
    @Query("SELECT COUNT(*) FROM members") suspend fun count(): Int
    @Query("UPDATE members SET sortOrder = :order WHERE id = :id") suspend fun setOrder(id: Long, order: Int)
    @Query("UPDATE members SET page = :page, sortOrder = :order WHERE id = :id") suspend fun setPage(id: Long, page: Int, order: Int)
    @Query("UPDATE members SET page = :to WHERE page = :from") suspend fun movePage(from: Int, to: Int)
    @Query("UPDATE members SET page = page - 1 WHERE page > :removed") suspend fun shiftPagesAfter(removed: Int)
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM members WHERE page = :page") suspend fun maxOrder(page: Int): Int

    @Query(
        """
        SELECT m.id, m.sortOrder, m.vip, m.page, m.tileColor, m.elder, m.avatar, m.jedi, m.createdAt, m.name, m.nickname, m.photoId,
          COALESCE((SELECT SUM(amount) FROM ledger l WHERE l.memberId = m.id), 0) AS balance,
          COALESCE((SELECT SUM(amount) FROM ledger l WHERE l.memberId = m.id
                    AND l.type != 'PAYMENT' AND l.createdAt >= :dayStart), 0) AS chargesToday,
          COALESCE((SELECT -SUM(amount) FROM ledger l WHERE l.memberId = m.id
                    AND l.type = 'PAYMENT' AND l.createdAt >= :dayStart), 0) AS paymentsToday,
          (SELECT v.id FROM visits v WHERE v.memberId = m.id AND v.closedAt IS NULL LIMIT 1) AS openVisitId,
          EXISTS (SELECT 1 FROM visits v WHERE v.memberId = m.id AND (v.openedAt >= :dayStart OR v.closedAt >= :dayStart)) AS visitedToday,
          (SELECT MAX(createdAt) FROM ledger l WHERE l.memberId = m.id) AS lastActivity
        FROM members m
        WHERE m.active = 1
        ORDER BY m.sortOrder, m.name COLLATE NOCASE
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

data class ProductCount(val productId: Long, val qty: Double)

/** Day-close numbers. */
data class DaySummary(
    val charged: Long,
    val paidCash: Long,
    val paidQr: Long,
    val paidOther: Long,
    val customers: Int,
)

@Dao
interface LedgerDao {
    @Query(
        """
        SELECT productId, SUM(quantity) AS qty FROM ledger
        WHERE productId IS NOT NULL AND type = 'CHARGE' AND createdAt >= :since
        GROUP BY productId ORDER BY qty DESC LIMIT :limit
        """
    )
    fun observeTopProducts(since: Long, limit: Int): Flow<List<ProductCount>>

    /** What this member orders most (voided charges excluded). */
    @Query(
        """
        SELECT productId, SUM(quantity) AS qty FROM ledger l
        WHERE memberId = :memberId AND productId IS NOT NULL AND type = 'CHARGE' AND voidsEntryId IS NULL
          AND NOT EXISTS (SELECT 1 FROM ledger v WHERE v.voidsEntryId = l.id)
        GROUP BY productId ORDER BY qty DESC LIMIT :limit
        """
    )
    fun observeMemberTopProducts(memberId: Long, limit: Int): Flow<List<ProductCount>>

    @Query(
        """
        SELECT
          COALESCE(SUM(CASE WHEN type != 'PAYMENT' THEN amount END), 0) AS charged,
          COALESCE(-SUM(CASE WHEN type = 'PAYMENT' AND method = 'CASH' THEN amount END), 0) AS paidCash,
          COALESCE(-SUM(CASE WHEN type = 'PAYMENT' AND method = 'QR' THEN amount END), 0) AS paidQr,
          COALESCE(-SUM(CASE WHEN type = 'PAYMENT' AND (method IS NULL OR method NOT IN ('CASH','QR')) THEN amount END), 0) AS paidOther,
          COUNT(DISTINCT CASE WHEN type = 'CHARGE' THEN memberId END) AS customers
        FROM ledger WHERE createdAt >= :since
        """
    )
    suspend fun daySummary(since: Long): DaySummary

    @Query("SELECT COALESCE(SUM(b),0) FROM (SELECT SUM(amount) AS b FROM ledger GROUP BY memberId HAVING b > 0)")
    suspend fun totalOutstanding(): Long

    @Query("SELECT * FROM ledger WHERE memberId = :memberId AND type = 'CHARGE' AND voidsEntryId IS NULL ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastCharge(memberId: Long): LedgerEntry?

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
    @Query("SELECT * FROM products") suspend fun all(): List<Product>
    @Query("SELECT * FROM products WHERE active = 1 AND COALESCE(menuName, name) = :menu") suspend fun servingsOf(menu: String): List<Product>
    @Query("UPDATE products SET sortOrder = :order WHERE id = :id") suspend fun setOrder(id: Long, order: Int)
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

data class BartenderSales(val bartenderId: Long?, val charged: Long, val paid: Long, val items: Double)

@Dao
interface BartenderDao {
    @Insert suspend fun insert(b: Bartender): Long
    @Update suspend fun update(b: Bartender)
    @Query("SELECT * FROM bartenders WHERE id = :id") suspend fun get(id: Long): Bartender?
    @Query("SELECT * FROM bartenders WHERE active = 1 ORDER BY name COLLATE NOCASE") fun observeActive(): Flow<List<Bartender>>
    @Query("SELECT * FROM bartenders ORDER BY active DESC, name COLLATE NOCASE") fun observeAll(): Flow<List<Bartender>>

    @Query(
        """
        SELECT bartenderId,
          COALESCE(SUM(CASE WHEN type != 'PAYMENT' THEN amount END), 0) AS charged,
          COALESCE(-SUM(CASE WHEN type = 'PAYMENT' THEN amount END), 0) AS paid,
          COALESCE(SUM(CASE WHEN type = 'CHARGE' AND voidsEntryId IS NULL THEN quantity END), 0) AS items
        FROM ledger WHERE createdAt >= :since GROUP BY bartenderId
        """
    )
    fun observeSales(since: Long): Flow<List<BartenderSales>>
}

/** Active keg + how much has been poured from it (litres), computed from non-voided sales. */
data class KegStatus(
    val id: Long, val kegGroup: String, val sizeLiters: Double, val tappedAt: Long,
    val pouredLiters: Double, val servings: Double,
)

@Dao
interface KegDao {
    @Insert suspend fun insert(k: Keg): Long
    @Query("UPDATE kegs SET finishedAt = :at WHERE kegGroup = :group AND finishedAt IS NULL")
    suspend fun finishActive(group: String, at: Long)

    @Query(
        """
        SELECT k.id, k.kegGroup, k.sizeLiters, k.tappedAt,
          COALESCE((SELECT SUM(l.quantity * p.pourMl) / 1000.0 FROM ledger l JOIN products p ON p.id = l.productId
                    WHERE p.kegGroup = k.kegGroup AND l.type = 'CHARGE' AND l.voidsEntryId IS NULL
                      AND l.createdAt >= k.tappedAt
                      AND NOT EXISTS (SELECT 1 FROM ledger v WHERE v.voidsEntryId = l.id)), 0) AS pouredLiters,
          COALESCE((SELECT SUM(l.quantity) FROM ledger l JOIN products p ON p.id = l.productId
                    WHERE p.kegGroup = k.kegGroup AND l.type = 'CHARGE' AND l.voidsEntryId IS NULL
                      AND l.createdAt >= k.tappedAt
                      AND NOT EXISTS (SELECT 1 FROM ledger v WHERE v.voidsEntryId = l.id)), 0) AS servings
        FROM kegs k WHERE k.finishedAt IS NULL ORDER BY k.kegGroup
        """
    )
    fun observeActive(): Flow<List<KegStatus>>

    /** Keg groups defined on products (to offer "Narazit" even when no keg is active). */
    @Query("SELECT DISTINCT kegGroup FROM products WHERE kegGroup IS NOT NULL AND kegGroup != '' AND active = 1 ORDER BY kegGroup")
    fun observeGroups(): Flow<List<String>>

    @Query("SELECT * FROM keg_stock") fun observeStock(): Flow<List<KegStock>>
    @Query("SELECT * FROM keg_stock WHERE kegGroup = :group") suspend fun stock(group: String): KegStock?
    @androidx.room.Upsert suspend fun upsertStock(s: KegStock)
    @Query("DELETE FROM keg_stock WHERE kegGroup = :group") suspend fun deleteStock(group: String)
    @Query("UPDATE kegs SET kegGroup = :to WHERE kegGroup = :from") suspend fun renameKegs(from: String, to: String)
    @Query("UPDATE products SET kegGroup = NULL, pourMl = 0 WHERE kegGroup = :group") suspend fun detachProducts(group: String)

    @Query("SELECT MAX(pourMl) FROM products WHERE kegGroup = :group")
    suspend fun largestPour(group: String): Int?
}

@Dao
interface SupplierDao {
    @Insert suspend fun insert(s: Supplier): Long
    @Update suspend fun update(s: Supplier)
    @Query("DELETE FROM suppliers WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM suppliers ORDER BY name COLLATE NOCASE") fun observeAll(): Flow<List<Supplier>>
}

@Dao
interface StockItemDao {
    @Insert suspend fun insert(s: StockItem): Long
    @Update suspend fun update(s: StockItem)
    @Query("SELECT * FROM stock_items WHERE id = :id") suspend fun get(id: Long): StockItem?
    @Query("SELECT * FROM stock_items WHERE active = 1 ORDER BY location COLLATE NOCASE, sortOrder, name COLLATE NOCASE")
    fun observeActive(): Flow<List<StockItem>>
    @Query("SELECT * FROM stock_items WHERE active = 1") suspend fun allActive(): List<StockItem>
    @Query("UPDATE stock_items SET qty = qty + :delta WHERE id = :id") suspend fun adjust(id: Long, delta: Double)
    @Query("UPDATE products SET stockItemId = NULL WHERE stockItemId = :id") suspend fun unlinkAll(id: Long)
    @Query("UPDATE products SET stockItemId = :id WHERE id IN (:productIds)") suspend fun link(id: Long, productIds: List<Long>)
    @Query("SELECT * FROM products WHERE stockItemId = :id AND active = 1") suspend fun linkedProducts(id: Long): List<Product>
}
