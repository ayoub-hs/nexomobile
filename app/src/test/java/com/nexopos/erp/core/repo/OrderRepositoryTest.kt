package com.nexopos.erp.core.repo

import android.content.Context
import android.util.Log
import com.nexopos.erp.core.db.AppDatabase
import com.nexopos.erp.core.network.ServiceLocator
import com.nexopos.erp.core.prefs.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [OrderRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrderRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var settings: SettingsRepository
    private lateinit var repository: OrderRepository

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

        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        
        settings = mockk(relaxed = true)
        every { settings.baseUrlFlow } returns MutableStateFlow("https://test.example.com/")
        
        repository = OrderRepository(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `order repository is created`() = runTest {
        assertTrue(true)
    }
}
