package com.nexopos.shared.utils

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

/**
 * Unit tests for Currency utilities and Money class.
 * These tests verify precise financial calculations.
 */
class CurrencyTest {
    
    @Test
    fun `Double to BigDecimal preserves precision`() {
        val value = 19.99
        val bigDecimal = with(Currency) { value.toBigDecimal() }
        
        assertEquals(BigDecimal.valueOf(19.99).setScale(2), bigDecimal)
    }
    
    @Test
    fun `Money addition is precise`() {
        val price1 = Money(0.1)
        val price2 = Money(0.2)
        val total = price1 + price2
        
        // This fails with Double (0.1 + 0.2 = 0.30000000000000004)
        // But works correctly with BigDecimal
        assertEquals(0.30, total.toDouble(), 0.001)
        assertEquals("$0.30", total.format())
    }
    
    @Test
    fun `Money subtraction is precise`() {
        val price = Money(10.00)
        val discount = Money(2.50)
        val final = price - discount
        
        assertEquals(7.50, final.toDouble(), 0.001)
        assertEquals("$7.50", final.format())
    }
    
    @Test
    fun `Money multiplication by quantity`() {
        val unitPrice = Money(19.99)
        val quantity = 3
        val total = unitPrice * quantity
        
        assertEquals(59.97, total.toDouble(), 0.001)
        assertEquals("$59.97", total.format())
    }
    
    @Test
    fun `Money division`() {
        val total = Money(100.00)
        val parts = 3
        val each = total / parts
        
        // 100 / 3 = 33.33 (rounded)
        assertEquals(33.33, each.toDouble(), 0.001)
    }
    
    @Test
    fun `Tax calculation is precise`() {
        // Example: Product costs $10, tax is 19%
        val price = Money(10.00)
        val taxRate = 0.19
        val tax = price * taxRate
        
        assertEquals(1.90, tax.toDouble(), 0.001)
        assertEquals("$1.90", tax.format())
        
        val totalWithTax = price + tax
        assertEquals(11.90, totalWithTax.toDouble(), 0.001)
    }
    
    @Test
    fun `Discount percentage calculation`() {
        val price = Money(100.00)
        val discountPercent = 15.0
        val discount = Money.percentage(price, discountPercent)
        
        assertEquals(15.00, discount.toDouble(), 0.001)
        
        val finalPrice = price - discount
        assertEquals(85.00, finalPrice.toDouble(), 0.001)
    }
    
    @Test
    fun `Sum of money list`() {
        val prices = listOf(
            Money(10.50),
            Money(25.99),
            Money(5.00),
            Money(100.01)
        )
        
        val total = prices.sum()
        assertEquals(141.50, total.toDouble(), 0.001)
    }
    
    @Test
    fun `Money comparison works correctly`() {
        val price1 = Money(10.00)
        val price2 = Money(20.00)
        val price3 = Money(10.00)
        
        assertTrue(price2 > price1)
        assertTrue(price1 < price2)
        assertEquals(price1, price3)
        assertTrue(price1 <= price3)
        assertTrue(price2 >= price1)
    }
    
    @Test
    fun `Money zero handling`() {
        val zero = Money.ZERO
        val price = Money(10.00)
        
        assertTrue(zero.isZero())
        assertFalse(price.isZero())
        
        val result = price + zero
        assertEquals(price, result)
        
        val subtracted = price - price
        assertTrue(subtracted.isZero())
    }
    
    @Test
    fun `Money negative values`() {
        val positive = Money(10.00)
        val negative = -positive
        
        assertTrue(positive.isPositive())
        assertFalse(positive.isNegative())
        
        assertTrue(negative.isNegative())
        assertFalse(negative.isPositive())
        
        assertEquals(10.00, negative.abs().toDouble(), 0.001)
    }
    
    @Test
    fun `Money formatting with different symbols`() {
        val price = Money(19.99)
        
        assertEquals("$19.99", price.format("$"))
        assertEquals("€19.99", price.format("€"))
        assertEquals("£19.99", price.format("£"))
    }
    
    @Test
    fun `Real world POS scenario - multiple items with tax`() {
        // Scenario: Customer buys 3 items
        // Item 1: $12.50 x 2 = $25.00
        // Item 2: $8.99 x 1 = $8.99
        // Item 3: $15.00 x 3 = $45.00
        // Subtotal: $78.99
        // Tax (19%): $15.01
        // Total: $94.00
        
        val item1 = Money(12.50) * 2
        val item2 = Money(8.99) * 1
        val item3 = Money(15.00) * 3
        
        val subtotal = listOf(item1, item2, item3).sum()
        assertEquals(78.99, subtotal.toDouble(), 0.001)
        
        val tax = subtotal * 0.19
        assertEquals(15.01, tax.toDouble(), 0.01)  // Allow small rounding difference
        
        val total = subtotal + tax
        assertEquals(94.00, total.toDouble(), 0.01)
    }
    
    @Test
    fun `Real world POS scenario - discount application`() {
        // Scenario: $100 order with 15% discount
        val subtotal = Money(100.00)
        val discountPercent = 15.0
        val discount = Money.percentage(subtotal, discountPercent)
        
        assertEquals(15.00, discount.toDouble(), 0.001)
        
        val afterDiscount = subtotal - discount
        assertEquals(85.00, afterDiscount.toDouble(), 0.001)
        
        // Apply 19% tax on discounted amount
        val tax = afterDiscount * 0.19
        assertEquals(16.15, tax.toDouble(), 0.001)
        
        val finalTotal = afterDiscount + tax
        assertEquals(101.15, finalTotal.toDouble(), 0.001)
    }
    
    @Test
    fun `Rounding behaves correctly`() {
        // Test HALF_UP rounding (0.5 rounds up)
        val value1 = Money(10.125)  // Should round to 10.13
        assertEquals(10.13, value1.toDouble(), 0.001)
        
        val value2 = Money(10.124)  // Should round to 10.12
        assertEquals(10.12, value2.toDouble(), 0.001)
        
        val value3 = Money(10.115)  // Should round to 10.12 (HALF_UP)
        assertEquals(10.12, value3.toDouble(), 0.001)
    }
}
