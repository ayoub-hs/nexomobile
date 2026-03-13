package com.nexopos.erp.feature.auth

import android.util.Log
import com.nexopos.erp.core.network.LoginData
import com.nexopos.erp.core.network.LoginResponse
import com.nexopos.erp.core.network.UserInfo
import com.nexopos.erp.core.repo.AuthRepository
import com.nexopos.erp.core.repo.AuthState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AuthViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        authRepository = mockk(relaxed = true)
        every { authRepository.authState } returns MutableStateFlow(AuthState.Unauthenticated)
        every { authRepository.currentUser } returns MutableStateFlow(null)

        viewModel = AuthViewModel(authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `login success updates state`() = runTest {
        val loginResponse = LoginResponse(
            success = true,
            data = LoginData(
                token = "test-token",
                user = UserInfo(1L, "Test User", "test@example.com")
            )
        )
        coEvery { authRepository.login(any(), any()) } returns Result.success(loginResponse)

        viewModel.login("test@example.com", "password")
        advanceUntilIdle()

        assertEquals(LoginState.Success, viewModel.loginState.value)
    }

    @Test
    fun `login failure updates state`() = runTest {
        val errorMessage = "Invalid credentials"
        coEvery { authRepository.login(any(), any()) } returns Result.failure(Exception(errorMessage))

        viewModel.login("test@example.com", "wrong")
        advanceUntilIdle()

        assertTrue(viewModel.loginState.value is LoginState.Error)
        assertEquals(errorMessage, (viewModel.loginState.value as LoginState.Error).message)
    }
}
