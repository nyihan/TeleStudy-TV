package com.telestudy.tv.features.auth

import com.telestudy.tv.core.tdlib.AuthState
import com.telestudy.tv.core.tdlib.TdlibAuthException
import com.telestudy.tv.core.tdlib.TelegramAuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/**
 * Fake implementation of TelegramAuthManager for deterministic testing of the AuthViewModel state machine.
 */
class FakeTelegramAuthManager : TelegramAuthManager {
    val _authState = MutableStateFlow<AuthState>(AuthState.WaitPhoneNumber)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val _currentUser = MutableStateFlow<TdApi.User?>(null)
    override val currentUser: StateFlow<TdApi.User?> = _currentUser.asStateFlow()

    var shouldRequire2fa = false
    var validPhoneNumber = "+1234567890"
    var validCode = "12345"
    var validPassword = "correct_password"
    var mockQrLink = "tg://login?token=mock_test_token_12345"

    var submittedPhoneNumber: String? = null
    var setPhoneNumberCallCount = 0
    var checkCodeCallCount = 0
    var checkPasswordCallCount = 0
    var requestQrCodeCallCount = 0
    var logOutCallCount = 0
    var resendCodeCallCount = 0

    override fun setPhoneNumber(phoneNumber: String, onResult: (Result<Unit>) -> Unit) {
        setPhoneNumberCallCount++
        submittedPhoneNumber = phoneNumber
        if (phoneNumber == validPhoneNumber) {
            _authState.value = AuthState.WaitCode()
            onResult(Result.success(Unit))
        } else {
            onResult(Result.failure(TdlibAuthException(400, "PHONE_NUMBER_INVALID")))
        }
    }

    override fun checkCode(code: String, onResult: (Result<Unit>) -> Unit) {
        checkCodeCallCount++
        if (code == validCode) {
            if (shouldRequire2fa) {
                _authState.value = AuthState.WaitPassword(passwordHint = "Hint: school", hasRecoveryEmailAddress = true)
            } else {
                val mockUser = TdApi.User().apply {
                    id = 123456789L
                    firstName = "Test"
                    lastName = "Parent"
                }
                _currentUser.value = mockUser
                _authState.value = AuthState.Ready(mockUser)
            }
            onResult(Result.success(Unit))
        } else {
            onResult(Result.failure(TdlibAuthException(400, "PHONE_CODE_INVALID")))
        }
    }

    override fun checkPassword(password: String, onResult: (Result<Unit>) -> Unit) {
        checkPasswordCallCount++
        if (password == validPassword) {
            val mockUser = TdApi.User().apply {
                id = 123456789L
                firstName = "Test"
                lastName = "Parent"
            }
            _currentUser.value = mockUser
            _authState.value = AuthState.Ready(mockUser)
            onResult(Result.success(Unit))
        } else {
            onResult(Result.failure(TdlibAuthException(400, "PASSWORD_HASH_INVALID")))
        }
    }

    override fun requestQrCode(onResult: (Result<Unit>) -> Unit) {
        requestQrCodeCallCount++
        _authState.value = AuthState.WaitOtherDeviceConfirmation(link = mockQrLink)
        onResult(Result.success(Unit))
    }

    override fun resendCode(onResult: (Result<Unit>) -> Unit) {
        resendCodeCallCount++
        onResult(Result.success(Unit))
    }

    override fun cancelQrCode(onResult: (Result<Unit>) -> Unit) {
        _authState.value = AuthState.WaitPhoneNumber
        onResult(Result.success(Unit))
    }

    override fun logOut(onResult: (Result<Unit>) -> Unit) {
        logOutCallCount++
        _currentUser.value = null
        _authState.value = AuthState.Closed
        onResult(Result.success(Unit))
    }
}
