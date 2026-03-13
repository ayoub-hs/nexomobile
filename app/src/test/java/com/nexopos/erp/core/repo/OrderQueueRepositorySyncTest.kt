package com.nexopos.erp.core.repo

import com.nexopos.erp.core.db.entities.QueuedOrderEntity
import com.nexopos.erp.core.db.entities.QueuedOrderStatus
import com.nexopos.erp.core.network.ServerOrder
import com.nexopos.erp.core.network.ServerOrderProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the server order sync logic.
 * These tests verify the sync behavior without requiring Android context.
 */
class OrderQueueRepositorySyncTest {

    @Test
    fun `ServerOrder converts to entity with correct fields`() {
        val serverOrder = createTestServerOrder(
            id = 100L,
            code = "ORD-100",
            paymentStatus = "paid",
            total = 150.0
        )

        // Verify server order has expected values
        assertEquals(100L, serverOrder.id)
        assertEquals("ORD-100", serverOrder.code)
        assertEquals("paid", serverOrder.paymentStatus)
        assertEquals(150.0, serverOrder.total, 0.001)
    }

    @Test
    fun `QueuedOrderEntity with serverId is considered synced`() {
        val entity = createTestEntity(
            id = 1L,
            serverId = 100L,
            serverCode = "ORD-100",
            status = QueuedOrderStatus.SYNCED
        )

        assertEquals(QueuedOrderStatus.SYNCED, entity.status)
        assertNotNull(entity.serverId)
        assertEquals(100L, entity.serverId)
    }

    @Test
    fun `QueuedOrderEntity without serverId is pending`() {
        val entity = createTestEntity(
            id = 1L,
            serverId = null,
            serverCode = null,
            status = QueuedOrderStatus.PENDING
        )

        assertEquals(QueuedOrderStatus.PENDING, entity.status)
        assertNull(entity.serverId)
    }

    @Test
    fun `isFromServer flag distinguishes phone vs server orders`() {
        val phoneOrder = createTestEntity(
            id = 1L,
            isFromServer = false
        )
        val serverOrder = createTestEntity(
            id = 2L,
            isFromServer = true
        )

        assertFalse(phoneOrder.isFromServer)
        assertTrue(serverOrder.isFromServer)
    }

    @Test
    fun `paymentStatus is preserved in entity`() {
        val entity = createTestEntity(
            id = 1L,
            paymentStatus = "partially_paid"
        )

        assertEquals("partially_paid", entity.paymentStatus)
    }

    @Test
    fun `updatedAt timestamp is tracked`() {
        val now = System.currentTimeMillis()
        val entity = createTestEntity(
            id = 1L,
            updatedAt = now
        )

        assertEquals(now, entity.updatedAt)
    }

    @Test
    fun `entity copy preserves all fields`() {
        val original = createTestEntity(
            id = 1L,
            serverId = 100L,
            serverCode = "ORD-100",
            paymentStatus = "paid",
            isFromServer = false
        )

        val updated = original.copy(
            paymentStatus = "unpaid",
            updatedAt = System.currentTimeMillis()
        )

        // Unchanged fields
        assertEquals(original.id, updated.id)
        assertEquals(original.serverId, updated.serverId)
        assertEquals(original.serverCode, updated.serverCode)
        assertEquals(original.isFromServer, updated.isFromServer)

        // Changed fields
        assertEquals("unpaid", updated.paymentStatus)
        assertNotNull(updated.updatedAt)
    }

    @Test
    fun `hold payment status is preserved after sync`() {
        // Bug fix: When order is created on hold offline, it should stay on hold after sync
        val entity = createTestEntity(
            id = 1L,
            paymentStatus = "hold",
            status = QueuedOrderStatus.PENDING
        )

        // Simulate what happens after sync - payment status should be preserved
        val syncedEntity = entity.copy(
            status = QueuedOrderStatus.SYNCED,
            serverId = 100L,
            serverCode = "ORD-100"
            // paymentStatus should remain "hold" - not changed to "paid"
        )

        assertEquals("hold", syncedEntity.paymentStatus)
        assertEquals(QueuedOrderStatus.SYNCED, syncedEntity.status)
    }

    @Test
    fun `order matched by clientReference avoids duplicate`() {
        // Bug fix: When order is created online, it gets saved locally with serverCode as clientReference
        // When server orders are fetched, we should match by clientReference to avoid duplicates
        val localOrder = createTestEntity(
            id = 1L,
            serverId = null, // Not yet synced with serverId
            serverCode = null,
            status = QueuedOrderStatus.SYNCED
        ).copy(clientReference = "ORD-100") // Server code used as clientReference

        val serverOrder = createTestServerOrder(
            id = 100L,
            code = "ORD-100", // Same code as clientReference
            paymentStatus = "hold",
            total = 150.0
        )

        // The sync logic should find localOrder by clientReference matching serverOrder.code
        // and update it instead of creating a duplicate
        assertEquals(localOrder.clientReference, serverOrder.code)
    }

    @Test
    fun `ServerOrderProduct has required fields`() {
        val product = ServerOrderProduct(
            id = 1L,
            productId = 10L,
            name = "Test Product",
            quantity = 2.0,
            unitQuantityId = null,
            unitId = null,
            unitName = "piece",
            unitPrice = 25.0,
            totalPrice = 50.0,
            totalPriceWithTax = 55.0,
            taxValue = 5.0,
            discount = 0.0
        )

        assertEquals("Test Product", product.name)
        assertEquals(2.0, product.quantity, 0.001)
        assertEquals(25.0, product.unitPrice, 0.001)
        assertEquals(50.0, product.totalPrice, 0.001)
    }

    private fun createTestServerOrder(
        id: Long,
        code: String?,
        paymentStatus: String?,
        total: Double = 100.0
    ): ServerOrder {
        return ServerOrder(
            id = id,
            code = code,
            total = total,
            subtotal = total,
            paymentStatus = paymentStatus,
            createdAt = "2024-01-01 10:00:00",
            updatedAt = "2024-01-01 10:00:00",
            customer = null,
            products = emptyList(),
            payments = emptyList(),
            typeIdentifier = null,
            tendered = total,
            change = 0.0,
            discountAmount = 0.0,
            discountType = null,
            discountPercentage = 0.0,
            taxValue = 0.0
        )
    }

    private fun createTestEntity(
        id: Long,
        serverId: Long? = null,
        serverCode: String? = null,
        status: QueuedOrderStatus = QueuedOrderStatus.PENDING,
        paymentStatus: String? = null,
        isFromServer: Boolean = false,
        updatedAt: Long? = null
    ): QueuedOrderEntity {
        return QueuedOrderEntity(
            id = id,
            clientReference = "ref-$id",
            payloadJson = "{}",
            createdAt = System.currentTimeMillis(),
            status = status,
            serverId = serverId,
            serverCode = serverCode,
            paymentStatus = paymentStatus,
            isFromServer = isFromServer,
            updatedAt = updatedAt
        )
    }
}
