package com.nexopos.erp.feature.settings.vm

import android.util.Log
import com.nexopos.erp.core.db.AppDatabase
import com.nexopos.erp.core.network.ServiceLocator
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.erp.feature.auth.AuthViewModel
import io.mockk.coVerify
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
 * Unit tests for [SettingsViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var authViewModel: AuthViewModel
    private lateinit var viewModel: SettingsViewModel

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
        ServiceLocator.clearApiCache()

        settingsRepository = mockk(relaxed = true)
        authViewModel = mockk(relaxed = true)
        
        every { settingsRepository.baseUrlFlow } returns MutableStateFlow("http://test.com")
        every { settingsRepository.tokenFlow } returns MutableStateFlow("")
        every { settingsRepository.storeNameFlow } returns MutableStateFlow("")
        every { settingsRepository.printerConfigFlow } returns MutableStateFlow(mockk(relaxed = true))
        
        viewModel = SettingsViewModel(settingsRepository, authViewModel)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `save updates repository`() = runTest {
        val newUrl = "https://new.com"
        viewModel.save(newUrl, "token", "store")
        advanceUntilIdle()

        // Verify that the repository was called with the correct values
        coVerify { settingsRepository.setBaseUrl(newUrl) }
        coVerify { settingsRepository.setToken("token") }
        coVerify { settingsRepository.setStoreName("store") }
    }
}
