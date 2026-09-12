package com.telestudy.tv.features.auth

import com.telestudy.tv.core.tdlib.AuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeAuthManager: FakeTelegramAuthManager
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAuthManager = FakeTelegramAuthManager()
        viewModel = AuthViewModel(fakeAuthManager)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState_startsAtWaitPhoneNumber() {
        val state = viewModel.uiState.value
        assertEquals(AuthState.WaitPhoneNumber, state.authState)
        assertEquals(AuthTab.PHONE, state.selectedTab)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun testSubmitPhoneNumber_validNumber_transitionsToWaitCode() {
        viewModel.onPhoneNumberChanged("+1234567890")
        viewModel.submitPhoneNumber()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAuthManager.setPhoneNumberCallCount)
        assertEquals("+1234567890", fakeAuthManager.submittedPhoneNumber)

        val state = viewModel.uiState.value
        assertTrue("State should transition to WaitCode", state.authState is AuthState.WaitCode)
        assertNull(state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun testSubmitPhoneNumber_invalidNumber_setsErrorMessage() {
        viewModel.onPhoneNumberChanged("123") // Too short
        viewModel.submitPhoneNumber()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fakeAuthManager.setPhoneNumberCallCount)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testSubmitPhoneNumber_tdlibRejection_setsErrorMessage() {
        viewModel.onPhoneNumberChanged("+9999999999") // Invalid in fake
        viewModel.submitPhoneNumber()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAuthManager.setPhoneNumberCallCount)
        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Invalid phone number", ignoreCase = true))
        assertFalse(state.isLoading)
    }

    @Test
    fun testSubmitCode_validCode_transitionsToReady() {
        // 1. Move to WaitCode
        fakeAuthManager._authState.value = AuthState.WaitCode()
        testDispatcher.scheduler.advanceUntilIdle()

        // 2. Submit code
        viewModel.onCodeChanged("12345")
        viewModel.submitCode()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAuthManager.checkCodeCallCount)
        val state = viewModel.uiState.value
        assertTrue("State should transition to Ready", state.authState is AuthState.Ready)
        assertNotNull(state.currentUser)
        assertEquals("Test", state.currentUser?.firstName)
    }

    @Test
    fun testSubmitCode_invalidCode_setsErrorMessageAndClearsCode() {
        fakeAuthManager._authState.value = AuthState.WaitCode()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onCodeChanged("99999") // Wrong code
        viewModel.submitCode()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAuthManager.checkCodeCallCount)
        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Incorrect Telegram verification code", ignoreCase = true))
        assertEquals("", state.code) // Cleared for security
    }

    @Test
    fun testSubmitCode_with2fa_transitionsToWaitPassword() {
        fakeAuthManager.shouldRequire2fa = true
        fakeAuthManager._authState.value = AuthState.WaitCode()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onCodeChanged("12345")
        viewModel.submitCode()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State should transition to WaitPassword", state.authState is AuthState.WaitPassword)
        val waitPassword = state.authState as AuthState.WaitPassword
        assertEquals("Hint: school", waitPassword.passwordHint)
    }

    @Test
    fun testSubmitPassword_validPassword_transitionsToReady() {
        fakeAuthManager._authState.value = AuthState.WaitPassword(passwordHint = "Hint: school", hasRecoveryEmailAddress = true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPasswordChanged("correct_password")
        viewModel.submitPassword()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAuthManager.checkPasswordCallCount)
        val state = viewModel.uiState.value
        assertTrue("State should transition to Ready", state.authState is AuthState.Ready)
        assertNotNull(state.currentUser)
    }

    @Test
    fun testSubmitPassword_invalidPassword_setsErrorMessageAndClearsPassword() {
        fakeAuthManager._authState.value = AuthState.WaitPassword(passwordHint = "Hint: school", hasRecoveryEmailAddress = true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPasswordChanged("wrong_password")
        viewModel.submitPassword()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAuthManager.checkPasswordCallCount)
        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Incorrect Two-Step Verification password", ignoreCase = true))
        assertEquals("", state.password) // Cleared for security
    }

    @Test
    fun testSelectTab_switchBetweenPhoneAndQr() {
        viewModel.selectTab(AuthTab.QR)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AuthTab.QR, viewModel.uiState.value.selectedTab)
        assertEquals(1, fakeAuthManager.requestQrCodeCallCount)
        assertTrue(viewModel.uiState.value.authState is AuthState.WaitOtherDeviceConfirmation)

        viewModel.selectTab(AuthTab.PHONE)
        assertEquals(AuthTab.PHONE, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun testLogOut_clearsCurrentUserAndTransitionsToClosed() {
        viewModel.logOut()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAuthManager.logOutCallCount)
        val state = viewModel.uiState.value
        assertEquals(AuthState.Closed, state.authState)
        assertNull(state.currentUser)
    }

    @Test
    fun testTogglePasswordVisibility() {
        assertFalse(viewModel.uiState.value.isPasswordVisible)
        viewModel.togglePasswordVisibility()
        assertTrue(viewModel.uiState.value.isPasswordVisible)
        viewModel.togglePasswordVisibility()
        assertFalse(viewModel.uiState.value.isPasswordVisible)
    }
}
