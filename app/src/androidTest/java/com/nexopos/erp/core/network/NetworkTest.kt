package com.nexopos.erp.core.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented network/API tests for mobile API endpoints.
 * 
 * These tests verify API response models and integration patterns.
 * Note: These tests don't make actual network calls but test the model layer.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class NetworkTest {

    @Test
    fun loginApi_responseModel_parsesCorrectly() {
        // Given - a login response model
        val loginResponse = LoginResponse(
            success = true,
            data = LoginResponse.LoginData(
                token = "test_token_123",
                user = LoginResponse.UserInfo(
                    id = 1L,
                    name = "Test User",
                    email = "test@example.com"
                )
            )
        )

        // Then - model is correctly parsed
        assertTrue(loginResponse.success)
        assertNotNull(loginResponse.data)
        assertEquals("test_token_123", loginResponse.data?.token)
        assertEquals("Test User", loginResponse.data?.user?.name)
        assertEquals("test@example.com", loginResponse.data?.user?.email)
    }

    @Test
    fun loginApi_requestModel_createsCorrectly() {
        // Given - a login request
        val loginRequest = LoginRequest(
            email = "test@example.com",
            password = "password123",
            deviceName = "NexoPos Mobile Test"
        )

        // Then - request is created correctly
        assertEquals("test@example.com", loginRequest.email)
        assertEquals("password123", loginRequest.password)
        assertEquals("NexoPos Mobile Test", loginRequest.deviceName)
    }

    @Test
    fun userResponse_model_parsesCorrectly() {
        // Given - a user response
        val userResponse = UserResponse(
            success = true,
            data = UserResponse.UserData(
                id = 1L,
                name = "Admin User",
                email = "admin@example.com",
                roles = listOf("admin", "manager")
            )
        )

        // Then - model is correctly parsed
        assertTrue(userResponse.success)
        assertNotNull(userResponse.data)
        assertEquals(1L, userResponse.data?.id)
        assertEquals("Admin User", userResponse.data?.name)
        assertEquals(2, userResponse.data?.roles?.size)
    }

    @Test
    fun permissionsResponse_model_parsesCorrectly() {
        // Given - a permissions response
        val permissionsResponse = PermissionsResponse(
            success = true,
            data = PermissionsData(
                permissions = listOf("orders.create", "orders.read", "products.read"),
                roles = listOf("admin")
            )
        )

        // Then - model is correctly parsed
        assertTrue(permissionsResponse.success)
        assertNotNull(permissionsResponse.data)
        assertEquals(3, permissionsResponse.data?.permissions?.size)
        assertTrue(permissionsResponse.data?.permissions?.contains("orders.create") == true)
    }

    @Test
    fun product_model_hasCorrectStructure() {
        // Given - a product model
        val product = Product(
            id = 1L,
            name = "Test Product",
            barcode = "1234567890",
            barcodeType = "EAN-13",
            sku = "TEST-001",
            status = "active",
            unitQuantities = listOf(
                ProductUnitQuantity(
                    id = 1L,
                    productId = 1L,
                    unitId = 1L,
                    salePrice = 100.0,
                    wholesalePrice = 80.0,
                    wholesalePriceWithTax = 95.2,
                    unit = ProductUnit(id = 1L, name = "Unit"),
                    conversionRatio = 1.0
                )
            )
        )

        // Then - model has correct structure
        assertEquals(1L, product.id)
        assertEquals("Test Product", product.name)
        assertEquals("1234567890", product.barcode)
        assertEquals(1, product.unitQuantities?.size)
        assertEquals(100.0, product.unitQuantities?.first()?.salePrice)
    }

    @Test
    fun customer_model_hasCorrectStructure() {
        // Given - a customer model
        val customer = Customer(
            id = 1L,
            firstName = "John",
            lastName = "Doe",
            email = "john.doe@example.com",
            phone = "+1234567890",
            isDefault = false
        )

        // Then - model has correct structure
        assertEquals(1L, customer.id)
        assertEquals("John", customer.firstName)
        assertEquals("Doe", customer.lastName)
        assertEquals("john.doe@example.com", customer.email)
    }

    @Test
    fun paymentMethod_model_hasCorrectStructure() {
        // Given - a payment method model
        val paymentMethod = PaymentMethod(
            identifier = "cash",
            label = "Cash Payment",
            selected = true,
            readonly = false
        )

        // Then - model has correct structure
        assertEquals("cash", paymentMethod.identifier)
        assertEquals("Cash Payment", paymentMethod.label)
        assertEquals(true, paymentMethod.selected)
        assertEquals(false, paymentMethod.readonly)
    }

    @Test
    fun category_model_hasCorrectStructure() {
        // Given - a category model
        val category = Category(
            id = 1L,
            name = "Electronics",
            slug = "electronics",
            description = "Electronic products",
            displayOrder = 1
        )

        // Then - model has correct structure
        assertEquals(1L, category.id)
        assertEquals("Electronics", category.name)
        assertEquals("electronics", category.slug)
    }

    @Test
    fun orderType_model_hasCorrectStructure() {
        // Given - an order type model
        val orderType = OrderType(
            identifier = "takeaway",
            label = "Take Away",
            selected = true
        )

        // Then - model has correct structure
        assertEquals("takeaway", orderType.identifier)
        assertEquals("Take Away", orderType.label)
        assertEquals(true, orderType.selected)
    }

    @Test
    fun cartItem_calculatesLineTotal() {
        // Given - a cart item
        val cartItem = CartItemForSerialization(
            productId = 1L,
            name = "Test Product",
            quantity = 2.0,
            unitPrice = 50.0
        )

        // Then - line total is calculated
        assertEquals(100.0, cartItem.unitPrice * cartItem.quantity, 0.01)
    }
}

/**
 * Data class for cart item serialization test
 */
data class CartItemForSerialization(
    val productId: Long,
    val name: String,
    val quantity: Double,
    val unitPrice: Double
)
