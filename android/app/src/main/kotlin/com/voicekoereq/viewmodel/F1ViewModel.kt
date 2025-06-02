package com.voicekoereq.viewmodel

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicekoereq.data.model.AuthResponse
import com.voicekoereq.data.model.AuthResult
import com.voicekoereq.data.repository.F1Repository
import com.voicekoereq.data.repository.PreferencesRepository
import com.voicekoereq.data.repository.TokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class F1ViewModel @Inject constructor(
    private val repository: F1Repository,
    private val preferencesRepository: PreferencesRepository,
    private val tokenRepository: TokenRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(F1UiState())
    val uiState: StateFlow<F1UiState> = _uiState.asStateFlow()

    init {
        checkBiometricAvailability()
        loadSavedCredentials()
    }

    fun updateUsername(username: String) {
        _uiState.update { it.copy(username = username) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun updateRememberMe(rememberMe: Boolean) {
        _uiState.update { it.copy(rememberMe = rememberMe) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun isFormValid(): Boolean {
        val state = _uiState.value
        return state.username.isNotBlank() && state.password.isNotBlank()
    }

    fun login() {
        if (!isFormValid()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            repository.authenticate(
                username = _uiState.value.username,
                password = _uiState.value.password
            ).collect { result ->
                when (result) {
                    is AuthResult.Success -> handleLoginSuccess(result.data)
                    is AuthResult.Error -> handleLoginError(result.exception)
                    is AuthResult.Loading -> _uiState.update { it.copy(isLoading = true) }
                }
            }
        }
    }

    fun loginWithBiometrics() {
        if (!_uiState.value.isBiometricAvailable) return

        // This method would be called from the UI layer with proper FragmentActivity context
        // The actual biometric prompt implementation would be handled there
        viewModelScope.launch {
            val savedCredentials = preferencesRepository.getSavedCredentials()
            if (savedCredentials != null) {
                _uiState.update { it.copy(isLoading = true) }
                repository.authenticate(
                    username = savedCredentials.username,
                    password = savedCredentials.password
                ).collect { result ->
                    when (result) {
                        is AuthResult.Success -> handleLoginSuccess(result.data)
                        is AuthResult.Error -> handleBiometricError()
                        is AuthResult.Loading -> _uiState.update { it.copy(isLoading = true) }
                    }
                }
            } else {
                _uiState.update { 
                    it.copy(
                        errorMessage = "保存された認証情報が見つかりません",
                        isLoading = false
                    )
                }
            }
        }
    }

    private suspend fun handleLoginSuccess(response: AuthResponse) {
        // Save token
        tokenRepository.saveToken(response.token)
        tokenRepository.saveUserId(response.userId)

        // Save credentials if remember me is checked
        if (_uiState.value.rememberMe) {
            preferencesRepository.saveCredentials(
                username = _uiState.value.username,
                password = _uiState.value.password
            )
            preferencesRepository.setRememberMe(true)
        }

        _uiState.update { 
            it.copy(
                isLoading = false,
                isAuthenticated = true,
                errorMessage = null
            )
        }
    }

    private fun handleLoginError(exception: Exception) {
        val errorMessage = when (exception) {
            is InvalidCredentialsException -> "ユーザー名またはパスワードが正しくありません"
            is NetworkException -> "ネットワークエラーが発生しました。接続を確認してください"
            is ServerException -> "サーバーエラーが発生しました。しばらくしてから再試行してください"
            else -> "予期しないエラーが発生しました"
        }

        _uiState.update { 
            it.copy(
                isLoading = false,
                errorMessage = errorMessage
            )
        }
    }

    private fun handleBiometricError() {
        _uiState.update { 
            it.copy(
                isLoading = false,
                errorMessage = "生体認証に失敗しました"
            )
        }
    }

    private fun checkBiometricAvailability() {
        val biometricManager = BiometricManager.from(context)
        
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                _uiState.update { 
                    it.copy(
                        isBiometricAvailable = true,
                        biometricType = getBiometricType()
                    )
                }
            }
            else -> {
                _uiState.update { 
                    it.copy(
                        isBiometricAvailable = false,
                        biometricType = "NONE"
                    )
                }
            }
        }
    }

    private fun getBiometricType(): String {
        // This is a simplified version. In a real app, you'd need more sophisticated detection
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val biometricManager = BiometricManager.from(context)
            if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == 
                BiometricManager.BIOMETRIC_SUCCESS) {
                // Check for face unlock capability
                if (context.packageManager.hasSystemFeature("android.hardware.biometrics.face")) {
                    "FACE_ID"
                } else {
                    "FINGERPRINT"
                }
            } else {
                "NONE"
            }
        } else {
            "FINGERPRINT"
        }
    }

    private fun loadSavedCredentials() {
        viewModelScope.launch {
            if (preferencesRepository.getRememberMe()) {
                val credentials = preferencesRepository.getSavedCredentials()
                credentials?.let {
                    _uiState.update { state ->
                        state.copy(
                            username = it.username,
                            password = it.password,
                            rememberMe = true
                        )
                    }
                }
            }
        }
    }

    fun createBiometricPrompt(activity: FragmentActivity): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(activity)
        
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    loginWithBiometrics()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    _uiState.update { 
                        it.copy(errorMessage = "生体認証エラー: $errString")
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    _uiState.update { 
                        it.copy(errorMessage = "生体認証に失敗しました")
                    }
                }
            })

        return biometricPrompt
    }

    fun getBiometricPromptInfo(): BiometricPrompt.PromptInfo {
        return BiometricPrompt.PromptInfo.Builder()
            .setTitle("Voice KoeReq")
            .setSubtitle("生体認証でログイン")
            .setNegativeButtonText("キャンセル")
            .build()
    }
}

data class F1UiState(
    val username: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val biometricType: String = "NONE"
)

// Custom exceptions
class InvalidCredentialsException : Exception()
class NetworkException : Exception()
class ServerException : Exception()