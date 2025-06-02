package com.voicekoereq

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.voicekoereq.repository.*
import com.voicekoereq.viewmodel.F7ViewModel
import com.voicekoereq.viewmodel.SortOrder
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class F7Test {
    
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var repository: F7RepositoryInterface
    private lateinit var viewModel: F7ViewModel
    private lateinit var testDispatcher: TestDispatcher
    
    private val mockSummaries = listOf(
        F7Summary(
            id = "1",
            transcriptionId = "trans-1",
            title = "初診患者の症状相談",
            summary = "患者は頭痛と軽度の発熱を訴えています。",
            keyPoints = listOf("頭痛", "発熱"),
            generatedAt = Date(1000),
            language = "ja"
        ),
        F7Summary(
            id = "2",
            transcriptionId = "trans-2",
            title = "経過観察の相談",
            summary = "症状は改善傾向にある。",
            keyPoints = listOf("改善"),
            generatedAt = Date(2000),
            language = "ja"
        )
    )
    
    @Before
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `loadSummaries success updates UI state correctly`() = runTest {
        // Given
        every { repository.getSummaries() } returns flowOf(
            F7Result.Loading,
            F7Result.Success(mockSummaries)
        )
        
        // When
        viewModel = F7ViewModel(repository)
        advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.summaries.size)
        assertNull(state.errorMessage)
        
        verify { repository.getSummaries() }
    }
    
    @Test
    fun `loadSummaries error updates UI state with error message`() = runTest {
        // Given
        val errorMessage = "ネットワークエラー"
        every { repository.getSummaries() } returns flowOf(
            F7Result.Loading,
            F7Result.Error(F7Exception.NetworkError(Exception(errorMessage)))
        )
        
        // When
        viewModel = F7ViewModel(repository)
        advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.summaries.isEmpty())
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains(errorMessage))
    }
    
    @Test
    fun `generateSummary success adds new summary and selects it`() = runTest {
        // Given
        val transcriptionId = "trans-new"
        val newSummary = F7Summary(
            id = "new",
            transcriptionId = transcriptionId,
            title = "新しいサマリー",
            summary = "テストサマリー",
            keyPoints = listOf("ポイント1"),
            generatedAt = Date(),
            language = "ja"
        )
        
        every { repository.getSummaries() } returns flowOf(F7Result.Success(emptyList()))
        every { repository.generateSummary(transcriptionId) } returns flowOf(
            F7Result.Loading,
            F7Result.Success(newSummary)
        )
        
        // When
        viewModel = F7ViewModel(repository)
        advanceUntilIdle()
        
        viewModel.updateTranscriptionIdInput(transcriptionId)
        viewModel.generateSummary()
        advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.summaries.size)
        assertEquals(newSummary, state.summaries.first())
        assertEquals(newSummary, state.selectedSummary)
        assertFalse(state.showGenerateDialog)
        assertTrue(state.transcriptionIdInput.isEmpty())
    }
    
    @Test
    fun `generateSummary with blank transcriptionId shows error`() = runTest {
        // Given
        every { repository.getSummaries() } returns flowOf(F7Result.Success(emptyList()))
        
        // When
        viewModel = F7ViewModel(repository)
        advanceUntilIdle()
        
        viewModel.updateTranscriptionIdInput("")
        viewModel.generateSummary()
        advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("文字起こしIDを入力してください"))
        
        verify(exactly = 0) { repository.generateSummary(any()) }
    }
    
    @Test
    fun `deleteSummary success removes summary from list`() = runTest {
        // Given
        val summaryToDelete = mockSummaries.first()
        every { repository.getSummaries() } returns flowOf(F7Result.Success(mockSummaries))
        every { repository.deleteSummary(summaryToDelete.id) } returns flowOf(
            F7Result.Loading,
            F7Result.Success(Unit)
        )
        
        // When
        viewModel = F7ViewModel(repository)
        advanceUntilIdle()
        
        viewModel.showDeleteDialog(summaryToDelete)
        viewModel.deleteSummary()
        advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.summaries.size)
        assertFalse(state.summaries.contains(summaryToDelete))
        assertFalse(state.showDeleteDialog)
        assertNull(state.summaryToDelete)
    }
    
    @Test
    fun `filteredSummaries filters by search query`() = runTest {
        // Given
        every { repository.getSummaries() } returns flowOf(F7Result.Success(mockSummaries))
        
        // When
        viewModel = F7ViewModel(repository)
        advanceUntilIdle()
        
        // Test filter by title
        viewModel.updateSearchQuery("初診")
        val filtered1 = viewModel.filteredSummaries.first()
        assertEquals(1, filtered1.size)
        assertEquals("初診患者の症状相談", filtered1.first().title)
        
        // Test filter by summary content
        viewModel.updateSearchQuery("改善")
        val filtered2 = viewModel.filteredSummaries.first()
        assertEquals(1, filtered2.size)
        assertEquals("経過観察の相談", filtered2.first().title)
        
        // Test no results
        viewModel.updateSearchQuery("存在しない")
        val filtered3 = viewModel.filteredSummaries.first()
        assertTrue(filtered3.isEmpty())
        
        // Test clear search
        viewModel.clearSearch()
        val filtered4 = viewModel.filteredSummaries.first()
        assertEquals(2, filtered4.size)
    }
    
    @Test
    fun `sortOrder changes sort summaries correctly`() = runTest {
        // Given
        every { repository.getSummaries() } returns flowOf(F7Result.Success(mockSummaries))
        
        // When
        viewModel = F7ViewModel(repository)
        advanceUntilIdle()
        
        // Test date descending (default)
        viewModel.changeSortOrder(SortOrder.DATE_DESCENDING)
        val sorted1 = viewModel.filteredSummaries.first()
        assertTrue(sorted1[0].generatedAt > sorted1[1].generatedAt)
        
        // Test date ascending
        viewModel.changeSortOrder(SortOrder.DATE_ASCENDING)
        val sorted2 = viewModel.filteredSummaries.first()
        assertTrue(sorted2[0].generatedAt < sorted2[1].generatedAt)
        
        // Test title ascending
        viewModel.changeSortOrder(SortOrder.TITLE_ASCENDING)
        val sorted3 = viewModel.filteredSummaries.first()
        assertTrue(sorted3[0].title < sorted3[1].title)
        
        // Test title descending
        viewModel.changeSortOrder(SortOrder.TITLE_DESCENDING)
        val sorted4 = viewModel.filteredSummaries.first()
        assertTrue(sorted4[0].title > sorted4[1].title)
    }
    
    @Test
    fun `exportSummary formats summary correctly`() = runTest {
        // Given
        every { repository.getSummaries() } returns flowOf(F7Result.Success(emptyList()))
        
        // When
        viewModel = F7ViewModel(repository)
        advanceUntilIdle()
        
        val exportText = viewModel.exportSummary(mockSummaries.first())
        
        // Then
        assertTrue(exportText.contains("【診察サマリー】"))
        assertTrue(exportText.contains("初診患者の症状相談"))
        assertTrue(exportText.contains("患者は頭痛と軽度の発熱を訴えています。"))
        assertTrue(exportText.contains("【要点】"))
        assertTrue(exportText.contains("1. 頭痛"))
        assertTrue(exportText.contains("2. 発熱"))
    }
    
    @Test
    fun `dialog state management works correctly`() = runTest {
        // Given
        every { repository.getSummaries() } returns flowOf(F7Result.Success(mockSummaries))
        
        // When
        viewModel = F7ViewModel(repository)
        advanceUntilIdle()
        
        // Test generate dialog
        viewModel.showGenerateDialog()
        assertTrue(viewModel.uiState.value.showGenerateDialog)
        
        viewModel.hideGenerateDialog()
        assertFalse(viewModel.uiState.value.showGenerateDialog)
        
        // Test delete dialog
        val summary = mockSummaries.first()
        viewModel.showDeleteDialog(summary)
        assertTrue(viewModel.uiState.value.showDeleteDialog)
        assertEquals(summary, viewModel.uiState.value.summaryToDelete)
        
        viewModel.hideDeleteDialog()
        assertFalse(viewModel.uiState.value.showDeleteDialog)
        assertNull(viewModel.uiState.value.summaryToDelete)
    }
    
    @Test
    fun `MockF7Repository behaves correctly`() = runTest {
        // Given
        val mockRepo = MockF7Repository()
        
        // Test get summaries
        val summariesResult = mockRepo.getSummaries().first { it !is F7Result.Loading }
        assertTrue(summariesResult is F7Result.Success)
        assertEquals(2, (summariesResult as F7Result.Success).data.size)
        
        // Test generate summary
        val generateResult = mockRepo.generateSummary("test-trans").first { it !is F7Result.Loading }
        assertTrue(generateResult is F7Result.Success)
        
        // Test error cases
        mockRepo.shouldFail = true
        
        val errorResult = mockRepo.getSummaries().first { it !is F7Result.Loading }
        assertTrue(errorResult is F7Result.Error)
        
        // Test invalid transcription ID
        mockRepo.shouldFail = false
        val invalidResult = mockRepo.generateSummary("").first { it !is F7Result.Loading }
        assertTrue(invalidResult is F7Result.Error)
        assertEquals(F7Exception.InvalidTranscriptionId, (invalidResult as F7Result.Error).exception)
    }
}