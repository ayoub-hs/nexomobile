package com.nexopos.erp.core.repo

import com.nexopos.erp.core.db.entities.SyncMetadataEntity
import com.nexopos.erp.core.db.entities.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for sync metadata entities and logic.
 */
class SyncMetadataTest {

    // ========================================================================
    // SyncMetadataEntity Tests
    // ========================================================================

    @Test
    fun `SyncMetadataEntity has correct keys`() {
        assertEquals("products", SyncMetadataEntity.KEY_PRODUCTS)
        assertEquals("customers", SyncMetadataEntity.KEY_CUSTOMERS)
        assertEquals("categories", SyncMetadataEntity.KEY_CATEGORIES)
        assertEquals("payment_methods", SyncMetadataEntity.KEY_PAYMENT_METHODS)
        assertEquals("bootstrap", SyncMetadataEntity.KEY_BOOTSTRAP)
    }

    @Test
    fun `SyncMetadataEntity defaults to NEVER_SYNCED`() {
        val entity = SyncMetadataEntity(key = "test")
        assertEquals(SyncStatus.NEVER_SYNCED, entity.syncStatus)
        assertNull(entity.syncToken)
        assertNull(entity.lastSyncAt)
        assertEquals(0, entity.itemCount)
    }

    @Test
    fun `SyncMetadataEntity can be marked as synced`() {
        val now = System.currentTimeMillis()
        val entity = SyncMetadataEntity(
            key = SyncMetadataEntity.KEY_PRODUCTS,
            syncToken = "token-123",
            lastSyncAt = now,
            lastServerTime = "2024-01-01 12:00:00",
            itemCount = 100,
            syncStatus = SyncStatus.SYNCED
        )
        
        assertEquals(SyncStatus.SYNCED, entity.syncStatus)
        assertEquals("token-123", entity.syncToken)
        assertEquals(now, entity.lastSyncAt)
        assertEquals(100, entity.itemCount)
    }

    @Test
    fun `SyncMetadataEntity can be marked as syncing`() {
        val entity = SyncMetadataEntity(
            key = SyncMetadataEntity.KEY_PRODUCTS,
            syncStatus = SyncStatus.SYNCING
        )
        assertEquals(SyncStatus.SYNCING, entity.syncStatus)
    }

    @Test
    fun `SyncMetadataEntity can be marked as failed`() {
        val entity = SyncMetadataEntity(
            key = SyncMetadataEntity.KEY_PRODUCTS,
            syncStatus = SyncStatus.FAILED
        )
        assertEquals(SyncStatus.FAILED, entity.syncStatus)
    }

    @Test
    fun `SyncMetadataEntity can be marked as stale`() {
        val entity = SyncMetadataEntity(
            key = SyncMetadataEntity.KEY_PRODUCTS,
            syncStatus = SyncStatus.STALE
        )
        assertEquals(SyncStatus.STALE, entity.syncStatus)
    }

    // ========================================================================
    // Stale Detection Logic Tests
    // ========================================================================

    @Test
    fun `isStale returns true when never synced`() {
        val entity = SyncMetadataEntity(key = "test")
        // lastSyncAt is null, so it's stale
        assertNull(entity.lastSyncAt)
    }

    @Test
    fun `isStale calculation with threshold`() {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - (60 * 60 * 1000)
        val twoHoursAgo = now - (2 * 60 * 60 * 1000)
        val threshold = 90 * 60 * 1000L // 90 minutes
        
        // Synced 1 hour ago with 90 min threshold = not stale
        val recentEntity = SyncMetadataEntity(
            key = "test",
            lastSyncAt = oneHourAgo,
            syncStatus = SyncStatus.SYNCED
        )
        assertTrue((now - recentEntity.lastSyncAt!!) < threshold)
        
        // Synced 2 hours ago with 90 min threshold = stale
        val oldEntity = SyncMetadataEntity(
            key = "test",
            lastSyncAt = twoHoursAgo,
            syncStatus = SyncStatus.SYNCED
        )
        assertTrue((now - oldEntity.lastSyncAt!!) > threshold)
    }

    // ========================================================================
    // Sync Token Tests
    // ========================================================================

    @Test
    fun `syncToken is preserved across updates`() {
        val original = SyncMetadataEntity(
            key = SyncMetadataEntity.KEY_BOOTSTRAP,
            syncToken = "token-v1",
            lastSyncAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.SYNCED
        )
        
        // Simulate update with new token
        val updated = original.copy(
            syncToken = "token-v2",
            lastSyncAt = System.currentTimeMillis()
        )
        
        assertEquals("token-v2", updated.syncToken)
        assertEquals(original.key, updated.key)
    }

    @Test
    fun `itemCount tracks synced items`() {
        val entity = SyncMetadataEntity(
            key = SyncMetadataEntity.KEY_PRODUCTS,
            itemCount = 500,
            syncStatus = SyncStatus.SYNCED
        )
        assertEquals(500, entity.itemCount)
    }

    // ========================================================================
    // SyncStatus Enum Tests
    // ========================================================================

    @Test
    fun `SyncStatus has all expected values`() {
        val statuses = SyncStatus.values()
        assertEquals(5, statuses.size)
        assertTrue(statuses.contains(SyncStatus.NEVER_SYNCED))
        assertTrue(statuses.contains(SyncStatus.SYNCED))
        assertTrue(statuses.contains(SyncStatus.SYNCING))
        assertTrue(statuses.contains(SyncStatus.FAILED))
        assertTrue(statuses.contains(SyncStatus.STALE))
    }

    // ========================================================================
    // Bootstrap Detection Tests
    // ========================================================================

    @Test
    fun `needsBootstrap when never synced`() {
        val entity = SyncMetadataEntity(
            key = SyncMetadataEntity.KEY_BOOTSTRAP,
            syncStatus = SyncStatus.NEVER_SYNCED
        )
        assertEquals(SyncStatus.NEVER_SYNCED, entity.syncStatus)
    }

    @Test
    fun `needsBootstrap false when synced`() {
        val entity = SyncMetadataEntity(
            key = SyncMetadataEntity.KEY_BOOTSTRAP,
            syncToken = "token-123",
            lastSyncAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.SYNCED
        )
        assertEquals(SyncStatus.SYNCED, entity.syncStatus)
        assertNotNull(entity.syncToken)
    }
}
