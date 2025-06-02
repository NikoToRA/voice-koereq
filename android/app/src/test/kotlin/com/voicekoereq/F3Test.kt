package com.voicekoereq

import android.net.Uri
import com.voicekoereq.repository.F3Repository
import com.voicekoereq.repository.TranscriptionError
import com.voicekoereq.viewmodel.F3ViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class F3Test {
    private lateinit var viewModel: F3ViewModel
    private lateinit var mockRepository: F3Repository
    private lateinit var testDispatcher: TestDispatcher
    
    @Before
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        
        mockRepository = mockk(relaxed = true)
        viewModel = F3ViewModel(mockRepository)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `initial state should be correct`() = runTest {
        // Given
        every { mockRepository.transcriptionResult } returns MutableStateFlow("")
        every { mockRepository.isProcessing } returns MutableStateFlow(false)
        every { mockRepository.progress } returns MutableStateFlow(0.0)
        every { mockRepository.error } returns MutableStateFlow(null)
        
        // When
        val viewModel = F3ViewModel(mockRepository)
        
        // Then
        val state = viewModel.uiState.value
        assertEquals("", state.transcribedText)
        assertFalse(state.isTranscribing)
        assertEquals(0.0, state.progress, 0.001)
        assertNull(state.error)
        assertFalse(state.hasAudioData)
        assertNull(state.audioDuration)
    }
    
    @Test
    fun `setAudioData should update state correctly`() = runTest {
        // Given
        val mockUri = mockk<Uri>()
        val duration = 5000L
        
        // When
        viewModel.setAudioData(mockUri, duration)
        
        // Then
        val state = viewModel.uiState.value
        assertTrue(state.hasAudioData)
        assertEquals(duration, state.audioDuration)
    }
    
    @Test
    fun `startTranscription should call repository when audio data exists`() = runTest {
        // Given
        val mockUri = mockk<Uri>()
        viewModel.setAudioData(mockUri)
        
        coEvery { mockRepository.transcribeAudio(any()) } just Runs
        
        // When
        viewModel.startTranscription()
        advanceUntilIdle()
        
        // Then
        coVerify { mockRepository.transcribeAudio(mockUri) }
    }
    
    @Test
    fun `startTranscription should show error when no audio data`() = runTest {
        // When
        viewModel.startTranscription()
        advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertNotNull(state.error)
        assertEquals("音声データがありません。先に録音を行ってください。", state.error)
    }
    
    @Test
    fun `clearTranscription should reset transcription state`() = runTest {
        // Given
        val mockUri = mockk<Uri>()
        viewModel.setAudioData(mockUri)
        
        // When
        viewModel.clearTranscription()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals("", state.transcribedText)
        assertEquals(0.0, state.progress, 0.001)
        assertNull(state.error)
    }
    
    @Test
    fun `clearError should remove error from state`() = runTest {
        // Given - Set an error state
        viewModel.startTranscription()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)
        
        // When
        viewModel.clearError()
        
        // Then
        assertNull(viewModel.uiState.value.error)
    }
    
    @Test
    fun `cancelTranscription should call repository cancel`() = runTest {
        // When
        viewModel.cancelTranscription()
        
        // Then
        verify { mockRepository.cancelTranscription() }
        val state = viewModel.uiState.value
        assertFalse(state.isTranscribing)
        assertEquals(0.0, state.progress, 0.001)
    }
    
    @Test
    fun `repository transcription updates should reflect in UI state`() = runTest {
        // Given
        val transcriptionFlow = MutableStateFlow("")
        val isProcessingFlow = MutableStateFlow(false)
        val progressFlow = MutableStateFlow(0.0)
        
        every { mockRepository.transcriptionResult } returns transcriptionFlow
        every { mockRepository.isProcessing } returns isProcessingFlow
        every { mockRepository.progress } returns progressFlow
        every { mockRepository.error } returns MutableStateFlow(null)
        
        val viewModel = F3ViewModel(mockRepository)
        
        // When - Update repository states
        transcriptionFlow.value = "テスト文字起こし結果"
        isProcessingFlow.value = true
        progressFlow.value = 0.5
        
        advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals("テスト文字起こし結果", state.transcribedText)
        assertTrue(state.isTranscribing)
        assertEquals(0.5, state.progress, 0.001)
    }
    
    @Test
    fun `repository error should update UI state`() = runTest {
        // Given
        val errorFlow = MutableStateFlow<TranscriptionError?>(null)
        
        every { mockRepository.transcriptionResult } returns MutableStateFlow("")
        every { mockRepository.isProcessing } returns MutableStateFlow(false)
        every { mockRepository.progress } returns MutableStateFlow(0.0)
        every { mockRepository.error } returns errorFlow
        
        val viewModel = F3ViewModel(mockRepository)
        
        // When
        errorFlow.value = TranscriptionError.NetworkError
        advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(TranscriptionError.NetworkError.message, state.error)
    }
}