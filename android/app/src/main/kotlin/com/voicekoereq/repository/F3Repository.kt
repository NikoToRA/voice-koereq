package com.voicekoereq.repository

import android.content.Context
import android.net.Uri
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class F3Repository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _transcriptionResult = MutableStateFlow("")
    val transcriptionResult: StateFlow<String> = _transcriptionResult.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val _progress = MutableStateFlow(0.0)
    val progress: StateFlow<Double> = _progress.asStateFlow()
    
    private val _error = MutableStateFlow<TranscriptionError?>(null)
    val error: StateFlow<TranscriptionError?> = _error.asStateFlow()
    
    private var speechConfig: SpeechConfig? = null
    private var audioConfig: AudioConfig? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionJob: Job? = null
    
    private val subscriptionKey: String
        get() = System.getenv("AZURE_SPEECH_KEY") ?: ""
    
    private val region: String
        get() = System.getenv("AZURE_SPEECH_REGION") ?: "japaneast"
    
    private val language = "ja-JP"
    
    init {
        setupSpeechConfig()
    }
    
    private fun setupSpeechConfig() {
        if (subscriptionKey.isEmpty()) {
            _error.value = TranscriptionError.InvalidConfiguration(
                "Azure Speech Service キーが設定されていません"
            )
            return
        }
        
        try {
            speechConfig = SpeechConfig.fromSubscription(subscriptionKey, region).apply {
                speechRecognitionLanguage = language
                
                // Enable detailed results for better accuracy
                setProperty(
                    PropertyId.SpeechServiceResponse_JsonResult,
                    "Detailed"
                )
                
                // Enable continuous recognition
                setProperty(
                    PropertyId.SpeechServiceConnection_RecoAutoDetectSourceLanguages,
                    "true"
                )
            }
        } catch (e: Exception) {
            _error.value = TranscriptionError.InvalidConfiguration(
                "設定エラー: ${e.message}"
            )
        }
    }
    
    suspend fun transcribeAudio(audioUri: Uri) = withContext(Dispatchers.IO) {
        _isProcessing.value = true
        _progress.value = 0.0
        _transcriptionResult.value = ""
        _error.value = null
        
        try {
            // Convert Uri to file path
            val audioFile = getFileFromUri(audioUri)
            if (!audioFile.exists()) {
                throw TranscriptionError.NoAudioData
            }
            
            // Configure audio input from file
            audioConfig = AudioConfig.fromWavFileInput(audioFile.absolutePath)
            
            val config = speechConfig ?: throw TranscriptionError.InvalidConfiguration(
                "Speech configuration not initialized"
            )
            
            val audio = audioConfig ?: throw TranscriptionError.InvalidConfiguration(
                "Audio configuration not initialized"
            )
            
            // Create speech recognizer
            speechRecognizer = SpeechRecognizer(config, audio)
            
            // Setup event handlers
            setupRecognitionHandlers()
            
            // Start continuous recognition
            performContinuousRecognition()
            
        } catch (e: TranscriptionError) {
            _error.value = e
            _isProcessing.value = false
        } catch (e: Exception) {
            _error.value = TranscriptionError.TranscriptionFailed(
                e.message ?: "Unknown error"
            )
            _isProcessing.value = false
        }
    }
    
    private fun getFileFromUri(uri: Uri): File {
        // For content URIs, copy to a temporary file
        if (uri.scheme == "content") {
            val tempFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.wav")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return tempFile
        }
        
        // For file URIs
        return File(uri.path ?: throw TranscriptionError.NoAudioData)
    }
    
    private fun setupRecognitionHandlers() {
        val recognizer = speechRecognizer ?: return
        
        // Handle intermediate results
        recognizer.recognizing.addEventListener { _, event ->
            event.result?.let { result ->
                // Update with partial results
                if (result.text.isNotEmpty()) {
                    _transcriptionResult.value = result.text
                    
                    // Update progress based on audio position
                    val duration = result.duration
                    val offset = result.offset
                    if (duration > 0) {
                        val progressValue = offset.toDouble() / duration.toDouble()
                        _progress.value = minOf(progressValue, 0.9) // Cap at 90% during processing
                    }
                }
            }
        }
        
        // Handle final results
        recognizer.recognized.addEventListener { _, event ->
            event.result?.let { result ->
                if (result.reason == ResultReason.RecognizedSpeech) {
                    // Append final recognized text
                    val currentText = _transcriptionResult.value
                    _transcriptionResult.value = if (currentText.isNotEmpty() && result.text.isNotEmpty()) {
                        "$currentText ${result.text}"
                    } else {
                        currentText + result.text
                    }
                }
            }
        }
        
        // Handle cancellation
        recognizer.canceled.addEventListener { _, event ->
            event.cancellationDetails?.let { details ->
                if (details.reason == CancellationReason.Error) {
                    _error.value = TranscriptionError.TranscriptionFailed(
                        details.errorDetails ?: "Unknown error"
                    )
                }
            }
            _isProcessing.value = false
            _progress.value = 0.0
        }
        
        // Handle session stopped
        recognizer.sessionStopped.addEventListener { _, _ ->
            _isProcessing.value = false
            _progress.value = 1.0
        }
    }
    
    private suspend fun performContinuousRecognition() = withContext(Dispatchers.IO) {
        val recognizer = speechRecognizer ?: throw TranscriptionError.InvalidConfiguration(
            "Speech recognizer not initialized"
        )
        
        recognitionJob = launch {
            try {
                // Start continuous recognition
                recognizer.startContinuousRecognitionAsync().get()
                
                // Wait for recognition to complete
                val completionSignal = CompletableDeferred<Unit>()
                
                recognizer.sessionStopped.addEventListener { _, _ ->
                    completionSignal.complete(Unit)
                }
                
                // Also monitor for completion
                launch {
                    delay(500)
                    checkForCompletion(recognizer) {
                        completionSignal.complete(Unit)
                    }
                }
                
                completionSignal.await()
                
            } catch (e: Exception) {
                throw TranscriptionError.TranscriptionFailed(
                    e.message ?: "Recognition failed"
                )
            }
        }
        
        recognitionJob?.join()
    }
    
    private suspend fun checkForCompletion(
        recognizer: SpeechRecognizer,
        onComplete: () -> Unit
    ) = withContext(Dispatchers.IO) {
        // Check if recognition has naturally completed after 2 seconds of inactivity
        delay(2000)
        
        if (_isProcessing.value) {
            try {
                recognizer.stopContinuousRecognitionAsync().get()
                onComplete()
            } catch (e: Exception) {
                _error.value = TranscriptionError.TranscriptionFailed(
                    e.message ?: "Failed to stop recognition"
                )
                _isProcessing.value = false
                onComplete()
            }
        }
    }
    
    fun cancelTranscription() {
        recognitionJob?.cancel()
        
        speechRecognizer?.let { recognizer ->
            try {
                recognizer.stopContinuousRecognitionAsync()
            } catch (e: Exception) {
                // Handle error silently as we're cancelling
            }
        }
        
        _isProcessing.value = false
        _progress.value = 0.0
    }
    
    fun cleanup() {
        cancelTranscription()
        audioConfig?.close()
        speechRecognizer?.close()
        speechConfig?.close()
    }
}

sealed class TranscriptionError : Exception() {
    object NoAudioData : TranscriptionError() {
        override val message = "音声データがありません。先に録音を行ってください。"
    }
    
    data class TranscriptionFailed(override val message: String) : TranscriptionError()
    
    object NetworkError : TranscriptionError() {
        override val message = "ネットワークエラーが発生しました。接続を確認してください。"
    }
    
    data class InvalidConfiguration(override val message: String) : TranscriptionError()
}