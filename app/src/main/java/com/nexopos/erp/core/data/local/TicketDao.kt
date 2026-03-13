package com.nexopos.erp.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for outstanding tickets.
 * Provides CRUD operations for offline ticket management.
 */
@Dao
interface TicketDao {
    
    /**
     * Get all outstanding tickets, ordered by creation date (newest first)
     */
    @Query("SELECT * FROM outstanding_tickets ORDER BY createdAt DESC")
    fun getAllTickets(): Flow<List<OutstandingTicketEntity>>
    
    /**
     * Get tickets by payment status
     */
    @Query("SELECT * FROM outstanding_tickets WHERE paymentStatus = :status ORDER BY createdAt DESC")
    fun getTicketsByStatus(status: String): Flow<List<OutstandingTicketEntity>>
    
    /**
     * Get unpaid and partially paid tickets (outstanding)
     */
    @Query("SELECT * FROM outstanding_tickets WHERE paymentStatus IN ('unpaid', 'partially_paid') ORDER BY createdAt DESC")
    fun getOutstandingTickets(): Flow<List<OutstandingTicketEntity>>
    
    /**
     * Get a single ticket by ID
     */
    @Query("SELECT * FROM outstanding_tickets WHERE id = :ticketId")
    suspend fun getTicketById(ticketId: Int): OutstandingTicketEntity?
    
    /**
     * Get a single ticket by code
     */
    @Query("SELECT * FROM outstanding_tickets WHERE code = :code")
    suspend fun getTicketByCode(code: String): OutstandingTicketEntity?
    
    /**
     * Search tickets by customer name
     */
    @Query("SELECT * FROM outstanding_tickets WHERE customerName LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchTicketsByCustomer(query: String): Flow<List<OutstandingTicketEntity>>
    
    /**
     * Insert a single ticket (replaces on conflict)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTicket(ticket: OutstandingTicketEntity)
    
    /**
     * Insert multiple tickets (replaces on conflict)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTickets(tickets: List<OutstandingTicketEntity>)
    
    /**
     * Update an existing ticket
     */
    @Update
    suspend fun updateTicket(ticket: OutstandingTicketEntity)
    
    /**
     * Update ticket payment status and amounts
     */
    @Query("UPDATE outstanding_tickets SET paidAmount = :paidAmount, dueAmount = :dueAmount, paymentStatus = :status WHERE id = :ticketId")
    suspend fun updatePaymentStatus(ticketId: Int, paidAmount: Double, dueAmount: Double, status: String)
    
    /**
     * Delete a ticket by ID
     */
    @Query("DELETE FROM outstanding_tickets WHERE id = :ticketId")
    suspend fun deleteTicket(ticketId: Int)
    
    /**
     * Delete all tickets
     */
    @Query("DELETE FROM outstanding_tickets")
    suspend fun deleteAllTickets()
    
    /**
     * Get count of outstanding tickets
     */
    @Query("SELECT COUNT(*) FROM outstanding_tickets WHERE paymentStatus IN ('unpaid', 'partially_paid')")
    fun getOutstandingTicketCount(): Flow<Int>
    
    /**
     * Get total due amount for all outstanding tickets
     */
    @Query("SELECT COALESCE(SUM(dueAmount), 0.0) FROM outstanding_tickets WHERE paymentStatus IN ('unpaid', 'partially_paid')")
    fun getTotalDueAmount(): Flow<Double>
    
    /**
     * Get tickets that need sync (modified since last sync)
     */
    @Query("SELECT * FROM outstanding_tickets WHERE lastSyncedAt < :timestamp")
    suspend fun getTicketsNeedingSync(timestamp: Long): List<OutstandingTicketEntity>
}
