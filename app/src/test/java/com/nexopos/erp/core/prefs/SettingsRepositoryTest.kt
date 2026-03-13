package com.nexopos.erp.core.prefs

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.nexopos.erp.core.network.ServiceLocator
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SettingsRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        mockkObject(ServiceLocator)
        every { ServiceLocator.api(any()) } returns mockk(relaxed = true)
        every { ServiceLocator.mobileApi(any()) } returns mockk(relaxed = true)

        // Mock SecureTokenStorage to avoid Keystore access in unit tests
        mockkConstructor(SecureTokenStorage::class)
        every { anyConstructed<SecureTokenStorage>().tokenFlow } returns MutableStateFlow("")

        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        
        // Mock DataStore extension property
        mockkStatic("com.nexopos.erp.core.prefs.SettingsRepositoryKt")
        val mockDataStore = mockk<DataStore<Preferences>>(relaxed = true)
        val mockPreferences = mockk<Preferences>(relaxed = true)
        every { mockDataStore.data } returns MutableStateFlow(mockPreferences)
        every { context.dataStore } returns mockDataStore
        
        repository = SettingsRepository(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `settings repository is created`() = runTest {
        assertTrue(true)
    }
}
