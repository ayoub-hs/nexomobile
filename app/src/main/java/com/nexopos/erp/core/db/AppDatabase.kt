package com.nexopos.erp.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nexopos.erp.core.db.dao.CategoryDao
import com.nexopos.erp.core.db.dao.CustomerDao
import com.nexopos.erp.core.db.dao.PaymentMethodDao
import com.nexopos.erp.core.db.dao.ProductDao
import com.nexopos.erp.core.db.dao.QueuedOrderDao
import com.nexopos.erp.core.db.dao.StockAdjustmentDao
import com.nexopos.erp.core.db.dao.SyncMetadataDao
import com.nexopos.erp.core.db.entities.CategoryEntity
import com.nexopos.erp.core.db.entities.CategoryProductEntity
import com.nexopos.erp.core.db.entities.CustomerEntity
import com.nexopos.erp.core.db.entities.PaymentMethodEntity
import com.nexopos.erp.core.db.entities.ProductEntity
import com.nexopos.erp.core.db.entities.QueuedOrderEntity
import com.nexopos.erp.core.db.entities.QueuedStockAdjustmentEntity
import com.nexopos.erp.core.db.entities.SyncMetadataEntity

@Database(
    entities = [
        ProductEntity::class,
        CategoryProductEntity::class,
        CategoryEntity::class,
        CustomerEntity::class,
        QueuedOrderEntity::class,
        PaymentMethodEntity::class,
        SyncMetadataEntity::class,
        QueuedStockAdjustmentEntity::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun customerDao(): CustomerDao
    abstract fun paymentMethodDao(): PaymentMethodDao
    abstract fun queuedOrderDao(): QueuedOrderDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun stockAdjustmentDao(): StockAdjustmentDao

    companion object {
        /**
         * MIGRATION_8_9: Add queued_stock_adjustments table for offline stock adjustment support
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS queued_stock_adjustments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        product_id INTEGER NOT NULL,
                        unit_quantity_id INTEGER,
                        adjustment_type TEXT NOT NULL,
                        quantity REAL NOT NULL,
                        reason TEXT,
                        reference TEXT,
                        payload_json TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        last_attempt_at INTEGER,
                        attempt_count INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        error TEXT,
                        server_id INTEGER,
                        updated_at INTEGER
                    )
                    """.trimIndent()
                )
                
                // Create indexes for efficient queries
                database.execSQL("CREATE INDEX IF NOT EXISTS index_queued_stock_adjustments_status ON queued_stock_adjustments(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_queued_stock_adjustments_product_id ON queued_stock_adjustments(product_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_queued_stock_adjustments_created_at ON queued_stock_adjustments(created_at)")
            }
        }
        
        /**
         * MIGRATION_7_8: Remove ProductEntity foreign key to allow independent sync
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Recreate products table without foreign key constraint
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS products_new (
                        id INTEGER PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        barcode TEXT,
                        barcode_type TEXT,
                        sku TEXT,
                        status TEXT,
                        category_id INTEGER,
                        unit_quantities_json TEXT,
                        updated_at INTEGER NOT NULL,
                        server_updated_at TEXT,
                        is_deleted INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                
                // Copy existing data
                database.execSQL(
                    """
                    INSERT INTO products_new 
                    SELECT * FROM products
                    """
                )
                
                // Drop old table and rename new table
                database.execSQL("DROP TABLE products")
                database.execSQL("ALTER TABLE products_new RENAME TO products")
                
                // Recreate indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS index_products_category_id ON products(category_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_products_barcode ON products(barcode)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_products_sku ON products(sku)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_products_name ON products(name)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_products_category_name ON products(category_id, name)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_products_is_deleted ON products(is_deleted)")
            }
        }
        
        /**
         * TASK_MED_002: Add foreign key constraints
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Recreate category_products table with foreign keys
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS category_products_new (
                        category_id INTEGER NOT NULL,
                        product_id INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        PRIMARY KEY(category_id, product_id),
                        FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE CASCADE,
                        FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                
                // Copy existing data
                database.execSQL(
                    """
                    INSERT INTO category_products_new (category_id, product_id, position)
                    SELECT category_id, product_id, position FROM category_products
                    """
                )
                
                // Drop old table and rename new table
                database.execSQL("DROP TABLE category_products")
                database.execSQL("ALTER TABLE category_products_new RENAME TO category_products")
                
                // Create indexes
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_category_products_category_id " +
                    "ON category_products(category_id)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_category_products_product_id " +
                    "ON category_products(product_id)"
                )
            }
        }
        
        /**
         * TASK_HIGH_001: Add composite indexes for query optimization
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Composite index for category product queries (WHERE category_id ORDER BY name)
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_products_category_name " +
                    "ON products(category_id, name)"
                )
                
                // Composite index for queued order queries (WHERE status ORDER BY created_at)
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_queued_orders_status_created " +
                    "ON queued_orders(status, created_at DESC)"
                )
                
                // Index for customer search queries
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_customers_first_name " +
                    "ON customers(first_name)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_customers_last_name " +
                    "ON customers(last_name)"
                )
                
                // Index for product deletion status filtering
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_products_is_deleted " +
                    "ON products(is_deleted)"
                )
            }
        }
        
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS products_new (
                        id INTEGER NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        barcode TEXT,
                        barcode_type TEXT,
                        sku TEXT,
                        status TEXT,
                        category_id INTEGER,
                        unit_quantities_json TEXT,
                        updated_at INTEGER NOT NULL,
                        server_updated_at TEXT,
                        is_deleted INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "INSERT INTO products_new (id, name, barcode, barcode_type, sku, status, category_id, unit_quantities_json, updated_at, server_updated_at, is_deleted) " +
                        "SELECT id, name, barcode, barcode_type, sku, status, category_id, unit_quantities_json, updated_at, server_updated_at, is_deleted FROM products"
                )
                database.execSQL("DROP TABLE products")
                database.execSQL("ALTER TABLE products_new RENAME TO products")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_products_barcode ON products(barcode)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_products_sku ON products(sku)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_products_category_id ON products(category_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_products_name ON products(name)")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nexopos.db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
