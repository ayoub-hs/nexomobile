package com.nexopos.erp.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for mobile API models.
 */
class MobileApiModelsTest {

    // ========================================================================
    // DeltaCollection Tests
    // ========================================================================

    @Test
    fun `DeltaCollection isEmpty returns true when all lists are empty`() {
        val delta = DeltaCollection<MobileProduct>()
        assertTrue(delta.isEmpty)
    }

    @Test
    fun `DeltaCollection isEmpty returns false when created has items`() {
        val delta = DeltaCollection(
            created = listOf(createTestMobileProduct(1L)),
            updated = emptyList(),
            deletedIds = emptyList()
        )
        assertFalse(delta.isEmpty)
    }

    @Test
    fun `DeltaCollection isEmpty returns false when updated has items`() {
        val delta = DeltaCollection(
            created = emptyList<MobileProduct>(),
            updated = listOf(createTestMobileProduct(1L)),
            deletedIds = emptyList()
        )
        assertFalse(delta.isEmpty)
    }

    @Test
    fun `DeltaCollection isEmpty returns false when deletedIds has items`() {
        val delta = DeltaCollection<MobileProduct>(
            created = emptyList(),
            updated = emptyList(),
            deletedIds = listOf(1L, 2L)
        )
        assertFalse(delta.isEmpty)
    }

    @Test
    fun `DeltaCollection totalChanges sums all collections`() {
        val delta = DeltaCollection(
            created = listOf(createTestMobileProduct(1L), createTestMobileProduct(2L)),
            updated = listOf(createTestMobileProduct(3L)),
            deletedIds = listOf(4L, 5L, 6L)
        )
        assertEquals(6, delta.totalChanges)
    }

    // ========================================================================
    // MobileProduct Tests
    // ========================================================================

    @Test
    fun `MobileProduct with deletedAt is considered deleted`() {
        val product = createTestMobileProduct(1L).copy(deletedAt = "2024-01-01 10:00:00")
        assertEquals("2024-01-01 10:00:00", product.deletedAt)
    }

    @Test
    fun `MobileProduct without deletedAt is not deleted`() {
        val product = createTestMobileProduct(1L)
        assertNull(product.deletedAt)
    }

    @Test
    fun `MobileProduct has correct unitQuantities`() {
        val units = listOf(
            UnitQuantity(
                id = 1L,
                unitId = 10L,
                barcode = "123456",
                salePrice = 25.0,
                wholesalePrice = 20.0,
                wholesalePriceWithTax = 22.0,
                unit = UnitDetail(id = 10L, name = "piece", identifier = "piece")
            )
        )
        val product = createTestMobileProduct(1L).copy(unitQuantities = units)
        assertEquals(1, product.unitQuantities.size)
        assertEquals(25.0, product.unitQuantities[0].salePrice!!, 0.001)
    }

    // ========================================================================
    // MobileCategory Tests
    // ========================================================================

    @Test
    fun `MobileCategory has correct fields`() {
        val category = MobileCategory(
            id = 1L,
            name = "Test Category",
            description = "A test category",
            productsCount = 10,
            displayOrder = 1
        )
        assertEquals(1L, category.id)
        assertEquals("Test Category", category.name)
        assertEquals(10, category.productsCount)
    }

    // ========================================================================
    // BatchOrderResponse Tests
    // ========================================================================

    @Test
    fun `BatchOrderResponse tracks success and failure counts`() {
        val response = BatchOrderResponse(
            results = listOf(
                BatchOrderResult(
                    clientReference = "ref-1",
                    success = true,
                    order = OrderSummary(
                        id = 100L,
                        code = "ORD-100",
                        total = 150.0,
                        totalWithoutTax = 140.0,
                        totalWithTax = 150.0,
                        totalCoupons = 0.0,
                        taxValue = 10.0,
                        paymentStatus = "paid",
                        customer = null
                    ),
                    error = null
                ),
                BatchOrderResult(
                    clientReference = "ref-2",
                    success = false,
                    order = null,
                    error = "Product not found"
                )
            ),
            successCount = 1,
            failureCount = 1
        )
        assertEquals(2, response.results.size)
        assertEquals(1, response.successCount)
        assertEquals(1, response.failureCount)
        assertTrue(response.results[0].success)
        assertFalse(response.results[1].success)
    }

    // ========================================================================
    // SyncStatusResponse Tests
    // ========================================================================

    @Test
    fun `SyncStatusResponse indicates what needs syncing`() {
        val status = SyncStatusResponse(
            productsUpdated = true,
            customersUpdated = false,
            categoriesUpdated = true,
            lastProductUpdate = "2024-01-01 10:00:00",
            lastCustomerUpdate = null,
            serverTime = "2024-01-01 12:00:00"
        )
        assertTrue(status.productsUpdated)
        assertFalse(status.customersUpdated)
        assertTrue(status.categoriesUpdated)
    }

    // ========================================================================
    // BootstrapSyncResponse Tests
    // ========================================================================

    @Test
    fun `BootstrapSyncResponse contains all data types`() {
        val response = BootstrapSyncResponse(
            categories = listOf(
                MobileCategory(1L, "Cat 1", null, 5, 0)
            ),
            products = listOf(
                createTestMobileProduct(1L),
                createTestMobileProduct(2L)
            ),
            customers = listOf(
                Customer(1L, "user1", "John Doe", "John", "Doe", "john@test.com", "123456", null, false)
            ),
            paymentMethods = listOf(
                PaymentMethod("cash", "Cash", true, false)
            ),
            orderTypes = listOf(
                OrderType("takeaway", "Takeaway", null, true)
            ),
            syncToken = "token-123",
            serverTime = "2024-01-01 12:00:00"
        )
        
        assertEquals(1, response.categories.size)
        assertEquals(2, response.products.size)
        assertEquals(1, response.customers.size)
        assertEquals(1, response.paymentMethods.size)
        assertEquals(1, response.orderTypes.size)
        assertEquals("token-123", response.syncToken)
    }

    // ========================================================================
    // Helper Functions
    // ========================================================================

    private fun createTestMobileProduct(id: Long): MobileProduct {
        return MobileProduct(
            id = id,
            name = "Product $id",
            barcode = "BAR$id",
            barcodeType = "ean13",
            sku = "SKU$id",
            status = "available",
            categoryId = 1L,
            unitQuantities = emptyList(),
            updatedAt = "2024-01-01 10:00:00",
            deletedAt = null
        )
    }
}
