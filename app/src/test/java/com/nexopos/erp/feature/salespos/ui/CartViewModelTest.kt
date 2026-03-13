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
import io.mockk.mockkConstructor
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
        every { ServiceLocator.api(any()) } returns mockk(relaxed = true)
        every { ServiceLocator.mobileApi(any()) } returns mockk(relaxed = true)
        ServiceLocator.clearApiCache()

        // Mock CustomerRepository to avoid internal initialization issues
        mockkConstructor(CustomerRepository::class)
        coEvery { anyConstructed<CustomerRepository>().listCustomers() } returns Result.success(emptyList())

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

        coEvery { orderRepository.listPaymentMethods(any()) } returns Result.success(emptyList())

        viewModel = CartViewModel(
            appContext = appContext,
            orderRepository = orderRepository,
            settingsRepository = settingsRepository,
            orderQueueRepository = orderQueueRepository
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
}
