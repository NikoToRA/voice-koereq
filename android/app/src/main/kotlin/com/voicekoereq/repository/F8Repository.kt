package com.voicekoereq.repository

import android.content.Context
import androidx.room.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

// Data Models
data class MedicalRequest(
    val id: String = UUID.randomUUID().toString(),
    val patientName: String,
    val symptoms: String,
    val timestamp: Long = System.currentTimeMillis(),
    val transcriptionText: String? = null,
    val audioFilePath: String? = null,
    val isSynced: Boolean = false,
    val lastModified: Long = System.currentTimeMillis()
)

data class SyncResult(
    val successCount: Int,
    val failureCount: Int,
    val conflicts: List<SyncConflict>
)

data class SyncConflict(
    val localData: MedicalRequest,
    val serverData: MedicalRequest,
    val resolution: ConflictResolution
)

enum class ConflictResolution {
    USE_LOCAL,
    USE_SERVER,
    MERGE
}

sealed class F8RepositoryError : Exception() {
    data class PersistenceError(override val message: String) : F8RepositoryError()
    data class SyncError(override val message: String) : F8RepositoryError()
    object NetworkUnavailable : F8RepositoryError()
    object DataCorruption : F8RepositoryError()
}

// Room Entities
@Entity(tableName = "medical_requests")
data class MedicalRequestEntity(
    @PrimaryKey val id: String,
    val patientName: String,
    val symptoms: String,
    val timestamp: Long,
    val transcriptionText: String?,
    val audioFilePath: String?,
    val isSynced: Boolean,
    val lastModified: Long
)

// Room DAO
@Dao
interface MedicalRequestDao {
    @Query("SELECT * FROM medical_requests ORDER BY timestamp DESC")
    fun getAllRequests(): Flow<List<MedicalRequestEntity>>
    
    @Query("SELECT * FROM medical_requests WHERE isSynced = 0")
    suspend fun getUnsyncedRequests(): List<MedicalRequestEntity>
    
    @Query("SELECT * FROM medical_requests WHERE id = :id")
    suspend fun getRequestById(id: String): MedicalRequestEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: MedicalRequestEntity)
    
    @Update
    suspend fun updateRequest(request: MedicalRequestEntity)
    
    @Delete
    suspend fun deleteRequest(request: MedicalRequestEntity)
    
    @Query("DELETE FROM medical_requests WHERE id = :id")
    suspend fun deleteRequestById(id: String)
    
    @Query("DELETE FROM medical_requests")
    suspend fun deleteAllRequests()
    
    @Query("UPDATE medical_requests SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)
}

// Room Database
@Database(
    entities = [MedicalRequestEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class VoiceKoeReqDatabase : RoomDatabase() {
    abstract fun medicalRequestDao(): MedicalRequestDao
    
    companion object {
        @Volatile
        private var INSTANCE: VoiceKoeReqDatabase? = null
        
        fun getDatabase(context: Context): VoiceKoeReqDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoiceKoeReqDatabase::class.java,
                    "voice_koereq_offline_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Type Converters
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

// Repository Interface
interface F8Repository {
    fun getAllOfflineRequests(): Flow<List<MedicalRequest>>
    suspend fun saveMedicalRequest(request: MedicalRequest): Result<Boolean>
    suspend fun syncOfflineData(): Result<SyncResult>
    suspend fun deleteOfflineRequest(id: String): Result<Boolean>
    suspend fun clearAllOfflineData(): Result<Boolean>
}

// Repository Implementation
@Singleton
class F8RepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkService: NetworkService
) : F8Repository {
    
    private val database = VoiceKoeReqDatabase.getDatabase(context)
    private val dao = database.medicalRequestDao()
    
    override fun getAllOfflineRequests(): Flow<List<MedicalRequest>> = flow {
        dao.getAllRequests().collect { entities ->
            emit(entities.map { it.toMedicalRequest() })
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun saveMedicalRequest(request: MedicalRequest): Result<Boolean> {
        return try {
            withContext(Dispatchers.IO) {
                val entity = request.toEntity()
                dao.insertRequest(entity)
                
                // Try to sync immediately if online
                if (NetworkMonitor.isConnected(context)) {
                    syncSingleRequest(request)
                }
                
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(F8RepositoryError.PersistenceError("データ保存エラー: ${e.message}"))
        }
    }
    
    override suspend fun syncOfflineData(): Result<SyncResult> {
        return try {
            if (!NetworkMonitor.isConnected(context)) {
                return Result.failure(F8RepositoryError.NetworkUnavailable)
            }
            
            withContext(Dispatchers.IO) {
                val unsyncedRequests = dao.getUnsyncedRequests()
                var successCount = 0
                var failureCount = 0
                val conflicts = mutableListOf<SyncConflict>()
                
                unsyncedRequests.forEach { entity ->
                    try {
                        // Simulate API call
                        val success = networkService.syncMedicalRequest(entity.toMedicalRequest())
                        
                        if (success) {
                            dao.markAsSynced(entity.id)
                            successCount++
                        } else {
                            failureCount++
                        }
                    } catch (e: Exception) {
                        failureCount++
                    }
                }
                
                Result.success(
                    SyncResult(
                        successCount = successCount,
                        failureCount = failureCount,
                        conflicts = conflicts
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(F8RepositoryError.SyncError("同期エラー: ${e.message}"))
        }
    }
    
    override suspend fun deleteOfflineRequest(id: String): Result<Boolean> {
        return try {
            withContext(Dispatchers.IO) {
                dao.deleteRequestById(id)
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(F8RepositoryError.PersistenceError("削除エラー: ${e.message}"))
        }
    }
    
    override suspend fun clearAllOfflineData(): Result<Boolean> {
        return try {
            withContext(Dispatchers.IO) {
                dao.deleteAllRequests()
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(F8RepositoryError.PersistenceError("データクリアエラー: ${e.message}"))
        }
    }
    
    private suspend fun syncSingleRequest(request: MedicalRequest) {
        try {
            val success = networkService.syncMedicalRequest(request)
            if (success) {
                dao.markAsSynced(request.id)
            }
        } catch (e: Exception) {
            // Silently fail - will be synced later
        }
    }
    
    // Extension functions
    private fun MedicalRequestEntity.toMedicalRequest(): MedicalRequest {
        return MedicalRequest(
            id = id,
            patientName = patientName,
            symptoms = symptoms,
            timestamp = timestamp,
            transcriptionText = transcriptionText,
            audioFilePath = audioFilePath,
            isSynced = isSynced,
            lastModified = lastModified
        )
    }
    
    private fun MedicalRequest.toEntity(): MedicalRequestEntity {
        return MedicalRequestEntity(
            id = id,
            patientName = patientName,
            symptoms = symptoms,
            timestamp = timestamp,
            transcriptionText = transcriptionText,
            audioFilePath = audioFilePath,
            isSynced = isSynced,
            lastModified = lastModified
        )
    }
}

// Network Monitor
object NetworkMonitor {
    fun isConnected(context: Context): Boolean {
        // Simplified implementation
        // In real app, use ConnectivityManager
        return true
    }
}

// Network Service (placeholder)
@Singleton
class NetworkService @Inject constructor() {
    suspend fun syncMedicalRequest(request: MedicalRequest): Boolean {
        // Simulate network call
        kotlinx.coroutines.delay(500)
        return kotlin.random.Random.nextBoolean()
    }
}

// Mock Repository for testing
class MockF8Repository : F8Repository {
    private val mockData = listOf(
        MedicalRequest(
            patientName = "テスト患者",
            symptoms = "頭痛と発熱",
            timestamp = System.currentTimeMillis() - 3600000,
            transcriptionText = "今朝から頭痛がひどく、熱も38度あります",
            isSynced = false
        ),
        MedicalRequest(
            patientName = "サンプル患者",
            symptoms = "咳と喉の痛み",
            timestamp = System.currentTimeMillis() - 7200000,
            transcriptionText = "3日前から咳が続いています",
            isSynced = true
        )
    )
    
    override fun getAllOfflineRequests(): Flow<List<MedicalRequest>> = flow {
        emit(mockData)
    }
    
    override suspend fun saveMedicalRequest(request: MedicalRequest): Result<Boolean> {
        return Result.success(true)
    }
    
    override suspend fun syncOfflineData(): Result<SyncResult> {
        return Result.success(
            SyncResult(successCount = 2, failureCount = 0, conflicts = emptyList())
        )
    }
    
    override suspend fun deleteOfflineRequest(id: String): Result<Boolean> {
        return Result.success(true)
    }
    
    override suspend fun clearAllOfflineData(): Result<Boolean> {
        return Result.success(true)
    }
}