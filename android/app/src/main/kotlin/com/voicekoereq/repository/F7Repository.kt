package com.voicekoereq.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

data class F7Summary(
    val id: String,
    val transcriptionId: String,
    val title: String,
    val summary: String,
    val keyPoints: List<String>,
    val generatedAt: Date,
    val language: String
)

sealed class F7Result<out T> {
    data class Success<T>(val data: T) : F7Result<T>()
    data class Error(val exception: F7Exception) : F7Result<Nothing>()
    object Loading : F7Result<Nothing>()
}

sealed class F7Exception(message: String) : Exception(message) {
    object InvalidTranscriptionId : F7Exception("無効な文字起こしIDです")
    object SummaryGenerationFailed : F7Exception("サマリー生成に失敗しました")
    object SummaryNotFound : F7Exception("サマリーが見つかりません")
    data class NetworkError(val cause: Throwable) : F7Exception("ネットワークエラー: ${cause.message}")
    object DecodingError : F7Exception("データの解析に失敗しました")
    object DeletionFailed : F7Exception("サマリーの削除に失敗しました")
}

interface F7RepositoryInterface {
    fun generateSummary(transcriptionId: String): Flow<F7Result<F7Summary>>
    fun getSummaries(): Flow<F7Result<List<F7Summary>>>
    fun getSummary(id: String): Flow<F7Result<F7Summary>>
    fun deleteSummary(id: String): Flow<F7Result<Unit>>
}

@Singleton
class F7Repository @Inject constructor() : F7RepositoryInterface {
    
    // Mock data for development
    private val mockSummaries = mutableListOf(
        F7Summary(
            id = "1",
            transcriptionId = "trans-1",
            title = "初診患者の症状相談",
            summary = "患者は頭痛と軽度の発熱を訴えています。症状は3日前から始まり、市販薬で一時的に改善するが再発する状態です。",
            keyPoints = listOf("頭痛（3日間継続）", "軽度の発熱（37.5度）", "市販薬で一時的改善"),
            generatedAt = Date(),
            language = "ja"
        ),
        F7Summary(
            id = "2",
            transcriptionId = "trans-2",
            title = "経過観察の相談",
            summary = "前回処方された薬の効果について報告。症状は改善傾向にあるが、完全には回復していない。",
            keyPoints = listOf("薬の効果あり", "症状改善傾向", "完全回復まで継続観察必要"),
            generatedAt = Date(System.currentTimeMillis() - 86400000),
            language = "ja"
        )
    )
    
    override fun generateSummary(transcriptionId: String): Flow<F7Result<F7Summary>> = flow {
        emit(F7Result.Loading)
        
        if (transcriptionId.isBlank()) {
            emit(F7Result.Error(F7Exception.InvalidTranscriptionId))
            return@flow
        }
        
        try {
            // Simulate API call
            delay(1000)
            
            val newSummary = F7Summary(
                id = System.currentTimeMillis().toString(),
                transcriptionId = transcriptionId,
                title = "新しい相談サマリー",
                summary = "これはテスト用の新しいサマリーです。実際のAIサービスでは、文字起こしデータから自動的にサマリーが生成されます。",
                keyPoints = listOf("テストポイント1", "テストポイント2", "テストポイント3"),
                generatedAt = Date(),
                language = "ja"
            )
            
            mockSummaries.add(0, newSummary)
            emit(F7Result.Success(newSummary))
            
        } catch (e: Exception) {
            emit(F7Result.Error(F7Exception.SummaryGenerationFailed))
        }
    }
    
    override fun getSummaries(): Flow<F7Result<List<F7Summary>>> = flow {
        emit(F7Result.Loading)
        
        try {
            // Simulate API call
            delay(500)
            emit(F7Result.Success(mockSummaries.toList()))
        } catch (e: Exception) {
            emit(F7Result.Error(F7Exception.NetworkError(e)))
        }
    }
    
    override fun getSummary(id: String): Flow<F7Result<F7Summary>> = flow {
        emit(F7Result.Loading)
        
        try {
            // Simulate API call
            delay(300)
            
            val summary = mockSummaries.find { it.id == id }
            if (summary != null) {
                emit(F7Result.Success(summary))
            } else {
                emit(F7Result.Error(F7Exception.SummaryNotFound))
            }
        } catch (e: Exception) {
            emit(F7Result.Error(F7Exception.NetworkError(e)))
        }
    }
    
    override fun deleteSummary(id: String): Flow<F7Result<Unit>> = flow {
        emit(F7Result.Loading)
        
        try {
            // Simulate API call
            delay(300)
            
            val removed = mockSummaries.removeAll { it.id == id }
            if (removed) {
                emit(F7Result.Success(Unit))
            } else {
                emit(F7Result.Error(F7Exception.DeletionFailed))
            }
        } catch (e: Exception) {
            emit(F7Result.Error(F7Exception.NetworkError(e)))
        }
    }
}

// Mock repository for testing
class MockF7Repository : F7RepositoryInterface {
    var shouldFail = false
    private val summaries = mutableListOf<F7Summary>()
    
    init {
        summaries.addAll(listOf(
            F7Summary(
                id = "test-1",
                transcriptionId = "trans-test-1",
                title = "テストサマリー1",
                summary = "これはテスト用のサマリー1です。",
                keyPoints = listOf("ポイント1", "ポイント2"),
                generatedAt = Date(),
                language = "ja"
            ),
            F7Summary(
                id = "test-2",
                transcriptionId = "trans-test-2",
                title = "テストサマリー2",
                summary = "これはテスト用のサマリー2です。",
                keyPoints = listOf("ポイントA", "ポイントB"),
                generatedAt = Date(System.currentTimeMillis() - 3600000),
                language = "ja"
            )
        ))
    }
    
    override fun generateSummary(transcriptionId: String): Flow<F7Result<F7Summary>> = flow {
        emit(F7Result.Loading)
        delay(100)
        
        if (shouldFail) {
            emit(F7Result.Error(F7Exception.SummaryGenerationFailed))
        } else if (transcriptionId.isBlank()) {
            emit(F7Result.Error(F7Exception.InvalidTranscriptionId))
        } else {
            val newSummary = F7Summary(
                id = "test-${System.currentTimeMillis()}",
                transcriptionId = transcriptionId,
                title = "テスト生成サマリー",
                summary = "これはテストで生成されたサマリーです。",
                keyPoints = listOf("生成ポイント1", "生成ポイント2"),
                generatedAt = Date(),
                language = "ja"
            )
            summaries.add(0, newSummary)
            emit(F7Result.Success(newSummary))
        }
    }
    
    override fun getSummaries(): Flow<F7Result<List<F7Summary>>> = flow {
        emit(F7Result.Loading)
        delay(100)
        
        if (shouldFail) {
            emit(F7Result.Error(F7Exception.NetworkError(Exception("Test error"))))
        } else {
            emit(F7Result.Success(summaries.toList()))
        }
    }
    
    override fun getSummary(id: String): Flow<F7Result<F7Summary>> = flow {
        emit(F7Result.Loading)
        delay(100)
        
        if (shouldFail) {
            emit(F7Result.Error(F7Exception.SummaryNotFound))
        } else {
            val summary = summaries.find { it.id == id }
            if (summary != null) {
                emit(F7Result.Success(summary))
            } else {
                emit(F7Result.Error(F7Exception.SummaryNotFound))
            }
        }
    }
    
    override fun deleteSummary(id: String): Flow<F7Result<Unit>> = flow {
        emit(F7Result.Loading)
        delay(100)
        
        if (shouldFail) {
            emit(F7Result.Error(F7Exception.DeletionFailed))
        } else {
            summaries.removeAll { it.id == id }
            emit(F7Result.Success(Unit))
        }
    }
}