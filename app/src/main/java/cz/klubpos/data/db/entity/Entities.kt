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
    /** Position on the main screen (drag & drop). */
    @androidx.room.ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    /** VIP member – shown with a gold frame. */
    @androidx.room.ColumnInfo(defaultValue = "0") val vip: Boolean = false,
    /** Which card (tab) of the main screen the member lives on. */
    @androidx.room.ColumnInfo(defaultValue = "0") val page: Int = 0,
    /** Glass tint of the tile: index into the tile palette, -1 = default. */
    @androidx.room.ColumnInfo(defaultValue = "-1") val tileColor: Int = -1,
    /** Elder of the club – shown with the Cthulhu badge. */
    @androidx.room.ColumnInfo(defaultValue = "0") val elder: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "''") val phone: String = "",
    @androidx.room.ColumnInfo(defaultValue = "''") val email: String = "",
    /** Built-in gamer avatar id (used when there is no photo). */
    @androidx.room.ColumnInfo(defaultValue = "NULL") val avatar: String? = null,
    /** Jedi mode: 0 = off, otherwise 1-based index of the blade colour. */
    @androidx.room.ColumnInfo(defaultValue = "0") val jedi: Int = 0,
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
    /** Happy hour: promo price (credits), null = no promo. */
    @androidx.room.ColumnInfo(defaultValue = "NULL") val promoPrice: Long? = null,
    /** Days bitmask: Mon=1, Tue=2, Wed=4 … Sun=64. */
    @androidx.room.ColumnInfo(defaultValue = "127") val promoDays: Int = 127,
    /** Minutes from midnight; to < from means it runs past midnight. */
    @androidx.room.ColumnInfo(defaultValue = "1020") val promoFrom: Int = 17 * 60,
    @androidx.room.ColumnInfo(defaultValue = "1200") val promoTo: Int = 20 * 60,
    /** Sale (akční cena): price for a longer period – between dates and/or until [saleLeft] pieces are sold. */
    @androidx.room.ColumnInfo(defaultValue = "NULL") val salePrice: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "NULL") val saleFrom: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "NULL") val saleTo: Long? = null,
    /** Pieces left at the sale price ("do vyprodání"); null = no piece limit. */
    @androidx.room.ColumnInfo(defaultValue = "NULL") val saleLeft: Double? = null,
    /** Deal "buy X get Y free" (e.g. 3+1). dealBuy = 0 → off. */
    @androidx.room.ColumnInfo(defaultValue = "0") val dealBuy: Int = 0,
    @androidx.room.ColumnInfo(defaultValue = "0") val dealFree: Int = 0,
    /** What it is: DRAUGHT (z sudu), POURED_ALCO, POURED_SOFT, SPIRIT, OTHER. */
    @androidx.room.ColumnInfo(defaultValue = "'OTHER'") val kind: String = "OTHER",
    /** For poured drinks / spirits: bottle size; stock is then counted in bottles. 0 = pieces. */
    @androidx.room.ColumnInfo(defaultValue = "0") val bottleMl: Int = 0,
    /** Drink on the menu card this serving belongs to (e.g. "Plzeň 12°"); several sizes share it. */
    @androidx.room.ColumnInfo(defaultValue = "NULL") val menuName: String? = null,
    /** Optional name of the serving size (e.g. "šnyt", "mlíko"). */
    @androidx.room.ColumnInfo(defaultValue = "NULL") val servingName: String? = null,
) {
    /** Stock change for selling [qty] servings (fraction of a bottle for poured drinks). */
    fun stockPerServing(): Double = if (bottleMl > 0 && pourMl > 0) pourMl.toDouble() / bottleMl else 1.0

    fun saleActive(nowMs: Long = System.currentTimeMillis()): Boolean =
        salePrice != null && (saleFrom == null || nowMs >= saleFrom) && (saleTo == null || nowMs < saleTo) &&
            (saleLeft == null || saleLeft > 0.0)

    fun dealActive() = dealBuy > 0 && dealFree > 0

    /** How many of [qty] pieces are free under the X+Y deal. */
    fun freeOf(qty: Double): Int = if (!dealActive()) 0 else (qty.toInt() / (dealBuy + dealFree)) * dealFree

    /** Is the promo running at [now]? For overnight promos the day is the day it started. */
    fun promoActive(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): Boolean {
        if (promoPrice == null) return false
        val m = now.hour * 60 + now.minute
        fun dayOn(d: java.time.DayOfWeek) = promoDays and (1 shl (d.value - 1)) != 0
        return if (promoFrom <= promoTo) m in promoFrom until promoTo && dayOn(now.dayOfWeek)
        else (m >= promoFrom && dayOn(now.dayOfWeek)) || (m < promoTo && dayOn(now.dayOfWeek.minus(1)))
    }

    /** Price charged right now (happy hour aware). */
    fun currentPrice(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): Long = when {
        promoActive(now) -> promoPrice!!
        saleActive() -> salePrice!!
        else -> price
    }
}

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

/** Full kegs waiting in the cellar, per beer. */
@Entity(tableName = "keg_stock")
data class KegStock(
    @PrimaryKey val kegGroup: String,
    val count: Int = 0,
    val minCount: Int = 1,          // at or below → keg goes on the shopping list
    val sizeLiters: Double = 50.0,  // usual keg size for this beer
)
