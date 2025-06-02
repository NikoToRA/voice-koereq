package com.voicekoereq.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicekoereq.repository.F3Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class F3ViewModel @Inject constructor(
    private val repository: F3Repository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(F3UiState())
    val uiState: StateFlow<F3UiState> = _uiState.asStateFlow()
    
    private var audioUri: Uri? = null
    
    init {
        // Observe repository state
        viewModelScope.launch {
            repository.transcriptionResult.collect { result ->
                _uiState.update { it.copy(transcribedText = result) }
            }
        }
        
        viewModelScope.launch {
            repository.isProcessing.collect { isProcessing ->
                _uiState.update { it.copy(isTranscribing = isProcessing) }
            }
        }
        
        viewModelScope.launch {
            repository.progress.collect { progress ->
                _uiState.update { it.copy(progress = progress) }
            }
        }
        
        viewModelScope.launch {
            repository.error.collect { error ->
                error?.let {
                    _uiState.update { it.copy(error = error.message) }
                }
            }
        }
    }
    
    fun setAudioData(uri: Uri, duration: Long? = null) {
        audioUri = uri
        _uiState.update { 
            it.copy(
                hasAudioData = true,
                audioDuration = duration
            )
        }
    }
    
    suspend fun startTranscription() {
        val uri = audioUri
        if (uri == null) {
            _uiState.update { 
                it.copy(error = "音声データがありません。先に録音を行ってください。")
            }
            return
        }
        
        _uiState.update { 
            it.copy(
                error = null,
                transcribedText = "",
                progress = 0.0
            )
        }
        
        repository.transcribeAudio(uri)
    }
    
    fun clearTranscription() {
        _uiState.update { 
            it.copy(
                transcribedText = "",
                progress = 0.0,
                error = null
            )
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    fun cancelTranscription() {
        repository.cancelTranscription()
        _uiState.update { 
            it.copy(
                isTranscribing = false,
                progress = 0.0
            )
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        cancelTranscription()
    }
}

data class F3UiState(
    val transcribedText: String = "",
    val isTranscribing: Boolean = false,
    val progress: Double = 0.0,
    val error: String? = null,
    val hasAudioData: Boolean = false,
    val audioDuration: Long? = null
)