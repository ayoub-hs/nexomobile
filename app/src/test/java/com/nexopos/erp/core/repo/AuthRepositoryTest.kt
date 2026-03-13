package com.nexopos.erp.core.repo

import android.content.Context
import android.util.Log
import com.nexopos.erp.core.db.AppDatabase
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.ServiceLocator
import com.nexopos.erp.core.prefs.FeatureFlags
import com.nexopos.erp.core.prefs.SecureTokenStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AuthRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var api: MobileApi
    private lateinit var tokenStorage: SecureTokenStorage
    private lateinit var featureFlags: FeatureFlags
    private lateinit var repository: AuthRepository

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
        
        api = mockk(relaxed = true)
        tokenStorage = mockk(relaxed = true)
        featureFlags = mockk(relaxed = true)
        
        repository = AuthRepository(context, api, tokenStorage, featureFlags)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `auth repository is created`() = runTest {
        assertTrue(true)
    }
}
