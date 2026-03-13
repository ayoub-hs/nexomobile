package com.nexopos.erp.feature.containermanagement.vm

import android.util.Log
import com.nexopos.erp.core.db.AppDatabase
import com.nexopos.erp.core.network.NexoApi
import com.nexopos.erp.core.network.ServiceLocator
import com.nexopos.erp.core.prefs.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Unit tests for [ContainerViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContainerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var api: NexoApi
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: ContainerViewModel

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

        val context = mockk<android.content.Context>(relaxed = true)
        every { context.applicationContext } returns context

        api = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        viewModel = ContainerViewModel(api, settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `loadContainers updates state`() = runTest {
        viewModel.loadContainers()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
    }
}
