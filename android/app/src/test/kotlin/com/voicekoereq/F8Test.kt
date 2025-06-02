package com.voicekoereq

import com.voicekoereq.repository.*
import com.voicekoereq.viewmodel.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import java.util.*

@ExperimentalCoroutinesApi
class F8Test {
    @get:Rule
    val testDispatcherRule = TestDispatcherRule()
    
    private lateinit var mockRepository: MockF8Repository
    private lateinit var viewModel: F8ViewModel
    private val testScope = TestScope(UnconfinedTestDispatcher())
    
    @Before
    fun setup() {
        mockRepository = MockF8Repository()
        viewModel = F8ViewModel(mockRepository)
    }
    
    @After
    fun tearDown() {
        // Clean up
    }
    
    // Repository Tests
    
    @Test
    fun `test getAllOfflineRequests returns mock data`() = testScope.runTest {
        val requests = mockRepository.getAllOfflineRequests().first()
        
        assertEquals(2, requests.size)
        assertEquals("テスト患者", requests[0].patientName)
        assertEquals("頭痛と発熱", requests[0].symptoms)
        assertEquals("サンプル患者", requests[1].patientName)
        assertEquals("咳と喉の痛み", requests[1].symptoms)
        assertFalse(requests[0].isSynced)
        assertTrue(requests[1].isSynced)
    }
    
    @Test
    fun `test saveMedicalRequest returns success`() = testScope.runTest {
        val request = MedicalRequest(
            patientName = "新規患者",
            symptoms = "腹痛",
            transcriptionText = "昨日から腹痛が続いています"
        )
        
        val result = mockRepository.saveMedicalRequest(request)
        
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
    }
    
    @Test
    fun `test syncOfflineData returns success result`() = testScope.runTest {
        val result = mockRepository.syncOfflineData()
        
        assertTrue(result.isSuccess)
        val syncResult = result.getOrNull()!!
        assertEquals(2, syncResult.successCount)
        assertEquals(0, syncResult.failureCount)
        assertTrue(syncResult.conflicts.isEmpty())
    }
    
    @Test
    fun `test deleteOfflineRequest returns success`() = testScope.runTest {
        val requestId = UUID.randomUUID().toString()
        val result = mockRepository.deleteOfflineRequest(requestId)
        
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
    }
    
    @Test
    fun `test clearAllOfflineData returns success`() = testScope.runTest {
        val result = mockRepository.clearAllOfflineData()
        
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
    }
    
    // ViewModel Tests
    
    @Test
    fun `test initial UI state`() = testScope.runTest {
        val state = viewModel.uiState.value
        
        assertEquals(0, state.offlineRequests.size) // Will be loaded async
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(SyncStatus.Idle, state.syncStatus)
        assertNull(state.selectedRequest)
        assertFalse(state.showDeleteConfirmation)
        assertNull(state.requestToDelete)
        assertNull(state.syncResult)
        assertFalse(state.showSyncResult)
        assertFalse(state.showAddRequest)
        assertTrue(state.isOnline)
    }
    
    @Test
    fun `test load requests event updates UI state`() = testScope.runTest {
        viewModel.handleEvent(F8UiEvent.LoadRequests)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(2, state.offlineRequests.size)
        assertEquals(1, state.unsyncedCount)
        assertEquals(2, state.totalCount)
    }
    
    @Test
    fun `test save request event`() = testScope.runTest {
        viewModel.handleEvent(
            F8UiEvent.SaveRequest(
                patientName = "新規患者",
                symptoms = "頭痛",
                transcriptionText = "朝から頭痛がします",
                audioFilePath = null
            )
        )
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.showAddRequest)
    }
    
    @Test
    fun `test sync data event`() = testScope.runTest {
        viewModel.handleEvent(F8UiEvent.SyncData)
        
        // Check immediate state
        assertEquals(SyncStatus.Syncing, viewModel.uiState.value.syncStatus)
        
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue(state.showSyncResult)
        assertNotNull(state.syncResult)
        assertEquals(2, state.syncResult?.successCount)
        assertEquals(0, state.syncResult?.failureCount)
    }
    
    @Test
    fun `test delete request flow`() = testScope.runTest {
        // First load requests
        viewModel.handleEvent(F8UiEvent.LoadRequests)
        advanceUntilIdle()
        
        val request = viewModel.uiState.value.offlineRequests.first()
        
        // Trigger delete
        viewModel.handleEvent(F8UiEvent.DeleteRequest(request))
        
        var state = viewModel.uiState.value
        assertTrue(state.showDeleteConfirmation)
        assertEquals(request, state.requestToDelete)
        
        // Confirm delete
        viewModel.handleEvent(F8UiEvent.ConfirmDelete)
        advanceUntilIdle()
        
        state = viewModel.uiState.value
        assertFalse(state.showDeleteConfirmation)
        assertNull(state.requestToDelete)
    }
    
    @Test
    fun `test cancel delete`() = testScope.runTest {
        val request = MedicalRequest(
            patientName = "患者",
            symptoms = "症状"
        )
        
        viewModel.handleEvent(F8UiEvent.DeleteRequest(request))
        assertTrue(viewModel.uiState.value.showDeleteConfirmation)
        
        viewModel.handleEvent(F8UiEvent.CancelDelete)
        
        val state = viewModel.uiState.value
        assertFalse(state.showDeleteConfirmation)
        assertNull(state.requestToDelete)
    }
    
    @Test
    fun `test clear all data event`() = testScope.runTest {
        viewModel.handleEvent(F8UiEvent.ClearAllData)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(0, state.offlineRequests.size)
    }
    
    @Test
    fun `test select request event`() = testScope.runTest {
        val request = MedicalRequest(
            patientName = "選択患者",
            symptoms = "選択症状"
        )
        
        viewModel.handleEvent(F8UiEvent.SelectRequest(request))
        
        assertEquals(request, viewModel.uiState.value.selectedRequest)
    }
    
    @Test
    fun `test show and hide add request dialog`() = testScope.runTest {
        assertFalse(viewModel.uiState.value.showAddRequest)
        
        viewModel.handleEvent(F8UiEvent.ShowAddRequest)
        assertTrue(viewModel.uiState.value.showAddRequest)
        
        viewModel.handleEvent(F8UiEvent.HideAddRequest)
        assertFalse(viewModel.uiState.value.showAddRequest)
    }
    
    @Test
    fun `test dismiss error`() = testScope.runTest {
        // Simulate an error state
        viewModel.handleEvent(F8UiEvent.LoadRequests)
        // Would need to mock error in real test
        
        viewModel.handleEvent(F8UiEvent.DismissError)
        assertNull(viewModel.uiState.value.errorMessage)
    }
    
    @Test
    fun `test dismiss sync result`() = testScope.runTest {
        viewModel.handleEvent(F8UiEvent.SyncData)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.showSyncResult)
        
        viewModel.handleEvent(F8UiEvent.DismissSyncResult)
        assertFalse(viewModel.uiState.value.showSyncResult)
    }
    
    @Test
    fun `test format date`() {
        val timestamp = 1609459200000L // 2021-01-01 00:00:00 UTC
        val formatted = viewModel.formatDate(timestamp)
        
        assertTrue(formatted.isNotEmpty())
        // Date format depends on locale, but should contain date parts
        assertTrue(formatted.contains("01") || formatted.contains("1"))
    }
    
    // Model Tests
    
    @Test
    fun `test MedicalRequest model defaults`() {
        val request = MedicalRequest(
            patientName = "患者名",
            symptoms = "症状"
        )
        
        assertNotNull(request.id)
        assertEquals("患者名", request.patientName)
        assertEquals("症状", request.symptoms)
        assertTrue(request.timestamp > 0)
        assertNull(request.transcriptionText)
        assertNull(request.audioFilePath)
        assertFalse(request.isSynced)
        assertEquals(request.timestamp, request.lastModified)
    }
    
    @Test
    fun `test SyncResult model`() {
        val localRequest = MedicalRequest(
            patientName = "ローカル患者",
            symptoms = "ローカル症状"
        )
        val serverRequest = MedicalRequest(
            patientName = "サーバー患者",
            symptoms = "サーバー症状"
        )
        
        val conflict = SyncConflict(
            localData = localRequest,
            serverData = serverRequest,
            resolution = ConflictResolution.USE_SERVER
        )
        
        val result = SyncResult(
            successCount = 10,
            failureCount = 2,
            conflicts = listOf(conflict)
        )
        
        assertEquals(10, result.successCount)
        assertEquals(2, result.failureCount)
        assertEquals(1, result.conflicts.size)
        assertEquals(ConflictResolution.USE_SERVER, result.conflicts[0].resolution)
    }
    
    @Test
    fun `test F8RepositoryError types`() {
        val persistenceError = F8RepositoryError.PersistenceError("Test error")
        assertEquals("Test error", persistenceError.message)
        
        val syncError = F8RepositoryError.SyncError("Sync failed")
        assertEquals("Sync failed", syncError.message)
        
        assertTrue(F8RepositoryError.NetworkUnavailable is F8RepositoryError)
        assertTrue(F8RepositoryError.DataCorruption is F8RepositoryError)
    }
    
    @Test
    fun `test ConflictResolution enum values`() {
        val resolutions = ConflictResolution.values()
        assertEquals(3, resolutions.size)
        assertTrue(resolutions.contains(ConflictResolution.USE_LOCAL))
        assertTrue(resolutions.contains(ConflictResolution.USE_SERVER))
        assertTrue(resolutions.contains(ConflictResolution.MERGE))
    }
    
    @Test
    fun `test SyncStatus sealed class types`() {
        val idle = SyncStatus.Idle
        val syncing = SyncStatus.Syncing
        val success = SyncStatus.Success("成功メッセージ")
        val failure = SyncStatus.Failure("失敗メッセージ")
        
        assertTrue(idle is SyncStatus)
        assertTrue(syncing is SyncStatus)
        assertTrue(success is SyncStatus)
        assertTrue(failure is SyncStatus)
        
        assertEquals("成功メッセージ", success.message)
        assertEquals("失敗メッセージ", failure.message)
    }
}

// Test Dispatcher Rule for coroutines testing
@ExperimentalCoroutinesApi
class TestDispatcherRule : TestRule {
    override fun apply(base: org.junit.runners.model.Statement, description: org.junit.runner.Description) =
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                Dispatchers.setMain(UnconfinedTestDispatcher())
                try {
                    base.evaluate()
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
}