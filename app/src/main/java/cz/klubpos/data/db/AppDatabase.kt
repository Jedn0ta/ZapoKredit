package cz.klubpos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import cz.klubpos.core.crypto.DatabaseKeyManager
import cz.klubpos.data.db.dao.LedgerDao
import cz.klubpos.data.db.dao.MemberDao
import cz.klubpos.data.db.dao.ProductDao
import cz.klubpos.data.db.dao.ShoppingDao
import cz.klubpos.data.db.dao.StockDao
import cz.klubpos.data.db.dao.VisitDao
import cz.klubpos.data.db.entity.LedgerEntry
import cz.klubpos.data.db.entity.Member
import cz.klubpos.data.db.entity.Product
import cz.klubpos.data.db.entity.ShoppingItem
import cz.klubpos.data.db.entity.StockMovement
import cz.klubpos.data.db.entity.Visit
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [Member::class, Product::class, Visit::class, LedgerEntry::class,
        StockMovement::class, ShoppingItem::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun members(): MemberDao
    abstract fun visits(): VisitDao
    abstract fun ledger(): LedgerDao
    abstract fun products(): ProductDao
    abstract fun stock(): StockDao
    abstract fun shopping(): ShoppingDao

    companion object {
        /** Whole DB file is encrypted with SQLCipher (AES-256); key comes from the Android Keystore. */
        fun create(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(DatabaseKeyManager.getOrCreatePassphrase(context))
            return Room.databaseBuilder(context, AppDatabase::class.java, "klubpos.db")
                .openHelperFactory(factory)
                // Before first production release replace with real migrations.
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }
    }
}
