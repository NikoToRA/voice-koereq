package com.voicekoereq.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicekoereq.repository.F2Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import kotlin.concurrent.timer

@HiltViewModel
class F2ViewModel @Inject constructor(
    private val repository: F2Repository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(F2UiState())
    val uiState: StateFlow<F2UiState> = _uiState.asStateFlow()
    
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    
    private var recordingTimer: Timer? = null
    private var recordingStartTime: Long = 0
    private var pausedDuration: Long = 0
    
    init {
        // リポジトリのオーディオレベルを購読
        viewModelScope.launch {
            repository.audioLevel.collect { level ->
                _audioLevel.value = level
            }
        }
        
        // 録音履歴の有無をチェック
        viewModelScope.launch {
            repository.hasRecordings().collect { hasRecordings ->
                _uiState.update { it.copy(hasRecordings = hasRecordings) }
            }
        }
    }
    
    fun requestMicrophonePermission(context: Context) {
        val permission = android.Manifest.permission.RECORD_AUDIO
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) {
            _uiState.update { 
                it.copy(errorMessage = "マイクの使用許可が必要です。設定から許可してください。")
            }
        }
    }
    
    suspend fun startRecording() {
        _uiState.update { it.copy(isLoading = true) }
        
        try {
            repository.startRecording()
            recordingStartTime = System.currentTimeMillis()
            pausedDuration = 0
            startRecordingTimer()
            
            _uiState.update { 
                it.copy(
                    recordingState = RecordingState.RECORDING,
                    isLoading = false,
                    statusText = "録音中"
                )
            }
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    errorMessage = "録音を開始できませんでした: ${e.message}"
                )
            }
        }
    }
    
    fun pauseRecording() {
        repository.pauseRecording()
        recordingTimer?.cancel()
        pausedDuration += System.currentTimeMillis() - recordingStartTime
        
        _uiState.update { 
            it.copy(
                recordingState = RecordingState.PAUSED,
                statusText = "一時停止中"
            )
        }
    }
    
    fun resumeRecording() {
        repository.resumeRecording()
        recordingStartTime = System.currentTimeMillis()
        startRecordingTimer()
        
        _uiState.update { 
            it.copy(
                recordingState = RecordingState.RECORDING,
                statusText = "録音中"
            )
        }
    }
    
    suspend fun stopRecording() {
        _uiState.update { it.copy(isLoading = true) }
        recordingTimer?.cancel()
        
        try {
            val recordingFile = repository.stopRecording()
            
            _uiState.update { 
                it.copy(
                    recordingState = RecordingState.IDLE,
                    isLoading = false,
                    statusText = "録音完了",
                    recordingTime = 0,
                    hasRecordings = true
                )
            }
            
            // 録音ファイルを処理（Azure Blob Storageへのアップロードなど）
            processRecording(recordingFile)
            
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    errorMessage = "録音の保存に失敗しました: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    private fun startRecordingTimer() {
        recordingTimer = timer(period = 100) {
            val currentTime = System.currentTimeMillis()
            val elapsedTime = (currentTime - recordingStartTime + pausedDuration) / 1000
            
            _uiState.update { 
                it.copy(recordingTime = elapsedTime)
            }
        }
    }
    
    private suspend fun processRecording(filePath: String) {
        // 録音ファイルの後処理
        // 例: Azure Blob Storageへアップロード、音声認識処理など
        viewModelScope.launch {
            try {
                repository.uploadRecording(filePath)
                // 必要に応じて音声認識を実行
                // val transcription = repository.transcribeAudio(filePath)
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        errorMessage = "録音のアップロードに失敗しました: ${e.message}"
                    )
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        recordingTimer?.cancel()
        repository.cleanup()
    }
}

// UI状態データクラス
data class F2UiState(
    val recordingState: RecordingState = RecordingState.IDLE,
    val isLoading: Boolean = false,
    val recordingTime: Long = 0,
    val hasRecordings: Boolean = false,
    val statusText: String = "準備完了",
    val errorMessage: String? = null
) {
    val recordingTimeText: String
        get() {
            val minutes = recordingTime / 60
            val seconds = recordingTime % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}

// 録音状態
enum class RecordingState {
    IDLE,      // 待機中
    RECORDING, // 録音中
    PAUSED     // 一時停止中
}