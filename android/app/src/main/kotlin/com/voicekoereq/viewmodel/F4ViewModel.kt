package com.voicekoereq.viewmodel

import android.media.MediaRecorder
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicekoereq.repository.F4Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import javax.inject.Inject

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Date = Date()
)

data class F4UiState(
    val isProcessing: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: String = ""
)

@HiltViewModel
class F4ViewModel @Inject constructor(
    private val repository: F4Repository
) : ViewModel() {
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _uiState = MutableStateFlow(F4UiState())
    val uiState: StateFlow<F4UiState> = _uiState.asStateFlow()
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    
    init {
        // 初期メッセージを追加
        val welcomeMessage = Message(
            content = "こんにちは！AI医療アシスタントです。どのような症状や健康に関する質問がありますか？お気軽にお聞きください。",
            isUser = false
        )
        _messages.value = listOf(welcomeMessage)
    }
    
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            // ユーザーメッセージを追加
            val userMessage = Message(content = text, isUser = true)
            _messages.value = _messages.value + userMessage
            
            _uiState.update { it.copy(isProcessing = true) }
            
            try {
                // AI応答を取得
                val response = repository.getMedicalAssistantResponse(text)
                
                // AI応答メッセージを追加
                val assistantMessage = Message(content = response, isUser = false)
                _messages.value = _messages.value + assistantMessage
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showError = true,
                        errorMessage = "応答の取得中にエラーが発生しました: ${e.message}"
                    )
                }
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }
    
    fun startRecording() {
        viewModelScope.launch {
            try {
                audioFile = File.createTempFile("audio_${System.currentTimeMillis()}", ".m4a")
                
                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(repository.getContext())
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128000)
                    setAudioSamplingRate(44100)
                    setOutputFile(audioFile?.absolutePath)
                    
                    prepare()
                    start()
                }
                
                _isRecording.value = true
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showError = true,
                        errorMessage = "録音の開始に失敗しました: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun stopRecording() {
        viewModelScope.launch {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                _isRecording.value = false
                
                // 録音したファイルを処理
                audioFile?.let { file ->
                    processRecordedAudio(file)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showError = true,
                        errorMessage = "録音の停止に失敗しました: ${e.message}"
                    )
                }
                _isRecording.value = false
            }
        }
    }
    
    private suspend fun processRecordedAudio(file: File) {
        _uiState.update { it.copy(isProcessing = true) }
        
        try {
            // 音声をテキストに変換
            val transcribedText = repository.transcribeAudio(file)
            
            // 変換されたテキストをメッセージとして送信
            sendMessage(transcribedText)
            
            // 一時ファイルを削除
            file.delete()
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    showError = true,
                    errorMessage = "音声の処理中にエラーが発生しました: ${e.message}"
                )
            }
        } finally {
            _uiState.update { it.copy(isProcessing = false) }
        }
    }
    
    fun clearConversation() {
        val welcomeMessage = Message(
            content = "こんにちは！AI医療アシスタントです。どのような症状や健康に関する質問がありますか？お気軽にお聞きください。",
            isUser = false
        )
        _messages.value = listOf(welcomeMessage)
    }
    
    fun dismissError() {
        _uiState.update { it.copy(showError = false, errorMessage = "") }
    }
    
    override fun onCleared() {
        super.onCleared()
        mediaRecorder?.release()
        mediaRecorder = null
    }
}