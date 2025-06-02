package com.voicekoereq.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicekoereq.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class F8UiState(
    val offlineRequests: List<MedicalRequest> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val selectedRequest: MedicalRequest? = null,
    val showDeleteConfirmation: Boolean = false,
    val requestToDelete: MedicalRequest? = null,
    val syncResult: SyncResult? = null,
    val showSyncResult: Boolean = false,
    val showAddRequest: Boolean = false,
    val isOnline: Boolean = true
) {
    val unsyncedCount: Int
        get() = offlineRequests.count { !it.isSynced }
    
    val totalCount: Int
        get() = offlineRequests.size
}

sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    data class Success(val message: String) : SyncStatus()
    data class Failure(val message: String) : SyncStatus()
}

sealed class F8UiEvent {
    object LoadRequests : F8UiEvent()
    data class SaveRequest(
        val patientName: String,
        val symptoms: String,
        val transcriptionText: String?,
        val audioFilePath: String?
    ) : F8UiEvent()
    object SyncData : F8UiEvent()
    data class DeleteRequest(val request: MedicalRequest) : F8UiEvent()
    object ConfirmDelete : F8UiEvent()
    object CancelDelete : F8UiEvent()
    object ClearAllData : F8UiEvent()
    data class SelectRequest(val request: MedicalRequest) : F8UiEvent()
    object ShowAddRequest : F8UiEvent()
    object HideAddRequest : F8UiEvent()
    object DismissError : F8UiEvent()
    object DismissSyncResult : F8UiEvent()
}

@HiltViewModel
class F8ViewModel @Inject constructor(
    private val repository: F8Repository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(F8UiState())
    val uiState: StateFlow<F8UiState> = _uiState.asStateFlow()
    
    init {
        loadOfflineRequests()
        startNetworkMonitoring()
        startAutoSync()
    }
    
    fun handleEvent(event: F8UiEvent) {
        when (event) {
            is F8UiEvent.LoadRequests -> loadOfflineRequests()
            is F8UiEvent.SaveRequest -> saveRequest(
                event.patientName,
                event.symptoms,
                event.transcriptionText,
                event.audioFilePath
            )
            is F8UiEvent.SyncData -> syncData()
            is F8UiEvent.DeleteRequest -> deleteRequest(event.request)
            is F8UiEvent.ConfirmDelete -> confirmDelete()
            is F8UiEvent.CancelDelete -> cancelDelete()
            is F8UiEvent.ClearAllData -> clearAllData()
            is F8UiEvent.SelectRequest -> selectRequest(event.request)
            is F8UiEvent.ShowAddRequest -> showAddRequest()
            is F8UiEvent.HideAddRequest -> hideAddRequest()
            is F8UiEvent.DismissError -> dismissError()
            is F8UiEvent.DismissSyncResult -> dismissSyncResult()
        }
    }
    
    private fun loadOfflineRequests() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            repository.getAllOfflineRequests()
                .catch { exception ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = exception.message ?: "データの読み込みに失敗しました"
                        )
                    }
                }
                .collect { requests ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            offlineRequests = requests
                        )
                    }
                }
        }
    }
    
    private fun saveRequest(
        patientName: String,
        symptoms: String,
        transcriptionText: String?,
        audioFilePath: String?
    ) {
        viewModelScope.launch {
            val request = MedicalRequest(
                patientName = patientName,
                symptoms = symptoms,
                transcriptionText = transcriptionText,
                audioFilePath = audioFilePath
            )
            
            _uiState.update { it.copy(isLoading = true) }
            
            repository.saveMedicalRequest(request).fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, showAddRequest = false) }
                    loadOfflineRequests()
                    
                    // Try to sync immediately if online
                    if (_uiState.value.isOnline) {
                        syncData()
                    }
                },
                onFailure = { exception ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = exception.message ?: "保存に失敗しました"
                        )
                    }
                }
            )
        }
    }
    
    private fun syncData() {
        if (_uiState.value.syncStatus is SyncStatus.Syncing) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(syncStatus = SyncStatus.Syncing) }
            
            repository.syncOfflineData().fold(
                onSuccess = { result ->
                    _uiState.update { state ->
                        state.copy(
                            syncResult = result,
                            showSyncResult = true,
                            syncStatus = if (result.failureCount == 0) {
                                SyncStatus.Success("${result.successCount}件のデータを同期しました")
                            } else {
                                SyncStatus.Failure("${result.successCount}件成功、${result.failureCount}件失敗")
                            }
                        )
                    }
                    
                    // Reload data after sync
                    loadOfflineRequests()
                    
                    // Reset status after delay
                    delay(3000)
                    _uiState.update { it.copy(syncStatus = SyncStatus.Idle) }
                },
                onFailure = { exception ->
                    _uiState.update { state ->
                        state.copy(
                            syncStatus = SyncStatus.Failure(
                                exception.message ?: "同期に失敗しました"
                            )
                        )
                    }
                    
                    delay(3000)
                    _uiState.update { it.copy(syncStatus = SyncStatus.Idle) }
                }
            )
        }
    }
    
    private fun deleteRequest(request: MedicalRequest) {
        _uiState.update { state ->
            state.copy(
                requestToDelete = request,
                showDeleteConfirmation = true
            )
        }
    }
    
    private fun confirmDelete() {
        val request = _uiState.value.requestToDelete ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            repository.deleteOfflineRequest(request.id).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            requestToDelete = null,
                            showDeleteConfirmation = false
                        )
                    }
                    loadOfflineRequests()
                },
                onFailure = { exception ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = exception.message ?: "削除に失敗しました"
                        )
                    }
                }
            )
        }
    }
    
    private fun cancelDelete() {
        _uiState.update { state ->
            state.copy(
                requestToDelete = null,
                showDeleteConfirmation = false
            )
        }
    }
    
    private fun clearAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            repository.clearAllOfflineData().fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            offlineRequests = emptyList()
                        )
                    }
                },
                onFailure = { exception ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = exception.message ?: "データクリアに失敗しました"
                        )
                    }
                }
            )
        }
    }
    
    private fun selectRequest(request: MedicalRequest) {
        _uiState.update { it.copy(selectedRequest = request) }
    }
    
    private fun showAddRequest() {
        _uiState.update { it.copy(showAddRequest = true) }
    }
    
    private fun hideAddRequest() {
        _uiState.update { it.copy(showAddRequest = false) }
    }
    
    private fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    private fun dismissSyncResult() {
        _uiState.update { it.copy(showSyncResult = false) }
    }
    
    private fun startNetworkMonitoring() {
        viewModelScope.launch {
            flow {
                while (true) {
                    emit(true) // Simplified - in real app, check actual network status
                    delay(5000)
                }
            }.collect { isOnline ->
                _uiState.update { it.copy(isOnline = isOnline) }
            }
        }
    }
    
    private fun startAutoSync() {
        viewModelScope.launch {
            flow {
                while (true) {
                    delay(30000) // Check every 30 seconds
                    emit(Unit)
                }
            }.collect {
                if (_uiState.value.isOnline && _uiState.value.unsyncedCount > 0) {
                    syncData()
                }
            }
        }
    }
    
    fun formatDate(timestamp: Long): String {
        val formatter = SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN)
        return formatter.format(Date(timestamp))
    }
}