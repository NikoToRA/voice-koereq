package com.voicekoereq

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.voicekoereq.repository.F2Repository
import com.voicekoereq.repository.RecordingException
import com.voicekoereq.repository.RecordingFile
import com.voicekoereq.viewmodel.F2ViewModel
import com.voicekoereq.viewmodel.RecordingState
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

@ExperimentalCoroutinesApi
class F2Test {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var repository: F2Repository
    private lateinit var viewModel: F2ViewModel
    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        context = mockk(relaxed = true)
        viewModel = F2ViewModel(repository)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    // ViewModel Tests
    
    @Test
    fun `初期状態のテスト`() = runTest {
        val uiState = viewModel.uiState.value
        
        assertEquals(RecordingState.IDLE, uiState.recordingState)
        assertFalse(uiState.isLoading)
        assertEquals(0L, uiState.recordingTime)
        assertEquals("準備完了", uiState.statusText)
        assertNull(uiState.errorMessage)
    }
    
    @Test
    fun `録音時間のフォーマットテスト`() = runTest {
        val viewModel = F2ViewModel(repository)
        
        // 0秒
        var uiState = viewModel.uiState.value.copy(recordingTime = 0)
        assertEquals("00:00", uiState.recordingTimeText)
        
        // 30秒
        uiState = uiState.copy(recordingTime = 30)
        assertEquals("00:30", uiState.recordingTimeText)
        
        // 1分30秒
        uiState = uiState.copy(recordingTime = 90)
        assertEquals("01:30", uiState.recordingTimeText)
        
        // 10分
        uiState = uiState.copy(recordingTime = 600)
        assertEquals("10:00", uiState.recordingTimeText)
    }
    
    @Test
    fun `録音開始の成功テスト`() = runTest {
        coEvery { repository.startRecording() } just Runs
        
        viewModel.startRecording()
        advanceUntilIdle()
        
        val uiState = viewModel.uiState.value
        assertEquals(RecordingState.RECORDING, uiState.recordingState)
        assertFalse(uiState.isLoading)
        assertEquals("録音中", uiState.statusText)
        
        coVerify { repository.startRecording() }
    }
    
    @Test
    fun `録音開始の失敗テスト`() = runTest {
        val errorMessage = "マイクアクセスエラー"
        coEvery { repository.startRecording() } throws Exception(errorMessage)
        
        viewModel.startRecording()
        advanceUntilIdle()
        
        val uiState = viewModel.uiState.value
        assertEquals(RecordingState.IDLE, uiState.recordingState)
        assertFalse(uiState.isLoading)
        assertNotNull(uiState.errorMessage)
        assertTrue(uiState.errorMessage!!.contains(errorMessage))
    }
    
    @Test
    fun `一時停止と再開のテスト`() = runTest {
        // 録音開始
        coEvery { repository.startRecording() } just Runs
        viewModel.startRecording()
        advanceUntilIdle()
        
        // 一時停止
        viewModel.pauseRecording()
        var uiState = viewModel.uiState.value
        assertEquals(RecordingState.PAUSED, uiState.recordingState)
        assertEquals("一時停止中", uiState.statusText)
        
        // 再開
        viewModel.resumeRecording()
        uiState = viewModel.uiState.value
        assertEquals(RecordingState.RECORDING, uiState.recordingState)
        assertEquals("録音中", uiState.statusText)
        
        verify { 
            repository.pauseRecording()
            repository.resumeRecording()
        }
    }
    
    @Test
    fun `録音停止の成功テスト`() = runTest {
        val recordingPath = "/path/to/recording.m4a"
        coEvery { repository.stopRecording() } returns recordingPath
        coEvery { repository.uploadRecording(any()) } just Runs
        
        // 録音開始
        coEvery { repository.startRecording() } just Runs
        viewModel.startRecording()
        advanceUntilIdle()
        
        // 録音停止
        viewModel.stopRecording()
        advanceUntilIdle()
        
        val uiState = viewModel.uiState.value
        assertEquals(RecordingState.IDLE, uiState.recordingState)
        assertFalse(uiState.isLoading)
        assertEquals("録音完了", uiState.statusText)
        assertTrue(uiState.hasRecordings)
        
        coVerify { 
            repository.stopRecording()
            repository.uploadRecording(recordingPath)
        }
    }
    
    @Test
    fun `エラーメッセージのクリアテスト`() = runTest {
        // エラー状態を作成
        coEvery { repository.startRecording() } throws Exception("テストエラー")
        viewModel.startRecording()
        advanceUntilIdle()
        
        assertNotNull(viewModel.uiState.value.errorMessage)
        
        // エラーをクリア
        viewModel.clearError()
        assertNull(viewModel.uiState.value.errorMessage)
    }
    
    // Repository Tests
    
    @Test
    fun `録音ファイルの存在確認テスト`() = runTest {
        val repository = F2Repository(context)
        
        every { context.getExternalFilesDir(null) } returns mockk {
            every { listFiles(any()) } returns arrayOf(
                mockk { 
                    every { name } returns "recording_12345.m4a"
                }
            )
        }
        
        val hasRecordings = repository.hasRecordings().first()
        assertTrue(hasRecordings)
    }
    
    @Test
    fun `録音ファイルリストの取得テスト`() = runTest {
        val repository = F2Repository(context)
        val mockFile = mockk<java.io.File> {
            every { name } returns "recording_12345.m4a"
            every { absolutePath } returns "/path/recording_12345.m4a"
            every { length() } returns 1024000L
            every { lastModified() } returns 1234567890L
        }
        
        every { context.getExternalFilesDir(null) } returns mockk {
            every { listFiles(any()) } returns arrayOf(mockFile)
        }
        
        val recordings = repository.getRecordings()
        assertEquals(1, recordings.size)
        assertEquals("recording_12345.m4a", recordings[0].fileName)
        assertEquals(1024000L, recordings[0].sizeInBytes)
    }
    
    @Test
    fun `オーディオレベルの範囲テスト`() = runTest {
        val repository = F2Repository(context)
        
        // オーディオレベルは0から1の範囲内
        repository.audioLevel.collect { level ->
            assertTrue(level >= 0f)
            assertTrue(level <= 1f)
        }
    }
    
    // Error Handling Tests
    
    @Test
    fun `RecordingException のテスト`() {
        val cause = IllegalStateException("元のエラー")
        val exception = RecordingException("録音エラー", cause)
        
        assertEquals("録音エラー", exception.message)
        assertEquals(cause, exception.cause)
    }
    
    // Integration Tests
    
    @Test
    fun `録音ワークフロー全体のテスト`() = runTest {
        val recordingPath = "/path/to/recording.m4a"
        
        coEvery { repository.startRecording() } just Runs
        coEvery { repository.stopRecording() } returns recordingPath
        coEvery { repository.uploadRecording(any()) } just Runs
        every { repository.hasRecordings() } returns flowOf(true)
        
        // 録音開始
        viewModel.startRecording()
        advanceUntilIdle()
        assertEquals(RecordingState.RECORDING, viewModel.uiState.value.recordingState)
        
        // 一時停止
        viewModel.pauseRecording()
        assertEquals(RecordingState.PAUSED, viewModel.uiState.value.recordingState)
        
        // 再開
        viewModel.resumeRecording()
        assertEquals(RecordingState.RECORDING, viewModel.uiState.value.recordingState)
        
        // 停止
        viewModel.stopRecording()
        advanceUntilIdle()
        assertEquals(RecordingState.IDLE, viewModel.uiState.value.recordingState)
        
        coVerifySequence {
            repository.startRecording()
            repository.pauseRecording()
            repository.resumeRecording()
            repository.stopRecording()
            repository.uploadRecording(recordingPath)
        }
    }
    
    // Performance Tests
    
    @Test
    fun `大量の録音ファイル処理のパフォーマンステスト`() = runTest {
        val repository = F2Repository(context)
        val mockFiles = Array(1000) { index ->
            mockk<java.io.File> {
                every { name } returns "recording_$index.m4a"
                every { absolutePath } returns "/path/recording_$index.m4a"
                every { length() } returns (1000000L + index)
                every { lastModified() } returns (System.currentTimeMillis() - index * 1000)
            }
        }
        
        every { context.getExternalFilesDir(null) } returns mockk {
            every { listFiles(any()) } returns mockFiles
        }
        
        val startTime = System.currentTimeMillis()
        val recordings = repository.getRecordings()
        val endTime = System.currentTimeMillis()
        
        assertEquals(1000, recordings.size)
        assertTrue("処理時間が1秒以内", endTime - startTime < 1000)
    }
}