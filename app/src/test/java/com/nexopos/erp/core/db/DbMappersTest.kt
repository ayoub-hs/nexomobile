package com.nexopos.erp.core.db

import com.nexopos.erp.core.db.entities.CategoryEntity
import com.nexopos.erp.core.db.entities.ProductEntity
import com.nexopos.erp.core.network.MobileCategory
import com.nexopos.erp.core.network.MobileProduct
import com.nexopos.erp.core.network.Product
import com.nexopos.erp.core.network.UnitDetail
import com.nexopos.erp.core.network.UnitQuantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for database mappers.
 */
class DbMappersTest {

    // ========================================================================
    // Product -> ProductEntity Tests
    // ========================================================================

    @Test
    fun `Product toEntity maps basic fields correctly`() {
        val product = Product(
            id = 1L,
            name = "Test Product",
            barcode = "123456",
            barcodeType = "ean13",
            sku = "SKU001",
            status = "available",
            unitQuantities = null
        )
        
        val now = System.currentTimeMillis()
        val entity = product.toEntity(now)
        
        assertEquals(1L, entity.id)
        assertEquals("Test Product", entity.name)
        assertEquals("123456", entity.barcode)
        assertEquals("ean13", entity.barcodeType)
        assertEquals("SKU001", entity.sku)
        assertEquals(now, entity.updatedAt)
    }

    @Test
    fun `Product toEntity with categoryId`() {
        val product = Product(
            id = 1L,
            name = "Test Product",
            barcode = null,
            barcodeType = null,
            sku = null,
            status = null,
            unitQuantities = null
        )
        
        val entity = product.toEntity(System.currentTimeMillis(), categoryId = 5L)
        assertEquals(5L, entity.categoryId)
    }

    @Test
    fun `Product toEntity serializes unitQuantities to JSON`() {
        val units = listOf(
            UnitQuantity(
                id = 1L,
                unitId = 10L,
                barcode = "UNIT123",
                salePrice = 25.0,
                wholesalePrice = 20.0,
                wholesalePriceWithTax = 22.0,
                unit = UnitDetail(10L, "piece", "piece")
            )
        )
        val product = Product(
            id = 1L,
            name = "Test",
            barcode = null,
            barcodeType = null,
            sku = null,
            status = null,
            unitQuantities = units
        )
        
        val entity = product.toEntity(System.currentTimeMillis())
        assertNotNull(entity.unitQuantitiesJson)
        // Check JSON contains the key fields - Moshi uses snake_case from @Json annotations
        assertTrue(entity.unitQuantitiesJson!!.contains("\"id\""))
        assertTrue(entity.unitQuantitiesJson!!.contains("\"sale_price\""))
    }

    // ========================================================================
    // ProductEntity -> Product Tests
    // ========================================================================

    @Test
    fun `ProductEntity toModel maps basic fields correctly`() {
        val entity = ProductEntity(
            id = 1L,
            name = "Test Product",
            barcode = "123456",
            barcodeType = "ean13",
            sku = "SKU001",
            status = "available",
            categoryId = 5L,
            unitQuantitiesJson = null,
            updatedAt = System.currentTimeMillis(),
            serverUpdatedAt = null,
            isDeleted = false
        )
        
        val product = entity.toModel()
        
        assertEquals(1L, product.id)
        assertEquals("Test Product", product.name)
        assertEquals("123456", product.barcode)
        assertEquals("ean13", product.barcodeType)
        assertEquals("SKU001", product.sku)
        assertEquals("available", product.status)
    }

    @Test
    fun `ProductEntity toModel deserializes unitQuantities from JSON`() {
        // Use proper JSON with all required fields
        val json = """[{"id":1,"unit_id":10,"barcode":"UNIT123","sale_price":25.0,"wholesale_price":20.0,"wholesale_price_edit":22.0}]"""
        val entity = ProductEntity(
            id = 1L,
            name = "Test",
            barcode = null,
            barcodeType = null,
            sku = null,
            status = null,
            categoryId = null,
            unitQuantitiesJson = json,
            updatedAt = System.currentTimeMillis(),
            serverUpdatedAt = null,
            isDeleted = false
        )
        
        val product = entity.toModel()
        assertNotNull(product.unitQuantities)
        assertEquals(1, product.unitQuantities!!.size)
        assertEquals(1L, product.unitQuantities!![0].id)
        assertEquals(25.0, product.unitQuantities!![0].salePrice!!, 0.001)
    }

    // ========================================================================
    // MobileProduct -> ProductEntity Tests
    // ========================================================================

    @Test
    fun `MobileProduct toEntity maps all fields`() {
        val mobileProduct = MobileProduct(
            id = 1L,
            name = "Mobile Product",
            barcode = "MOB123",
            barcodeType = "ean13",
            sku = "MOBSKU",
            status = "available",
            categoryId = 5L,
            unitQuantities = emptyList(),
            updatedAt = "2024-01-01 10:00:00",
            deletedAt = null
        )
        
        val now = System.currentTimeMillis()
        val entity = mobileProduct.toEntity(now)
        
        assertEquals(1L, entity.id)
        assertEquals("Mobile Product", entity.name)
        assertEquals("MOB123", entity.barcode)
        assertEquals(5L, entity.categoryId)
        assertEquals("2024-01-01 10:00:00", entity.serverUpdatedAt)
        assertFalse(entity.isDeleted)
    }

    @Test
    fun `MobileProduct toEntity marks deleted when deletedAt is set`() {
        val mobileProduct = MobileProduct(
            id = 1L,
            name = "Deleted Product",
            barcode = null,
            barcodeType = null,
            sku = null,
            status = null,
            categoryId = null,
            unitQuantities = emptyList(),
            updatedAt = "2024-01-01 10:00:00",
            deletedAt = "2024-01-02 10:00:00"
        )
        
        val entity = mobileProduct.toEntity(System.currentTimeMillis())
        assertTrue(entity.isDeleted)
    }

    // ========================================================================
    // MobileCategory Tests
    // ========================================================================

    @Test
    fun `MobileCategory toEntity maps correctly`() {
        val category = MobileCategory(
            id = 1L,
            name = "Test Category",
            description = "A test category",
            productsCount = 10,
            displayOrder = 2
        )
        
        val now = System.currentTimeMillis()
        val entity = category.toEntity(now)
        
        assertEquals(1L, entity.id)
        assertEquals("Test Category", entity.name)
        assertEquals("A test category", entity.description)
        assertEquals(10, entity.productsCount)
        assertEquals(2, entity.displayOrder)
        assertEquals(now, entity.updatedAt)
    }

    @Test
    fun `CategoryEntity toMobileCategory maps correctly`() {
        val entity = CategoryEntity(
            id = 1L,
            name = "Test Category",
            description = "A test category",
            productsCount = 10,
            displayOrder = 2,
            updatedAt = System.currentTimeMillis()
        )
        
        val category = entity.toMobileCategory()
        
        assertEquals(1L, category.id)
        assertEquals("Test Category", category.name)
        assertEquals("A test category", category.description)
        assertEquals(10, category.productsCount)
        assertEquals(2, category.displayOrder)
    }

    // ========================================================================
    // Round-trip Tests
    // ========================================================================

    @Test
    fun `Product survives round-trip through entity`() {
        val original = Product(
            id = 1L,
            name = "Round Trip Product",
            barcode = "RT123",
            barcodeType = "ean13",
            sku = "RTSKU",
            status = "available",
            unitQuantities = listOf(
                UnitQuantity(
                    id = 1L,
                    unitId = 10L,
                    barcode = "UNIT123",
                    salePrice = 25.0,
                    wholesalePrice = 20.0,
                    wholesalePriceWithTax = 22.0,
                    unit = UnitDetail(10L, "piece", "piece")
                )
            )
        )
        
        val entity = original.toEntity(System.currentTimeMillis())
        val restored = entity.toModel()
        
        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.barcode, restored.barcode)
        assertEquals(original.sku, restored.sku)
        assertNotNull(restored.unitQuantities)
        assertEquals(1, restored.unitQuantities!!.size)
        assertEquals(25.0, restored.unitQuantities!![0].salePrice!!, 0.001)
    }
}
