package com.nexopos.desktop.core.repo

import com.nexopos.desktop.core.db.AppDatabase
import com.nexopos.desktop.core.db.Categories
import com.nexopos.desktop.core.network.MobileCategory
import com.nexopos.desktop.core.network.NexoApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*

/**
 * Category repository (matching Android app)
 */
class CategoryRepository(private val api: NexoApiClient) {
    
    fun getAllCategories(): Flow<List<CategoryEntity>> = flow {
        val categories = AppDatabase.query {
            Categories.selectAll()
                .orderBy(Categories.displayOrder to SortOrder.ASC, Categories.name to SortOrder.ASC)
                .map { row -> rowToEntity(row) }
        }
        emit(categories)
    }.flowOn(Dispatchers.IO)
    
    suspend fun refreshCategories(forceRefresh: Boolean = true): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // First call uses forceRefresh=true, subsequent calls use cache
            val bootstrap = api.bootstrapSync(forceRefresh = forceRefresh)
            if (bootstrap.isSuccess) {
                val categories = bootstrap.getOrNull()?.categories ?: emptyList()
                saveCategories(categories)
                println("[CategoryRepository] Saved ${categories.size} categories")
                Result.success(Unit)
            } else {
                Result.failure(bootstrap.exceptionOrNull() ?: Exception("Failed to fetch categories"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun saveCategories(categories: List<MobileCategory>) {
        AppDatabase.query {
            categories.forEach { category ->
                Categories.replace {
                    it[id] = category.id
                    it[name] = category.name
                    it[description] = category.description
                    it[productsCount] = category.productsCount ?: 0
                    it[displayOrder] = category.displayOrder
                    it[updatedAt] = System.currentTimeMillis()
                }
            }
        }
    }
    
    private fun rowToEntity(row: ResultRow): CategoryEntity {
        return CategoryEntity(
            id = row[Categories.id].value,
            name = row[Categories.name],
            description = row[Categories.description],
            productsCount = row[Categories.productsCount],
            displayOrder = row[Categories.displayOrder]
        )
    }
}

data class CategoryEntity(
    val id: Long,
    val name: String,
    val description: String?,
    val productsCount: Int,
    val displayOrder: Int
)
