package com.nexopos.erp.feature.orders.vm

import android.content.Context
import android.util.Log
import com.nexopos.erp.core.db.AppDatabase
import com.nexopos.erp.core.network.ServiceLocator
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.erp.core.repo.OrderQueueRepository
import com.nexopos.erp.core.repo.OrderRepository
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [OrdersViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var appContext: Context
    private lateinit var queueRepository: OrderQueueRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: OrdersViewModel

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
        every { AppDatabase.get(any()) } returns mockk(relaxed = true)

        mockkObject(ServiceLocator)
        every { ServiceLocator.api(any()) } returns mockk(relaxed = true)
        every { ServiceLocator.mobileApi(any()) } returns mockk(relaxed = true)

        appContext = mockk(relaxed = true)
        every { appContext.applicationContext } returns appContext

        queueRepository = mockk(relaxed = true)
        every { queueRepository.observeAll() } returns MutableStateFlow(emptyList())
        
        orderRepository = mockk(relaxed = true)
        
        settingsRepository = mockk(relaxed = true)
        every { settingsRepository.baseUrlFlow } returns MutableStateFlow("https://test.example.com/")
        every { settingsRepository.tokenFlow } returns MutableStateFlow("test-token")
        
        viewModel = OrdersViewModel(appContext, queueRepository, orderRepository, settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `refreshFromServer updates state`() = runTest {
        viewModel.refreshFromServer()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
    }
}
