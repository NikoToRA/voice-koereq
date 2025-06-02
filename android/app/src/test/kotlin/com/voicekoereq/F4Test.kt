package com.voicekoereq

import android.content.Context
import com.voicekoereq.data.AzureConfig
import com.voicekoereq.repository.F4Repository
import com.voicekoereq.viewmodel.F4ViewModel
import com.voicekoereq.viewmodel.Message
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class F4Test {
    
    @get:Rule
    val coroutineRule = TestCoroutineRule()
    
    private lateinit var repository: F4Repository
    private lateinit var viewModel: F4ViewModel
    private lateinit var mockContext: Context
    private lateinit var mockClient: OkHttpClient
    private lateinit var mockAzureConfig: AzureConfig
    
    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockClient = mockk(relaxed = true)
        mockAzureConfig = AzureConfig(
            openAIEndpoint = "https://test.openai.azure.com",
            openAIKey = "test-key",
            speechKey = "test-speech-key",
            speechRegion = "japaneast"
        )
        
        repository = spyk(F4Repository(mockContext, mockClient, mockAzureConfig))
        viewModel = F4ViewModel(repository)
    }
    
    @After
    fun tearDown() {
        unmockkAll()
    }
    
    @Test
    fun `初期メッセージが正しく表示される`() = runTest {
        // When
        val messages = viewModel.messages.first()
        
        // Then
        assertEquals(1, messages.size)
        assertFalse(messages[0].isUser)
        assertTrue(messages[0].content.contains("AI医療アシスタント"))
    }
    
    @Test
    fun `メッセージ送信が正しく動作する`() = runTest {
        // Given
        val testMessage = "頭痛がします"
        val mockResponse = "頭痛についてお聞きしました。一般的な頭痛の原因には..."
        
        coEvery { repository.getMedicalAssistantResponse(any()) } returns mockResponse
        
        // When
        viewModel.sendMessage(testMessage)
        advanceUntilIdle()
        
        val messages = viewModel.messages.first()
        
        // Then
        assertEquals(3, messages.size) // 初期メッセージ + ユーザー + AI応答
        assertEquals(testMessage, messages[1].content)
        assertTrue(messages[1].isUser)
        assertEquals(mockResponse, messages[2].content)
        assertFalse(messages[2].isUser)
    }
    
    @Test
    fun `会話クリアが正しく動作する`() = runTest {
        // Given
        viewModel.sendMessage("テストメッセージ")
        advanceUntilIdle()
        
        // When
        viewModel.clearConversation()
        
        val messages = viewModel.messages.first()
        
        // Then
        assertEquals(1, messages.size)
        assertFalse(messages[0].isUser)
    }
    
    @Test
    fun `処理中状態が正しく管理される`() = runTest {
        // Given
        coEvery { repository.getMedicalAssistantResponse(any()) } coAnswers {
            delay(100)
            "テスト応答"
        }
        
        // When
        val processingStates = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect {
                processingStates.add(it.isProcessing)
            }
        }
        
        viewModel.sendMessage("テスト")
        advanceUntilIdle()
        
        // Then
        assertTrue(processingStates.contains(true))
        assertTrue(processingStates.contains(false))
        assertFalse(viewModel.uiState.first().isProcessing)
        
        job.cancel()
    }
    
    @Test
    fun `エラー処理が正しく動作する`() = runTest {
        // Given
        val errorMessage = "ネットワークエラー"
        coEvery { repository.getMedicalAssistantResponse(any()) } throws Exception(errorMessage)
        
        // When
        viewModel.sendMessage("テスト")
        advanceUntilIdle()
        
        val uiState = viewModel.uiState.first()
        
        // Then
        assertTrue(uiState.showError)
        assertTrue(uiState.errorMessage.contains(errorMessage))
    }
    
    @Test
    fun `エラーダイアログの非表示が動作する`() = runTest {
        // Given
        coEvery { repository.getMedicalAssistantResponse(any()) } throws Exception("エラー")
        viewModel.sendMessage("テスト")
        advanceUntilIdle()
        
        // When
        viewModel.dismissError()
        
        val uiState = viewModel.uiState.first()
        
        // Then
        assertFalse(uiState.showError)
        assertEquals("", uiState.errorMessage)
    }
    
    @Test
    fun `Messageモデルが正しく作成される`() {
        // Given
        val content = "テストメッセージ"
        val isUser = true
        
        // When
        val message = Message(content = content, isUser = isUser)
        
        // Then
        assertNotNull(message.id)
        assertEquals(content, message.content)
        assertEquals(isUser, message.isUser)
        assertNotNull(message.timestamp)
    }
}

// テスト用のCoroutineルール
@OptIn(ExperimentalCoroutinesApi::class)
class TestCoroutineRule : TestWatcher() {
    val testDispatcher = UnconfinedTestDispatcher()
    
    override fun starting(description: org.junit.runner.Description) {
        Dispatchers.setMain(testDispatcher)
    }
    
    override fun finished(description: org.junit.runner.Description) {
        Dispatchers.resetMain()
    }
}

// モックリポジトリ
class MockF4Repository(
    context: Context,
    client: OkHttpClient,
    azureConfig: AzureConfig
) : F4Repository(context, client, azureConfig) {
    
    var shouldThrowError = false
    var mockResponse = "これはテスト応答です。"
    var mockTranscription = "これはテスト音声認識結果です。"
    
    override suspend fun getMedicalAssistantResponse(query: String): String {
        if (shouldThrowError) {
            throw Exception("Mock error")
        }
        return mockResponse
    }
    
    override suspend fun transcribeAudio(audioFile: File): String {
        if (shouldThrowError) {
            throw Exception("Mock transcription error")
        }
        return mockTranscription
    }
}