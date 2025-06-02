package com.voicekoereq.repository

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class F2Repository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingPath: String? = null
    private var isPaused = false
    
    // Azure Speech Services設定
    private var speechConfig: SpeechConfig? = null
    private var audioConfig: AudioConfig? = null
    private var speechRecognizer: SpeechRecognizer? = null
    
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: Flow<Float> = _audioLevel
    
    init {
        setupAzureSpeechServices()
    }
    
    private fun setupAzureSpeechServices() {
        // Azure Speech Services の設定
        // 注意: 実際のキーとリージョンは BuildConfig または環境変数から読み込む
        val subscriptionKey = System.getenv("AZURE_SPEECH_KEY") ?: ""
        val region = System.getenv("AZURE_SPEECH_REGION") ?: ""
        
        if (subscriptionKey.isNotEmpty() && region.isNotEmpty()) {
            try {
                speechConfig = SpeechConfig.fromSubscription(subscriptionKey, region)
                speechConfig?.speechRecognitionLanguage = "ja-JP"
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    suspend fun startRecording(): Unit = withContext(Dispatchers.IO) {
        val fileName = "recording_${System.currentTimeMillis()}.m4a"
        val recordingFile = File(context.getExternalFilesDir(null), fileName)
        currentRecordingPath = recordingFile.absolutePath
        
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(192000)
            setOutputFile(recordingFile.absolutePath)
            
            try {
                prepare()
                start()
                isPaused = false
                startAudioLevelMonitoring()
            } catch (e: IOException) {
                throw RecordingException("録音の開始に失敗しました", e)
            }
        }
    }
    
    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.pause()
            isPaused = true
            _audioLevel.value = 0f
        }
    }
    
    fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.resume()
            isPaused = false
            startAudioLevelMonitoring()
        }
    }
    
    suspend fun stopRecording(): String = withContext(Dispatchers.IO) {
        mediaRecorder?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                throw RecordingException("録音の停止に失敗しました", e)
            }
        }
        mediaRecorder = null
        _audioLevel.value = 0f
        
        currentRecordingPath ?: throw RecordingException("録音ファイルが見つかりません")
    }
    
    private fun startAudioLevelMonitoring() {
        // MediaRecorderは直接オーディオレベルを取得できないため、
        // 実際の実装では AudioRecord を使用するか、
        // または録音中のファイルサイズの変化を監視するなどの方法を検討
        
        // ここではダミーの実装
        Thread {
            while (mediaRecorder != null && !isPaused) {
                _audioLevel.value = (0..100).random() / 100f
                Thread.sleep(100)
            }
        }.start()
    }
    
    suspend fun uploadRecording(filePath: String) = withContext(Dispatchers.IO) {
        // Azure Blob Storage へのアップロード実装
        // 実際の実装では Azure Storage SDK を使用
        val file = File(filePath)
        if (!file.exists()) {
            throw RecordingException("アップロードするファイルが見つかりません")
        }
        
        // TODO: Azure Blob Storage へのアップロード
        // val blobClient = BlobServiceClient(connectionString)
        // blobClient.uploadFile(file)
    }
    
    suspend fun transcribeAudio(filePath: String): String = withContext(Dispatchers.IO) {
        val audioConfig = AudioConfig.fromWavFileInput(filePath)
        val recognizer = SpeechRecognizer(speechConfig, audioConfig)
        
        var transcription = ""
        recognizer.recognized.addEventListener { _, event ->
            if (event.result.reason == ResultReason.RecognizedSpeech) {
                transcription += event.result.text
            }
        }
        
        val result = recognizer.recognizeOnceAsync().get()
        
        when (result.reason) {
            ResultReason.RecognizedSpeech -> transcription
            ResultReason.NoMatch -> throw TranscriptionException("音声を認識できませんでした")
            ResultReason.Canceled -> {
                val cancellation = CancellationDetails.fromResult(result)
                throw TranscriptionException("音声認識がキャンセルされました: ${cancellation.reason}")
            }
            else -> throw TranscriptionException("音声認識に失敗しました")
        }
    }
    
    fun hasRecordings(): Flow<Boolean> = flow {
        val recordingsDir = context.getExternalFilesDir(null)
        val recordings = recordingsDir?.listFiles { file ->
            file.name.startsWith("recording_") && file.name.endsWith(".m4a")
        }
        emit(!recordings.isNullOrEmpty())
    }
    
    suspend fun getRecordings(): List<RecordingFile> = withContext(Dispatchers.IO) {
        val recordingsDir = context.getExternalFilesDir(null)
        val recordings = recordingsDir?.listFiles { file ->
            file.name.startsWith("recording_") && file.name.endsWith(".m4a")
        } ?: emptyArray()
        
        recordings.map { file ->
            RecordingFile(
                fileName = file.name,
                filePath = file.absolutePath,
                sizeInBytes = file.length(),
                createdAt = file.lastModified()
            )
        }.sortedByDescending { it.createdAt }
    }
    
    fun cleanup() {
        mediaRecorder?.release()
        mediaRecorder = null
        speechRecognizer?.close()
    }
}

// データクラス
data class RecordingFile(
    val fileName: String,
    val filePath: String,
    val sizeInBytes: Long,
    val createdAt: Long
)

// カスタム例外
class RecordingException(message: String, cause: Throwable? = null) : Exception(message, cause)
class TranscriptionException(message: String) : Exception(message)