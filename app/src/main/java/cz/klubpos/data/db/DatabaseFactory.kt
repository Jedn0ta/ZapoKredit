package cz.klubpos.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import cz.klubpos.core.crypto.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Android: schema migrations of the tablet database (the PC starts with a fresh database). */
object Migrations {

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

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE members ADD COLUMN tileColor INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE members ADD COLUMN elder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE members ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE members ADD COLUMN email TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE members ADD COLUMN avatar TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN promoPrice INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE products ADD COLUMN promoDays INTEGER NOT NULL DEFAULT 127")
                db.execSQL("ALTER TABLE products ADD COLUMN promoFrom INTEGER NOT NULL DEFAULT 1020")
                db.execSQL("ALTER TABLE products ADD COLUMN promoTo INTEGER NOT NULL DEFAULT 1200")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN salePrice INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE products ADD COLUMN saleFrom INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE products ADD COLUMN saleTo INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE products ADD COLUMN saleLeft REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE products ADD COLUMN dealBuy INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN dealFree INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE members ADD COLUMN jedi INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN kind TEXT NOT NULL DEFAULT 'OTHER'")
                db.execSQL("ALTER TABLE products ADD COLUMN bottleMl INTEGER NOT NULL DEFAULT 0")
                // existing draught items
                db.execSQL("UPDATE products SET kind = 'DRAUGHT' WHERE kegGroup IS NOT NULL")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN menuName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE products ADD COLUMN servingName TEXT DEFAULT NULL")
                db.execSQL("UPDATE products SET menuName = COALESCE(kegGroup, name)")
                db.execSQL("UPDATE products SET kind = 'WINE' WHERE kind = 'POURED_ALCO'")
                db.execSQL("UPDATE products SET kind = 'SOFT_TAP' WHERE kind = 'POURED_SOFT'")
            }
        }

        /** New stock: tracked products become stock items linked back to their servings. */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE members ADD COLUMN isTable INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `prep_orders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `memberId` INTEGER, `target` TEXT NOT NULL, `isTable` INTEGER NOT NULL, `itemsJson` TEXT NOT NULL,
                    `note` TEXT, `waiter` TEXT, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)""")
            }
        }
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `eet_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `paymentEntryId` INTEGER, `memberId` INTEGER, `amount` INTEGER NOT NULL, `datTrzby` TEXT NOT NULL,
                    `poradCis` TEXT NOT NULL, `status` TEXT NOT NULL, `pok` TEXT, `error` TEXT, `warnings` TEXT,
                    `attempts` INTEGER NOT NULL, `lastTry` INTEGER, `createdAt` INTEGER NOT NULL,
                    `playground` INTEGER NOT NULL, `verification` INTEGER NOT NULL)""")
            }
        }
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stock_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`unitType` TEXT NOT NULL, `unitLabel` TEXT NOT NULL, `containerMl` INTEGER NOT NULL, `qty` REAL NOT NULL, " +
                        "`target` REAL NOT NULL, `location` TEXT NOT NULL, `supplierId` INTEGER, `active` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, `legacyProductId` INTEGER)"
                )
                db.execSQL("ALTER TABLE products ADD COLUMN stockItemId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN stockItemId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN supplierId INTEGER DEFAULT NULL")
                db.execSQL(
                    "INSERT INTO stock_items (name, unitType, unitLabel, containerMl, qty, target, location, supplierId, active, sortOrder, legacyProductId) " +
                        "SELECT COALESCE(menuName, name), CASE WHEN bottleMl > 0 THEN 'BOTTLE' ELSE 'PIECE' END, " +
                        "CASE WHEN bottleMl > 0 THEN 'lahev' ELSE unit END, bottleMl, stockQty, " +
                        "CASE WHEN reorderQty > 0 THEN minStock + reorderQty ELSE minStock * 2 END, 'Sklad', NULL, 1, 0, id " +
                        "FROM products WHERE trackStock = 1 AND active = 1"
                )
                db.execSQL("UPDATE products SET stockItemId = (SELECT s.id FROM stock_items s WHERE s.legacyProductId = products.id), trackStock = 0 " +
                    "WHERE trackStock = 1 AND active = 1")
            }
        }
}

/** Transaction helper (Android: Room's withTransaction). Same function exists for the PC build. */
suspend fun <R> AppDatabase.tx(block: suspend () -> R): R = withTransaction { block() }

/** Whole DB file is encrypted with SQLCipher (AES-256); key comes from the Android Keystore. */
fun createDatabase(context: Context): AppDatabase {
    System.loadLibrary("sqlcipher")
    val factory = SupportOpenHelperFactory(DatabaseKeyManager.getOrCreatePassphrase(context))
    return Room.databaseBuilder(context, AppDatabase::class.java, "klubpos.db")
        .openHelperFactory(factory)
        .addMigrations(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3, Migrations.MIGRATION_3_4, Migrations.MIGRATION_4_5, Migrations.MIGRATION_5_6, Migrations.MIGRATION_6_7, Migrations.MIGRATION_7_8, Migrations.MIGRATION_8_9, Migrations.MIGRATION_9_10, Migrations.MIGRATION_10_11, Migrations.MIGRATION_11_12, Migrations.MIGRATION_12_13, Migrations.MIGRATION_13_14, Migrations.MIGRATION_14_15, Migrations.MIGRATION_15_16)
        // Before first production release replace with real migrations.
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()
}
