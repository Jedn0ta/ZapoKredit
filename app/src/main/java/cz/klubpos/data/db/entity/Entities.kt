package cz.klubpos.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** All money values are in credits (Long). 1 credit = 1 Kč. */

@Entity(tableName = "members")
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nickname: String? = null,
    val photoId: String? = null,     // id in ImageStore (encrypted file)
    val note: String? = null,
    val active: Boolean = true,      // archived members are hidden from the main grid
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "Ostatní",
    val price: Long,                 // credits
    val imageId: String? = null,
    val trackStock: Boolean = true,
    val unit: String = "ks",
    val stockQty: Double = 0.0,
    val minStock: Double = 0.0,      // below this -> goes to shopping list suggestions
    val reorderQty: Double = 0.0,    // suggested buy amount (0 = fill up to 2× minStock)
    val active: Boolean = true,
    val sortOrder: Int = 0,
    /** Draught beer: name of the keg it is tapped from (e.g. "Plzeň 12°"); several sizes can share one keg. */
    @androidx.room.ColumnInfo(defaultValue = "NULL") val kegGroup: String? = null,
    /** Millilitres per serving taken from the keg (500 = velké, 300 = malé). */
    @androidx.room.ColumnInfo(defaultValue = "0") val pourMl: Int = 0,
)

/** One stay in the club = "otevřený účet". closedAt == null means the member is in the club now. */
@Entity(
    tableName = "visits",
    foreignKeys = [ForeignKey(entity = Member::class, parentColumns = ["id"], childColumns = ["memberId"])],
    indices = [Index("memberId"), Index("closedAt")],
)
data class Visit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
)

enum class EntryType { CHARGE, PAYMENT, ADJUSTMENT }

/**
 * Immutable ledger of the member's tab.
 * amount > 0 increases debt (CHARGE), amount < 0 decreases it (PAYMENT).
 * Mistakes are fixed by a reversing entry (VOID), never by deleting rows.
 */
@Entity(
    tableName = "ledger",
    foreignKeys = [ForeignKey(entity = Member::class, parentColumns = ["id"], childColumns = ["memberId"])],
    indices = [Index("memberId"), Index("createdAt"), Index("productId")],
)
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val type: EntryType,
    val description: String,
    val productId: Long? = null,
    val quantity: Double = 1.0,
    val unitPrice: Long = 0,
    val amount: Long,
    val visitId: Long? = null,
    val voidsEntryId: Long? = null,  // set on a correcting entry
    val method: String? = null,      // payments: CASH / QR
    val bartenderId: Long? = null,   // who wrote it (null = before logins existed / no bartenders set up)
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "stock_movements",
    indices = [Index("productId"), Index("createdAt")],
)
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val delta: Double,               // +purchase / -sale / ±inventory correction
    val reason: String,              // SALE, PURCHASE, INVENTORY, VOID
    val ledgerEntryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "shopping_items", indices = [Index("productId")])
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long? = null,     // null = free-text item (e.g. "ubrousky")
    val name: String,
    val quantity: Double,
    val unit: String = "ks",
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Staff member who logs in with a PIN. PIN is stored only as a salted PBKDF2 hash. */
@Entity(tableName = "bartenders")
data class Bartender(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pinHash: String,
    val pinSalt: String,
    val colorIndex: Int = 0,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A keg on tap. Active = finishedAt == null. Poured volume is computed from sales since [tappedAt]. */
@Entity(tableName = "kegs", indices = [Index("kegGroup")])
data class Keg(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kegGroup: String,
    val sizeLiters: Double,
    val tappedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
)

/** Who to call / message when restocking. */
@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val note: String = "",      // what they supply, delivery days…
)
