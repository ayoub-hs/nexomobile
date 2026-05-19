package com.nexopos.erp.feature.salespos.ui

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.nexopos.erp.core.db.AppDatabase
import com.nexopos.erp.core.network.ServiceLocator
import com.nexopos.erp.core.repo.CustomerRepository
import com.nexopos.erp.core.repo.OrderQueueRepository
import com.nexopos.erp.core.repo.OrderRepository
import com.nexopos.erp.core.prefs.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CartViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var appContext: Context
    private lateinit var orderRepository: OrderRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var orderQueueRepository: OrderQueueRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var viewModel: CartViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        mockkObject(AppDatabase.Companion)
        val mockDb = mockk<AppDatabase>(relaxed = true)
        every { AppDatabase.get(any()) } returns mockDb
        every { mockDb.productDao() } returns mockk(relaxed = true)

        mockkObject(ServiceLocator)
        every { ServiceLocator.api(any(), any()) } returns mockk(relaxed = true)
        every { ServiceLocator.mobileApi(any(), any()) } returns mockk(relaxed = true)
        ServiceLocator.clearApiCache()

        appContext = mockk(relaxed = true)
        every { appContext.applicationContext } returns appContext
        every { appContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockk<ConnectivityManager>(relaxed = true)

        orderRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        
        // Mock ALL flows used with .first() to prevent NoSuchElementException
        every { settingsRepository.baseUrlFlow } returns MutableStateFlow("https://test.example.com/")
        every { settingsRepository.tokenFlow } returns MutableStateFlow("test-token")
        every { settingsRepository.printerConfigFlow } returns MutableStateFlow(mockk(relaxed = true))
        
        orderQueueRepository = mockk(relaxed = true)
        every { orderQueueRepository.observePendingCount() } returns MutableStateFlow(0)
        every { orderQueueRepository.observeFailedCount() } returns MutableStateFlow(0)

        customerRepository = mockk(relaxed = true)
        coEvery { customerRepository.listCustomers() } returns Result.success(emptyList())
        coEvery { orderRepository.listPaymentMethods(any()) } returns Result.success(emptyList())

        viewModel = CartViewModel(
            appContext = appContext,
            orderRepository = orderRepository,
            settingsRepository = settingsRepository,
            orderQueueRepository = orderQueueRepository,
            customerRepository = customerRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `viewModel starts with empty cart`() = runTest {
        advanceUntilIdle()
        assertEquals(0, viewModel.state.value.items.size)
        assertEquals(0.0, viewModel.state.value.total, 0.0)
    }

    @Test
    fun `quick custom products are added as separate lines`() = runTest {
        advanceUntilIdle()

        viewModel.addProduct(
            productId = 0L,
            name = "Quick Product A",
            unitQuantityId = null,
            unitId = null,
            unitName = null,
            unitPrice = 10.0,
            quantity = 1.0,
            isCustomPrice = true
        )
        viewModel.addProduct(
            productId = 0L,
            name = "Quick Product B",
            unitQuantityId = null,
            unitId = null,
            unitName = null,
            unitPrice = 20.0,
            quantity = 2.0,
            isCustomPrice = true
        )

        val items = viewModel.state.value.items
        assertEquals(2, items.size)
        assertEquals("Quick Product A", items[0].name)
        assertEquals(1.0, items[0].quantity, 0.0)
        assertEquals("Quick Product B", items[1].name)
        assertEquals(2.0, items[1].quantity, 0.0)
    }

    @Test
    fun `edited price item can be added again`() = runTest {
        advanceUntilIdle()

        viewModel.addProduct(
            productId = 101L,
            name = "Coffee",
            unitQuantityId = 11L,
            unitId = 1L,
            unitName = "PCS",
            unitPrice = 10.0,
            quantity = 1.0,
            isCustomPrice = false
        )
        val key = viewModel.state.value.items.first().key

        viewModel.updateItemPrice(key, 8.5)
        viewModel.addProduct(
            productId = 101L,
            name = "Coffee",
            unitQuantityId = 11L,
            unitId = 1L,
            unitName = "PCS",
            unitPrice = 10.0,
            quantity = 1.0,
            isCustomPrice = false
        )

        val items = viewModel.state.value.items
        assertEquals(1, items.size)
        assertEquals(2.0, items.first().quantity, 0.0)
        assertEquals(8.5, items.first().unitPrice, 0.0)
    }

    @Test
    fun `cart can increment existing line at max size`() = runTest {
        advanceUntilIdle()

        for (id in 1L..100L) {
            viewModel.addProduct(
                productId = id,
                name = "Item $id",
                unitQuantityId = id,
                unitId = 1L,
                unitName = "PCS",
                unitPrice = 5.0,
                quantity = 1.0,
                isCustomPrice = false
            )
        }
        assertEquals(100, viewModel.state.value.items.size)

        viewModel.addProduct(
            productId = 1L,
            name = "Item 1",
            unitQuantityId = 1L,
            unitId = 1L,
            unitName = "PCS",
            unitPrice = 5.0,
            quantity = 1.0,
            isCustomPrice = false
        )

        val items = viewModel.state.value.items
        assertEquals(100, items.size)
        val first = items.first { it.productId == 1L && it.unitQuantityId == 1L }
        assertEquals(2.0, first.quantity, 0.0)
    }

    @Test
    fun `re-added existing product moves to end of cart`() = runTest {
        advanceUntilIdle()

        viewModel.addProduct(
            productId = 1L,
            name = "Item 1",
            unitQuantityId = 1L,
            unitId = 1L,
            unitName = "PCS",
            unitPrice = 5.0,
            quantity = 1.0,
            isCustomPrice = false
        )
        viewModel.addProduct(
            productId = 2L,
            name = "Item 2",
            unitQuantityId = 2L,
            unitId = 1L,
            unitName = "PCS",
            unitPrice = 6.0,
            quantity = 1.0,
            isCustomPrice = false
        )

        viewModel.addProduct(
            productId = 1L,
            name = "Item 1",
            unitQuantityId = 1L,
            unitId = 1L,
            unitName = "PCS",
            unitPrice = 5.0,
            quantity = 1.0,
            isCustomPrice = false
        )

        val items = viewModel.state.value.items
        assertEquals(2, items.size)
        assertEquals(2L, items[0].productId)
        assertEquals(1L, items[1].productId)
        assertEquals(2.0, items[1].quantity, 0.0)
    }
}
