package com.telestudy.tv.features.auth

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telestudy.tv.core.tdlib.AuthState
import com.telestudy.tv.core.tdlib.TdlibAuthException
import com.telestudy.tv.core.tdlib.TelegramAuthManager
import com.telestudy.tv.core.util.QrGenerator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import timber.log.Timber

enum class AuthTab {
    PHONE,
    QR
}

data class AuthUiState(
    val authState: AuthState = AuthState.Initial,
    val selectedTab: AuthTab = AuthTab.PHONE,
    val phoneNumber: String = "+",
    val code: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val resendCountdown: Int = 0,
    val qrBitmap: Bitmap? = null,
    val qrLink: String? = null,
    val currentUser: TdApi.User? = null
)

/**
 * Shared ViewModel managing Telegram authentication lifecycle across Phone, Tablet, and Android TV.
 */
class AuthViewModel(
    private val authManager: TelegramAuthManager,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var isQrRequestInFlight = false
    private var isQrRequestDeferred = false

    init {
        observeAuthState()
        observeCurrentUser()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authManager.authState.collect { state ->
                _uiState.update { current ->
                    current.copy(
                        authState = state,
                        isLoading = if (state is AuthState.WaitPhoneNumber && isQrRequestDeferred) current.isLoading else false,
                        errorMessage = if (state is AuthState.Error) formatErrorMessage(state.code, state.message) else current.errorMessage
                    )
                }

                when (state) {
                    is AuthState.WaitPhoneNumber -> {
                        // If QR tab is currently selected and a request was deferred (or we need to initiate QR)
                        if (_uiState.value.selectedTab == AuthTab.QR && (isQrRequestDeferred || _uiState.value.qrBitmap == null)) {
                            Timber.i("TDLib reached WaitPhoneNumber and QR tab is active. Executing deferred QR request.")
                            requestQrCode()
                        }
                    }
                    is AuthState.WaitCode -> {
                        startResendCountdown()
                    }
                    is AuthState.WaitOtherDeviceConfirmation -> {
                        isQrRequestDeferred = false
                        isQrRequestInFlight = false
                        generateQrCode(state.link)
                    }
                    is AuthState.Ready -> {
                        countdownJob?.cancel()
                        isQrRequestDeferred = false
                        isQrRequestInFlight = false
                        _uiState.update { it.copy(code = "", password = "", resendCountdown = 0) }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            authManager.currentUser.collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
    }

    fun selectTab(tab: AuthTab) {
        _uiState.update { it.copy(selectedTab = tab, errorMessage = null) }
        if (tab == AuthTab.QR) {
            requestQrCode()
        } else {
            isQrRequestDeferred = false
            if (_uiState.value.authState is AuthState.WaitOtherDeviceConfirmation) {
                authManager.cancelQrCode()
            }
        }
    }

    fun onPhoneNumberChanged(number: String) {
        _uiState.update { it.copy(phoneNumber = number, errorMessage = null) }
    }

    fun submitPhoneNumber() {
        val rawNumber = _uiState.value.phoneNumber.trim()
        val digitsOnly = rawNumber.filter { it.isDigit() }
        if (rawNumber.isEmpty() || rawNumber == "+" || digitsOnly.length < 5) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid phone number with country code (e.g. +959...)") }
            return
        }

        val formattedNumber = if (rawNumber.startsWith("+")) rawNumber else "+$rawNumber"
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        if (_uiState.value.authState is AuthState.WaitOtherDeviceConfirmation) {
            authManager.cancelQrCode {
                authManager.setPhoneNumber(formattedNumber) { result ->
                    result.onFailure { ex ->
                        val message = if (ex is TdlibAuthException) {
                            formatErrorMessage(ex.code, ex.message ?: "")
                        } else {
                            ex.message ?: "Failed to submit phone number"
                        }
                        _uiState.update { it.copy(isLoading = false, errorMessage = message) }
                    }
                }
            }
            return
        }

        authManager.setPhoneNumber(formattedNumber) { result ->
            result.onFailure { ex ->
                val message = if (ex is TdlibAuthException) {
                    formatErrorMessage(ex.code, ex.message ?: "")
                } else {
                    ex.message ?: "Failed to submit phone number"
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = message) }
            }
        }
    }

    fun onCodeChanged(code: String) {
        // Strip non-digits
        val digitsOnly = code.filter { it.isDigit() }
        _uiState.update { it.copy(code = digitsOnly, errorMessage = null) }
    }

    fun submitCode() {
        val code = _uiState.value.code.trim()
        if (code.isEmpty() || code.length < 3) {
            _uiState.update { it.copy(errorMessage = "Please enter the verification code sent to your Telegram account") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        authManager.checkCode(code) { result ->
            result.onFailure { ex ->
                val message = if (ex is TdlibAuthException) {
                    formatErrorMessage(ex.code, ex.message ?: "")
                } else {
                    ex.message ?: "Failed to verify code"
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = message, code = "") }
            }
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun submitPassword() {
        val password = _uiState.value.password
        if (password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter your Two-Step Verification password") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        authManager.checkPassword(password) { result ->
            result.onFailure { ex ->
                val message = if (ex is TdlibAuthException) {
                    formatErrorMessage(ex.code, ex.message ?: "")
                } else {
                    ex.message ?: "Failed to verify password"
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = message, password = "") }
            }
        }
    }

    fun requestQrCode() {
        val currentAuthState = authManager.authState.value

        // 1. If state is already waiting for confirmation and has link, ensure bitmap is present
        if (currentAuthState is AuthState.WaitOtherDeviceConfirmation) {
            if (_uiState.value.qrBitmap == null && currentAuthState.link.isNotBlank()) {
                generateQrCode(currentAuthState.link)
            }
            return
        }

        // 2. If TDLib is still initializing (Initial or WaitParameters), defer until WaitPhoneNumber
        if (currentAuthState is AuthState.Initial || currentAuthState is AuthState.WaitParameters) {
            isQrRequestDeferred = true
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            Timber.d("TDLib is initializing (%s). Deferring QR request until WaitPhoneNumber.", currentAuthState.javaClass.simpleName)
            return
        }

        // 3. Single-flight guard: at most ONE active QR request
        if (isQrRequestInFlight) {
            Timber.d("QR request already in flight. Skipping duplicate request.")
            return
        }

        // 4. Dispatch to TDLib only when in WaitPhoneNumber
        if (currentAuthState is AuthState.WaitPhoneNumber) {
            isQrRequestInFlight = true
            isQrRequestDeferred = false
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            Timber.i("Dispatching QR code authentication request to TDLib...")
            authManager.requestQrCode { result ->
                isQrRequestInFlight = false
                result.onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                }
                result.onFailure { ex ->
                    val message = if (ex is TdlibAuthException) {
                        formatErrorMessage(ex.code, ex.message ?: "")
                    } else {
                        ex.message ?: "Failed to generate QR code"
                    }
                    _uiState.update { it.copy(isLoading = false, errorMessage = message) }
                }
            }
        } else {
            Timber.w("Cannot request QR code in state: %s", currentAuthState.javaClass.simpleName)
        }
    }

    fun resendCode() {
        if (_uiState.value.resendCountdown > 0) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        authManager.resendCode { result ->
            result.onSuccess {
                startResendCountdown()
                _uiState.update { it.copy(isLoading = false) }
            }
            result.onFailure { ex ->
                val message = if (ex is TdlibAuthException) {
                    formatErrorMessage(ex.code, ex.message ?: "")
                } else {
                    ex.message ?: "Failed to resend code"
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = message) }
            }
        }
    }

    fun logOut() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        authManager.logOut { result ->
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun generateQrCode(link: String) {
        viewModelScope.launch(defaultDispatcher) {
            val bitmap = QrGenerator.generateQrBitmap(link, sizePx = 600)
            _uiState.update { it.copy(qrLink = link, qrBitmap = bitmap, isLoading = false) }
        }
    }

    private fun startResendCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (i in 60 downTo 0) {
                _uiState.update { it.copy(resendCountdown = i) }
                delay(1000)
            }
        }
    }

    private fun formatErrorMessage(code: Int, rawMessage: String): String {
        return when {
            rawMessage.contains("PHONE_NUMBER_INVALID", ignoreCase = true) ->
                "Invalid phone number. Please include the international country code (e.g. +1...)."
            rawMessage.contains("PHONE_CODE_INVALID", ignoreCase = true) ->
                "Incorrect Telegram verification code. Please check the code in your Telegram app and try again."
            rawMessage.contains("PHONE_CODE_EXPIRED", ignoreCase = true) ->
                "The verification code has expired. Please click Resend Code."
            rawMessage.contains("PASSWORD_HASH_INVALID", ignoreCase = true) ->
                "Incorrect Two-Step Verification password. Please try again."
            rawMessage.contains("FLOOD_WAIT", ignoreCase = true) ->
                "Too many attempts. Please wait a few minutes before trying again."
            rawMessage.contains("ACCESS_TOKEN_INVALID", ignoreCase = true) ->
                "Invalid access token."
            rawMessage.isNotBlank() ->
                rawMessage.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            else -> "An error occurred during authentication (code $code). Please try again."
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}
