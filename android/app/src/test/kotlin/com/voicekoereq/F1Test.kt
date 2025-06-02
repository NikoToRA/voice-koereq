package com.voicekoereq

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.voicekoereq.data.model.AuthResponse
import com.voicekoereq.data.model.AuthResult
import com.voicekoereq.data.model.Credentials
import com.voicekoereq.data.repository.*
import com.voicekoereq.viewmodel.F1ViewModel
import com.voicekoereq.viewmodel.InvalidCredentialsException
import com.voicekoereq.viewmodel.NetworkException
import com.voicekoereq.viewmodel.ServerException
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class F1ViewModelTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var viewModel: F1ViewModel
    private lateinit var repository: F1Repository
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var tokenRepository: TokenRepository
    private lateinit var context: Context
    
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        repository = mockk()
        preferencesRepository = mockk()
        tokenRepository = mockk()
        context = mockk()
        
        // Mock biometric availability check
        every { context.getSystemService(any()) } returns mockk()
        every { context.packageManager } returns mockk {
            every { hasSystemFeature(any()) } returns false
        }
        
        // Default mocks
        coEvery { preferencesRepository.getRememberMe() } returns false
        coEvery { preferencesRepository.getSavedCredentials() } returns null
        
        viewModel = F1ViewModel(
            repository = repository,
            preferencesRepository = preferencesRepository,
            tokenRepository = tokenRepository,
            context = context
        )
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `test initial state`() {
        val state = viewModel.uiState.value
        
        assertEquals("", state.username)
        assertEquals("", state.password)
        assertFalse(state.rememberMe)
        assertFalse(state.isPasswordVisible)
        assertFalse(state.isLoading)
        assertEquals(null, state.errorMessage)
        assertFalse(state.isAuthenticated)
    }
    
    @Test
    fun `test form validation with empty fields`() {
        assertFalse(viewModel.isFormValid())
    }
    
    @Test
    fun `test form validation with only username`() {
        viewModel.updateUsername("testuser")
        assertFalse(viewModel.isFormValid())
    }
    
    @Test
    fun `test form validation with only password`() {
        viewModel.updatePassword("password123")
        assertFalse(viewModel.isFormValid())
    }
    
    @Test
    fun `test form validation with both fields`() {
        viewModel.updateUsername("testuser")
        viewModel.updatePassword("password123")
        assertTrue(viewModel.isFormValid())
    }
    
    @Test
    fun `test successful login`() = runTest {
        val authResponse = AuthResponse(
            token = "test-token",
            userId = "test-user-id",
            userName = "テストユーザー",
            expiresIn = 3600
        )
        
        coEvery { 
            repository.authenticate("demo", "demo123") 
        } returns flowOf(AuthResult.Success(authResponse))
        
        coEvery { tokenRepository.saveToken(any()) } just Runs
        coEvery { tokenRepository.saveUserId(any()) } just Runs
        
        viewModel.updateUsername("demo")
        viewModel.updatePassword("demo123")
        viewModel.login()
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue(state.isAuthenticated)
        assertFalse(state.isLoading)
        assertEquals(null, state.errorMessage)
        
        coVerify { tokenRepository.saveToken("test-token") }
        coVerify { tokenRepository.saveUserId("test-user-id") }
    }
    
    @Test
    fun `test login with invalid credentials`() = runTest {
        coEvery { 
            repository.authenticate("wrong", "wrong") 
        } returns flowOf(AuthResult.Error(InvalidCredentialsException()))
        
        viewModel.updateUsername("wrong")
        viewModel.updatePassword("wrong")
        viewModel.login()
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertFalse(state.isAuthenticated)
        assertFalse(state.isLoading)
        assertEquals("ユーザー名またはパスワードが正しくありません", state.errorMessage)
    }
    
    @Test
    fun `test login with network error`() = runTest {
        coEvery { 
            repository.authenticate(any(), any()) 
        } returns flowOf(AuthResult.Error(NetworkException()))
        
        viewModel.updateUsername("test")
        viewModel.updatePassword("test123")
        viewModel.login()
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertFalse(state.isAuthenticated)
        assertFalse(state.isLoading)
        assertEquals("ネットワークエラーが発生しました。接続を確認してください", state.errorMessage)
    }
    
    @Test
    fun `test login with server error`() = runTest {
        coEvery { 
            repository.authenticate(any(), any()) 
        } returns flowOf(AuthResult.Error(ServerException()))
        
        viewModel.updateUsername("test")
        viewModel.updatePassword("test123")
        viewModel.login()
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertFalse(state.isAuthenticated)
        assertFalse(state.isLoading)
        assertEquals("サーバーエラーが発生しました。しばらくしてから再試行してください", state.errorMessage)
    }
    
    @Test
    fun `test remember me saves credentials`() = runTest {
        val authResponse = AuthResponse(
            token = "test-token",
            userId = "test-user-id",
            userName = "テストユーザー",
            expiresIn = 3600
        )
        
        coEvery { 
            repository.authenticate("demo", "demo123") 
        } returns flowOf(AuthResult.Success(authResponse))
        
        coEvery { tokenRepository.saveToken(any()) } just Runs
        coEvery { tokenRepository.saveUserId(any()) } just Runs
        coEvery { preferencesRepository.saveCredentials(any(), any()) } just Runs
        coEvery { preferencesRepository.setRememberMe(any()) } just Runs
        
        viewModel.updateUsername("demo")
        viewModel.updatePassword("demo123")
        viewModel.updateRememberMe(true)
        viewModel.login()
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { preferencesRepository.saveCredentials("demo", "demo123") }
        coVerify { preferencesRepository.setRememberMe(true) }
    }
    
    @Test
    fun `test loading saved credentials`() = runTest {
        val savedCredentials = Credentials("saveduser", "savedpass")
        
        coEvery { preferencesRepository.getRememberMe() } returns true
        coEvery { preferencesRepository.getSavedCredentials() } returns savedCredentials
        
        // Create new viewModel to trigger init block
        viewModel = F1ViewModel(
            repository = repository,
            preferencesRepository = preferencesRepository,
            tokenRepository = tokenRepository,
            context = context
        )
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals("saveduser", state.username)
        assertEquals("savedpass", state.password)
        assertTrue(state.rememberMe)
    }
    
    @Test
    fun `test toggle password visibility`() {
        assertFalse(viewModel.uiState.value.isPasswordVisible)
        
        viewModel.togglePasswordVisibility()
        assertTrue(viewModel.uiState.value.isPasswordVisible)
        
        viewModel.togglePasswordVisibility()
        assertFalse(viewModel.uiState.value.isPasswordVisible)
    }
    
    @Test
    fun `test clear error`() = runTest {
        // Set an error first
        coEvery { 
            repository.authenticate(any(), any()) 
        } returns flowOf(AuthResult.Error(NetworkException()))
        
        viewModel.updateUsername("test")
        viewModel.updatePassword("test123")
        viewModel.login()
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Verify error is set
        assertEquals("ネットワークエラーが発生しました。接続を確認してください", viewModel.uiState.value.errorMessage)
        
        // Clear error
        viewModel.clearError()
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }
}

@ExperimentalCoroutinesApi
class F1RepositoryTest {
    
    private lateinit var repository: F1Repository
    private lateinit var apiService: ApiService
    
    @Before
    fun setup() {
        apiService = mockk()
        repository = F1Repository(apiService)
    }
    
    @Test
    fun `test successful authentication`() = runTest {
        val authResponse = AuthResponse(
            token = "test-token",
            userId = "test-user-id",
            userName = "テストユーザー",
            expiresIn = 3600
        )
        
        coEvery { 
            apiService.login(any()) 
        } returns authResponse
        
        val result = mutableListOf<AuthResult>()
        repository.authenticate("test", "test123").collect {
            result.add(it)
        }
        
        assertEquals(2, result.size)
        assertTrue(result[0] is AuthResult.Loading)
        assertTrue(result[1] is AuthResult.Success)
        assertEquals(authResponse, (result[1] as AuthResult.Success).data)
    }
    
    @Test
    fun `test authentication with invalid credentials`() = runTest {
        coEvery { 
            apiService.login(any()) 
        } throws retrofit2.HttpException(
            retrofit2.Response.error<Any>(401, okhttp3.ResponseBody.create(null, ""))
        )
        
        val result = mutableListOf<AuthResult>()
        repository.authenticate("wrong", "wrong").collect {
            result.add(it)
        }
        
        assertEquals(2, result.size)
        assertTrue(result[0] is AuthResult.Loading)
        assertTrue(result[1] is AuthResult.Error)
        assertTrue((result[1] as AuthResult.Error).exception is InvalidCredentialsException)
    }
    
    @Test
    fun `test authentication with network error`() = runTest {
        coEvery { 
            apiService.login(any()) 
        } throws java.io.IOException("Network error")
        
        val result = mutableListOf<AuthResult>()
        repository.authenticate("test", "test123").collect {
            result.add(it)
        }
        
        assertEquals(2, result.size)
        assertTrue(result[0] is AuthResult.Loading)
        assertTrue(result[1] is AuthResult.Error)
        assertTrue((result[1] as AuthResult.Error).exception is NetworkException)
    }
    
    @Test
    fun `test authentication with server error`() = runTest {
        coEvery { 
            apiService.login(any()) 
        } throws retrofit2.HttpException(
            retrofit2.Response.error<Any>(500, okhttp3.ResponseBody.create(null, ""))
        )
        
        val result = mutableListOf<AuthResult>()
        repository.authenticate("test", "test123").collect {
            result.add(it)
        }
        
        assertEquals(2, result.size)
        assertTrue(result[0] is AuthResult.Loading)
        assertTrue(result[1] is AuthResult.Error)
        assertTrue((result[1] as AuthResult.Error).exception is ServerException)
    }
}