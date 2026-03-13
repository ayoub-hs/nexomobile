package com.nexopos.desktop.core.db

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

/**
 * Database tables matching Android Room entities
 * TASK_HIGH_001: Added composite indexes for query optimization
 */
object Products : LongIdTable("products") {
    val name = varchar("name", 255).index()
    val barcode = varchar("barcode", 255).nullable().index()
    val barcodeType = varchar("barcode_type", 50).nullable()
    val sku = varchar("sku", 255).nullable().index()
    val status = varchar("status", 50).nullable()
    val categoryId = long("category_id").nullable().index()
    val unitQuantitiesJson = text("unit_quantities_json").nullable()
    val updatedAt = long("updated_at")
    val serverUpdatedAt = varchar("server_updated_at", 255).nullable()
    val isDeleted = bool("is_deleted").default(false).index()
    
    // Composite index for category product queries (WHERE category_id ORDER BY name)
    init {
        index(isUnique = false, categoryId, name)
    }
}

object Customers : LongIdTable("customers") {
    val username = varchar("username", 255).nullable()
    val name = varchar("name", 255).nullable()
    val firstName = varchar("first_name", 255).nullable().index()
    val lastName = varchar("last_name", 255).nullable().index()
    val email = varchar("email", 255).nullable()
    val phone = varchar("phone", 100).nullable()
    val groupId = long("group_id").nullable()
    val groupName = varchar("group_name", 255).nullable()
    val isDefault = bool("is_default").nullable()
    val updatedAt = long("updated_at")
}

object PaymentMethods : Table("payment_methods") {
    val identifier = varchar("identifier", 100)
    val label = varchar("label", 255).nullable()
    val selected = bool("selected").nullable()
    val readonly = bool("is_readonly").nullable()
    val updatedAt = long("updated_at")
    
    override val primaryKey = PrimaryKey(identifier)
}

object Categories : LongIdTable("categories") {
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val productsCount = integer("products_count").default(0)
    val displayOrder = integer("display_order").default(0)
    val updatedAt = long("updated_at")
}

object OrderTypes : Table("order_types") {
    val identifier = varchar("identifier", 100)
    val label = varchar("label", 255).nullable()
    val icon = varchar("icon", 100).nullable()
    val selected = bool("selected").nullable()
    val updatedAt = long("updated_at")
    
    override val primaryKey = PrimaryKey(identifier)
}

object QueuedOrders : LongIdTable("queued_orders") {
    val orderJson = text("order_json")
    val status = varchar("status", 50).index()
    val errorMessage = text("error_message").nullable()
    val serverId = long("server_id").nullable()
    val serverCode = varchar("server_code", 100).nullable()
    val clientReference = varchar("client_reference", 255)
    val paymentStatus = varchar("payment_status", 50).nullable()
    val isFromServer = bool("is_from_server").default(false)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    
    // Composite indexes for performance optimization
    init {
        index(isUnique = false, status, createdAt)
        index(isUnique = false, serverId)
        // Performance indexes for main queries
        index(isUnique = false, createdAt)  // For ORDER BY created_at DESC
        index(isUnique = false, isFromServer, createdAt)  // For filtered queries
        index(isUnique = false, status, isFromServer, createdAt)  // For complex filters
    }
}

/**
 * Database singleton (matching Android Room)
 * TASK_MED_002: Added versioning and migration support
 */
object AppDatabase {
    private var isInitialized = false
    private const val DATABASE_VERSION = 4 // Fix: Ensure all columns exist
    private const val VERSION_FILE = "db_version.txt"
    
    fun init(dbPath: String = "nexopos.db") {
        if (isInitialized) return
        
        val dbDir = File(System.getProperty("user.home"), ".nexopos")
        dbDir.mkdirs()
        
        val dbFile = File(dbDir, dbPath)
        val versionFile = File(dbDir, VERSION_FILE)
        
        // Connect and set as default database
        Database.connect(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC"
        )
        
        // Get current database version
        val currentVersion = if (versionFile.exists()) {
            try {
                versionFile.readText().trim().toIntOrNull() ?: 0
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }
        
        // Run migrations if needed
        transaction {
            if (currentVersion == 0) {
                // Fresh install - create all tables
                SchemaUtils.create(
                    Products,
                    Customers,
                    PaymentMethods,
                    Categories,
                    OrderTypes,
                    QueuedOrders
                )
                println("[AppDatabase] Created all tables (fresh install)")
            } else if (currentVersion < DATABASE_VERSION) {
                // Run migrations
                println("[AppDatabase] Migrating from version $currentVersion to $DATABASE_VERSION")
                runMigrations(currentVersion, DATABASE_VERSION)
            }
        }
        
        // Save current version
        versionFile.writeText(DATABASE_VERSION.toString())
        
        isInitialized = true
        println("[AppDatabase] Initialized at version $DATABASE_VERSION")
    }
    
    /**
     * TASK_MED_002: Migration system
     */
    private fun Transaction.runMigrations(from: Int, to: Int) {
        for (version in (from + 1)..to) {
            when (version) {
                2 -> migrateToVersion2()
                3 -> migrateToVersion3()
                4 -> migrateToVersion4()
                // Add future migrations here
            }
            println("[AppDatabase] Migrated to version $version")
        }
    }
    
    /**
     * Migration to version 2: Add QueuedOrders server sync fields
     * TASK_MED_002 Pattern: Handles schema changes gracefully
     */
    private fun Transaction.migrateToVersion2() {
        try {
            // Check if table exists and needs migration
            val tableExists = exec("SELECT name FROM sqlite_master WHERE type='table' AND name='queued_orders'") { rs ->
                rs.next()
            } ?: false
            
            if (tableExists) {
                // Add new columns if they don't exist
                val columnsToAdd = listOf(
                    "ALTER TABLE queued_orders ADD COLUMN server_id INTEGER" to "server_id",
                    "ALTER TABLE queued_orders ADD COLUMN server_code VARCHAR(100)" to "server_code",
                    "ALTER TABLE queued_orders ADD COLUMN client_reference VARCHAR(255) DEFAULT 'MIGRATED'" to "client_reference",
                    "ALTER TABLE queued_orders ADD COLUMN payment_status VARCHAR(50)" to "payment_status",
                    "ALTER TABLE queued_orders ADD COLUMN is_from_server BOOLEAN DEFAULT 0" to "is_from_server"
                )
                
                columnsToAdd.forEach { (sql, columnName) ->
                    try {
                        exec(sql)
                        println("[AppDatabase] Added column: $columnName")
                    } catch (e: Exception) {
                        // Column might already exist - that's ok
                        println("[AppDatabase] Column $columnName already exists or error: ${e.message}")
                    }
                }
                
                // Create indexes
                try {
                    exec("CREATE INDEX IF NOT EXISTS queued_orders_server_id ON queued_orders(server_id)")
                    println("[AppDatabase] Created index on server_id")
                } catch (e: Exception) {
                    println("[AppDatabase] Index already exists: ${e.message}")
                }
            } else {
                // Table doesn't exist yet - create it
                SchemaUtils.create(QueuedOrders)
                println("[AppDatabase] Created queued_orders table")
            }
        } catch (e: Exception) {
            println("[AppDatabase] Migration error (non-fatal): ${e.message}")
            // Continue - worst case, we recreate the table
        }
    }
    
    /**
     * Migration to version 3: Add performance indexes
     * Optimizes ORDER BY created_at DESC and filtered queries
     */
    private fun Transaction.migrateToVersion3() {
        try {
            exec("CREATE INDEX IF NOT EXISTS queued_orders_created_at ON queued_orders(created_at)")
            exec("CREATE INDEX IF NOT EXISTS queued_orders_is_from_server_created_at ON queued_orders(is_from_server, created_at)")
            exec("CREATE INDEX IF NOT EXISTS queued_orders_status_is_from_server_created_at ON queued_orders(status, is_from_server, created_at)")
            println("[AppDatabase] Created performance indexes for queued_orders")
        } catch (e: Exception) {
            println("[AppDatabase] Index creation error (non-fatal): ${e.message}")
        }
    }
    
    /**
     * Migration to version 4: Ensure all required columns exist
     * SAFETY FIX: Some databases may have skipped v2 migration
     */
    private fun Transaction.migrateToVersion4() {
        try {
            // Verify queued_orders table exists
            val tableExists = exec("SELECT name FROM sqlite_master WHERE type='table' AND name='queued_orders'") { rs ->
                rs.next()
            } ?: false
            
            if (!tableExists) {
                SchemaUtils.create(QueuedOrders)
                println("[AppDatabase] Created queued_orders table")
                return
            }
            
            // Check which columns exist
            val existingColumns = mutableSetOf<String>()
            exec("PRAGMA table_info(queued_orders)") { rs ->
                while (rs.next()) {
                    existingColumns.add(rs.getString("name"))
                }
            }
            
            // Add missing columns
            val requiredColumns = mapOf(
                "server_id" to "ALTER TABLE queued_orders ADD COLUMN server_id INTEGER",
                "server_code" to "ALTER TABLE queued_orders ADD COLUMN server_code VARCHAR(100)",
                "client_reference" to "ALTER TABLE queued_orders ADD COLUMN client_reference VARCHAR(255) DEFAULT 'MIGRATED'",
                "payment_status" to "ALTER TABLE queued_orders ADD COLUMN payment_status VARCHAR(50)",
                "is_from_server" to "ALTER TABLE queued_orders ADD COLUMN is_from_server BOOLEAN DEFAULT 0",
                "created_at" to "ALTER TABLE queued_orders ADD COLUMN created_at INTEGER DEFAULT 0",
                "updated_at" to "ALTER TABLE queued_orders ADD COLUMN updated_at INTEGER DEFAULT 0"
            )
            
            requiredColumns.forEach { (columnName, sql) ->
                if (!existingColumns.contains(columnName)) {
                    try {
                        exec(sql)
                        println("[AppDatabase] ✓ Added missing column: $columnName")
                    } catch (e: Exception) {
                        println("[AppDatabase] ✗ Failed to add $columnName: ${e.message}")
                    }
                } else {
                    println("[AppDatabase] ✓ Column exists: $columnName")
                }
            }
            
            // Ensure indexes exist
            exec("CREATE INDEX IF NOT EXISTS queued_orders_server_id ON queued_orders(server_id)")
            exec("CREATE INDEX IF NOT EXISTS queued_orders_status ON queued_orders(status)")
            println("[AppDatabase] ✓ Ensured all indexes exist")
            
        } catch (e: Exception) {
            println("[AppDatabase] ✗ Migration v4 error: ${e.message}")
            e.printStackTrace()
        }
    }
    
    fun <T> query(block: Transaction.() -> T): T {
        // Use default database connection
        return transaction {
            block()
        }
    }
    
    /**
     * Get current database version
     */
    fun getVersion(): Int = DATABASE_VERSION
}
