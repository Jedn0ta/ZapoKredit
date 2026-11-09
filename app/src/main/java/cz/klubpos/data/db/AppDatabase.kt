package cz.klubpos.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import cz.klubpos.data.db.dao.LedgerDao
import cz.klubpos.data.db.dao.MemberDao
import cz.klubpos.data.db.dao.ProductDao
import cz.klubpos.data.db.dao.ShoppingDao
import cz.klubpos.data.db.dao.StockDao
import cz.klubpos.data.db.dao.VisitDao
import cz.klubpos.data.db.entity.LedgerEntry
import cz.klubpos.data.db.entity.Bartender
import cz.klubpos.data.db.entity.Keg
import cz.klubpos.data.db.entity.Supplier
import cz.klubpos.data.db.entity.KegStock
import cz.klubpos.data.db.entity.StockItem
import cz.klubpos.data.db.dao.StockItemDao
import cz.klubpos.data.db.dao.KegDao
import cz.klubpos.data.db.dao.SupplierDao
import cz.klubpos.data.db.dao.BartenderDao
import cz.klubpos.data.db.entity.Member
import cz.klubpos.data.db.entity.Product
import cz.klubpos.data.db.entity.ShoppingItem
import cz.klubpos.data.db.entity.StockMovement
import cz.klubpos.data.db.entity.Visit

@Database(
    entities = [Member::class, Product::class, Visit::class, LedgerEntry::class,
        StockMovement::class, ShoppingItem::class, Bartender::class, Keg::class, Supplier::class, KegStock::class, StockItem::class, cz.klubpos.data.db.entity.EetRecord::class, cz.klubpos.data.db.entity.PrepOrder::class],
    version = 16,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun members(): MemberDao
    abstract fun eet(): cz.klubpos.data.db.dao.EetDao
    abstract fun prepOrders(): cz.klubpos.data.db.dao.PrepOrderDao
    abstract fun visits(): VisitDao
    abstract fun ledger(): LedgerDao
    abstract fun products(): ProductDao
    abstract fun stock(): StockDao
    abstract fun shopping(): ShoppingDao
    abstract fun bartenders(): BartenderDao
    abstract fun kegs(): KegDao
    abstract fun suppliers(): SupplierDao
    abstract fun stockItems(): StockItemDao
}
