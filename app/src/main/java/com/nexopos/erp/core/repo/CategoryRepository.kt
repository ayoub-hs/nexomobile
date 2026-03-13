package com.nexopos.erp.core.repo

import com.nexopos.erp.core.db.dao.CategoryDao
import com.nexopos.erp.core.network.CategoryResponse
import com.nexopos.erp.core.network.MobileApi

/**
 * Repository for category data operations.
 *
 * Uses the synced Room cache as the primary source so POS tabs can be built
 * from the same category dataset used by offline bootstrap/delta sync.
 */
class CategoryRepository(
    private val api: MobileApi,
    private val categoryDao: CategoryDao
) {
    /**
     * Fetch all categories using a cache-first strategy.
     */
    suspend fun getCategories(): List<CategoryResponse> {
        val cached = categoryDao.getAll()
        if (cached.isNotEmpty()) {
            return cached.map { category ->
                CategoryResponse(
                    id = category.id,
                    name = category.name,
                    slug = ""
                )
            }
        }

        return api.getCategories()
    }
}
