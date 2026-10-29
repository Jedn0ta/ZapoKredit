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
import cz.klubpos.data.db.entity.Bartender
import cz.klubpos.data.db.entity.Keg
import cz.klubpos.data.db.entity.Supplier
import cz.klubpos.data.db.entity.KegStock
import cz.klubpos.data.db.dao.KegDao
import cz.klubpos.data.db.dao.SupplierDao
import cz.klubpos.data.db.dao.BartenderDao
import cz.klubpos.data.db.entity.Member
import cz.klubpos.data.db.entity.Product
import cz.klubpos.data.db.entity.ShoppingItem
import cz.klubpos.data.db.entity.StockMovement
import cz.klubpos.data.db.entity.Visit
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [Member::class, Product::class, Visit::class, LedgerEntry::class,
        StockMovement::class, ShoppingItem::class, Bartender::class, Keg::class, Supplier::class, KegStock::class],
    version = 8,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun members(): MemberDao
    abstract fun visits(): VisitDao
    abstract fun ledger(): LedgerDao
    abstract fun products(): ProductDao
    abstract fun stock(): StockDao
    abstract fun shopping(): ShoppingDao
    abstract fun bartenders(): BartenderDao
    abstract fun kegs(): KegDao
    abstract fun suppliers(): SupplierDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ledger ADD COLUMN method TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ledger ADD COLUMN bartenderId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bartenders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `pinHash` TEXT NOT NULL, `pinSalt` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL, " +
                        "`active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN kegGroup TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE products ADD COLUMN pourMl INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `kegs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kegGroup` TEXT NOT NULL, " +
                        "`sizeLiters` REAL NOT NULL, `tappedAt` INTEGER NOT NULL, `finishedAt` INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_kegs_kegGroup` ON `kegs` (`kegGroup`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `suppliers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`phone` TEXT NOT NULL, `email` TEXT NOT NULL, `note` TEXT NOT NULL)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `keg_stock` (`kegGroup` TEXT NOT NULL, `count` INTEGER NOT NULL, " +
                        "`minCount` INTEGER NOT NULL, `sizeLiters` REAL NOT NULL, PRIMARY KEY(`kegGroup`))"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE members ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE members ADD COLUMN vip INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE members ADD COLUMN page INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Whole DB file is encrypted with SQLCipher (AES-256); key comes from the Android Keystore. */
        fun create(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(DatabaseKeyManager.getOrCreatePassphrase(context))
            return Room.databaseBuilder(context, AppDatabase::class.java, "klubpos.db")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                // Before first production release replace with real migrations.
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }
    }
}
