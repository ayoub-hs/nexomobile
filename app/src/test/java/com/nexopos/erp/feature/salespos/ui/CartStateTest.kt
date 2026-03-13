package com.nexopos.erp.feature.salespos.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CartState] and [CartItem] data classes.
 * 
 * Tests cart calculations, totals, and state management.
 */
class CartStateTest {

    @Test
    fun `cart total calculates correctly with multiple items`() {
        // Given
        val items = listOf(
            CartItem(
                key = "1",
                productId = 1L,
                name = "Product 1",
                quantity = 2.0,
                unitPrice = 100.0
            ),
            CartItem(
                key = "2",
                productId = 2L,
                name = "Product 2",
                quantity = 1.0,
                unitPrice = 50.0
            )
        )

        // When
        val cartState = CartState(items = items)

        // Then
        assertEquals(250.0, cartState.subtotal, 0.01)
    }

    @Test
    fun `cart item line total calculates correctly`() {
        // Given
        val cartItem = CartItem(
            key = "1",
            productId = 1L,
            name = "Test Product",
            quantity = 3.0,
            unitPrice = 25.50
        )

        // When
        val lineTotal = cartItem.lineTotal

        // Then
        assertEquals(76.50, lineTotal, 0.01)
    }

    @Test
    fun `empty cart has zero totals`() {
        // Given
        val cartState = CartState()

        // Then
        assertTrue(cartState.items.isEmpty())
        assertEquals(0.0, cartState.subtotal, 0.01)
        assertEquals(0.0, cartState.total, 0.01)
        assertEquals(0.0, cartState.taxTotal, 0.01)
        assertEquals(0.0, cartState.discountAmount, 0.01)
    }

    @Test
    fun `cart state with discount calculates discount amount`() {
        // Given
        val items = listOf(
            CartItem(
                key = "1",
                productId = 1L,
                name = "Product 1",
                quantity = 1.0,
                unitPrice = 100.0
            )
        )

        // When - state with fixed discount
        val cartState = CartState(
            items = items,
            discountType = DiscountType.Amount,
            discountValue = 25.0
        )

        // Then
        assertEquals(25.0, cartState.discountAmount, 0.01)
    }

    @Test
    fun `cart state percentage discount calculates correctly`() {
        // Given
        val items = listOf(
            CartItem(
                key = "1",
                productId = 1L,
                name = "Product 1",
                quantity = 1.0,
                unitPrice = 100.0
            )
        )

        // When - state with percentage discount (10%)
        val cartState = CartState(
            items = items,
            discountType = DiscountType.Percent,
            discountValue = 10.0
        )

        // Then - percentage discount on 100 = 10
        assertEquals(10.0, cartState.discountAmount, 0.01)
    }

    @Test
    fun `cart with tendered amount calculates change`() {
        // Given
        val items = listOf(
            CartItem(
                key = "1",
                productId = 1L,
                name = "Product 1",
                quantity = 1.0,
                unitPrice = 80.0
            )
        )

        // When - tendered 100, total 80
        val cartState = CartState(
            items = items,
            subtotal = 80.0,
            total = 80.0,
            tendered = 100.0
        )

        // Then
        assertEquals(20.0, cartState.change, 0.01)
    }

    @Test
    fun `cart item subtotal excluding tax equals line total`() {
        // Given
        val cartItem = CartItem(
            key = "1",
            productId = 1L,
            name = "Test Product",
            quantity = 5.0,
            unitPrice = 20.0
        )

        // When & Then
        assertEquals(cartItem.lineTotal, cartItem.subtotalExcludingTax, 0.01)
    }

    @Test
    fun `cart item tax amount is zero by default`() {
        // Given
        val cartItem = CartItem(
            key = "1",
            productId = 1L,
            name = "Test Product",
            quantity = 1.0,
            unitPrice = 100.0
        )

        // Then
        assertEquals(0.0, cartItem.taxAmount, 0.01)
    }

    @Test
    fun `discount type enum has correct values`() {
        // Then
        assertEquals(2, DiscountType.entries.size)
        assertTrue(DiscountType.entries.contains(DiscountType.Amount))
        assertTrue(DiscountType.entries.contains(DiscountType.Percent))
    }

    @Test
    fun `cart state default values are correct`() {
        // Given
        val cartState = CartState()

        // Then
        assertEquals(DiscountType.Amount, cartState.discountType)
        assertEquals(0.0, cartState.discountValue, 0.01)
        assertEquals(0.0, cartState.discountAmount, 0.01)
        assertEquals(0.0, cartState.tendered, 0.01)
        assertEquals(0.0, cartState.change, 0.01)
        assertEquals(false, cartState.tenderedOverride)
        assertEquals(true, cartState.printReceipt)
        assertEquals(false, cartState.printerReady)
    }

    @Test
    fun `cart state with custom prices maintains custom flag`() {
        // Given
        val cartItem = CartItem(
            key = "1",
            productId = 1L,
            name = "Custom Price Item",
            quantity = 1.0,
            unitPrice = 150.0,
            isCustomPrice = true
        )

        // When
        val cartState = CartState(items = listOf(cartItem))

        // Then
        assertTrue(cartState.items.first().isCustomPrice)
    }

    @Test
    fun `cart state wholesale pricing maintains flag`() {
        // Given
        val cartItem = CartItem(
            key = "1",
            productId = 1L,
            name = "Wholesale Item",
            quantity = 10.0,
            unitPrice = 80.0,
            useWholesale = true,
            wholesalePriceWithTax = 95.2
        )

        // When
        val cartState = CartState(items = listOf(cartItem))

        // Then
        assertTrue(cartState.items.first().useWholesale)
        assertEquals(95.2, cartState.items.first().wholesalePriceWithTax)
    }

    @Test
    fun `cart with zero quantity items calculates correctly`() {
        // Given
        val items = listOf(
            CartItem(
                key = "1",
                productId = 1L,
                name = "Product 1",
                quantity = 0.0,
                unitPrice = 100.0
            )
        )

        // When
        val cartState = CartState(items = items)

        // Then - line total should be 0
        assertEquals(0.0, cartState.items.first().lineTotal, 0.01)
        assertEquals(0.0, cartState.subtotal, 0.01)
    }

    @Test
    fun `cart state copy preserves items`() {
        // Given
        val originalItems = listOf(
            CartItem(
                key = "1",
                productId = 1L,
                name = "Product 1",
                quantity = 1.0,
                unitPrice = 100.0
            )
        )
        val originalState = CartState(items = originalItems)

        // When
        val copiedState = originalState.copy(total = 150.0)

        // Then
        assertEquals(1, copiedState.items.size)
        assertEquals(150.0, copiedState.total, 0.01)
    }

    @Test
    fun `large quantity cart calculates correctly`() {
        // Given
        val items = listOf(
            CartItem(
                key = "1",
                productId = 1L,
                name = "Bulk Product",
                quantity = 100.0,
                unitPrice = 10.0
            )
        )

        // When
        val cartState = CartState(items = items)

        // Then
        assertEquals(1000.0, cartState.items.first().lineTotal, 0.01)
        assertEquals(1000.0, cartState.subtotal, 0.01)
    }

    @Test
    fun `decimal quantity cart calculates correctly`() {
        // Given
        val items = listOf(
            CartItem(
                key = "1",
                productId = 1L,
                name = "Liquid Product",
                quantity = 1.5,
                unitPrice = 10.0
            )
        )

        // When
        val cartState = CartState(items = items)

        // Then
        assertEquals(15.0, cartState.items.first().lineTotal, 0.01)
    }
}
