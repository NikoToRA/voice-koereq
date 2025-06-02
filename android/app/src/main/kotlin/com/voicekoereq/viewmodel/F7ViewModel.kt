package com.voicekoereq.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicekoereq.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class F7UiState(
    val summaries: List<F7Summary> = emptyList(),
    val selectedSummary: F7Summary? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.DATE_DESCENDING,
    val showGenerateDialog: Boolean = false,
    val transcriptionIdInput: String = "",
    val showDeleteDialog: Boolean = false,
    val summaryToDelete: F7Summary? = null
)

enum class SortOrder {
    DATE_ASCENDING,
    DATE_DESCENDING,
    TITLE_ASCENDING,
    TITLE_DESCENDING
}

@HiltViewModel
class F7ViewModel @Inject constructor(
    private val repository: F7RepositoryInterface
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(F7UiState())
    val uiState: StateFlow<F7UiState> = _uiState.asStateFlow()
    
    val filteredSummaries: StateFlow<List<F7Summary>> = _uiState
        .map { state ->
            val filtered = if (state.searchQuery.isEmpty()) {
                state.summaries
            } else {
                state.summaries.filter { summary ->
                    summary.title.contains(state.searchQuery, ignoreCase = true) ||
                    summary.summary.contains(state.searchQuery, ignoreCase = true) ||
                    summary.keyPoints.any { it.contains(state.searchQuery, ignoreCase = true) }
                }
            }
            
            filtered.sortedWith(
                when (state.sortOrder) {
                    SortOrder.DATE_ASCENDING -> compareBy { it.generatedAt }
                    SortOrder.DATE_DESCENDING -> compareByDescending { it.generatedAt }
                    SortOrder.TITLE_ASCENDING -> compareBy { it.title }
                    SortOrder.TITLE_DESCENDING -> compareByDescending { it.title }
                }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    init {
        loadSummaries()
    }
    
    fun loadSummaries() {
        viewModelScope.launch {
            repository.getSummaries().collect { result ->
                when (result) {
                    is F7Result.Loading -> {
                        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    }
                    is F7Result.Success -> {
                        _uiState.update { 
                            it.copy(
                                summaries = result.data,
                                isLoading = false,
                                errorMessage = null
                            )
                        }
                    }
                    is F7Result.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                errorMessage = result.exception.message
                            )
                        }
                    }
                }
            }
        }
    }
    
    fun generateSummary() {
        val transcriptionId = _uiState.value.transcriptionIdInput
        if (transcriptionId.isBlank()) {
            _uiState.update { 
                it.copy(errorMessage = "文字起こしIDを入力してください")
            }
            return
        }
        
        viewModelScope.launch {
            repository.generateSummary(transcriptionId).collect { result ->
                when (result) {
                    is F7Result.Loading -> {
                        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    }
                    is F7Result.Success -> {
                        _uiState.update { state ->
                            state.copy(
                                summaries = listOf(result.data) + state.summaries,
                                selectedSummary = result.data,
                                isLoading = false,
                                errorMessage = null,
                                showGenerateDialog = false,
                                transcriptionIdInput = ""
                            )
                        }
                    }
                    is F7Result.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                errorMessage = result.exception.message
                            )
                        }
                    }
                }
            }
        }
    }
    
    fun selectSummary(summary: F7Summary) {
        _uiState.update { it.copy(selectedSummary = summary) }
    }
    
    fun clearSelectedSummary() {
        _uiState.update { it.copy(selectedSummary = null) }
    }
    
    fun deleteSummary() {
        val summaryToDelete = _uiState.value.summaryToDelete ?: return
        
        viewModelScope.launch {
            repository.deleteSummary(summaryToDelete.id).collect { result ->
                when (result) {
                    is F7Result.Loading -> {
                        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    }
                    is F7Result.Success -> {
                        _uiState.update { state ->
                            state.copy(
                                summaries = state.summaries.filter { it.id != summaryToDelete.id },
                                selectedSummary = if (state.selectedSummary?.id == summaryToDelete.id) null else state.selectedSummary,
                                isLoading = false,
                                errorMessage = null,
                                showDeleteDialog = false,
                                summaryToDelete = null
                            )
                        }
                    }
                    is F7Result.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                errorMessage = result.exception.message,
                                showDeleteDialog = false,
                                summaryToDelete = null
                            )
                        }
                    }
                }
            }
        }
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }
    
    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
    }
    
    fun changeSortOrder(order: SortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
    }
    
    fun showGenerateDialog() {
        _uiState.update { it.copy(showGenerateDialog = true) }
    }
    
    fun hideGenerateDialog() {
        _uiState.update { it.copy(showGenerateDialog = false, transcriptionIdInput = "") }
    }
    
    fun updateTranscriptionIdInput(input: String) {
        _uiState.update { it.copy(transcriptionIdInput = input) }
    }
    
    fun showDeleteDialog(summary: F7Summary) {
        _uiState.update { it.copy(showDeleteDialog = true, summaryToDelete = summary) }
    }
    
    fun hideDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false, summaryToDelete = null) }
    }
    
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    fun exportSummary(summary: F7Summary): String {
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.JAPANESE)
        
        return buildString {
            appendLine("【診察サマリー】")
            appendLine("生成日時: ${dateFormat.format(summary.generatedAt)}")
            appendLine("タイトル: ${summary.title}")
            appendLine()
            appendLine("【サマリー】")
            appendLine(summary.summary)
            appendLine()
            appendLine("【要点】")
            summary.keyPoints.forEachIndexed { index, point ->
                appendLine("${index + 1}. $point")
            }
        }
    }
}