package com.voicekoereq.data.repository

import com.voicekoereq.data.model.AuthResponse
import com.voicekoereq.data.model.AuthResult
import com.voicekoereq.data.model.Credentials
import com.voicekoereq.data.network.ApiService
import com.voicekoereq.data.network.AuthRequest
import com.voicekoereq.viewmodel.InvalidCredentialsException
import com.voicekoereq.viewmodel.NetworkException
import com.voicekoereq.viewmodel.ServerException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class F1Repository @Inject constructor(
    private val apiService: ApiService
) {

    fun authenticate(username: String, password: String): Flow<AuthResult> = flow {
        emit(AuthResult.Loading)
        
        try {
            val response = apiService.login(AuthRequest(username, password))
            emit(AuthResult.Success(response))
        } catch (e: Exception) {
            emit(AuthResult.Error(mapException(e)))
        }
    }.flowOn(Dispatchers.IO)

    fun refreshToken(token: String): Flow<AuthResult> = flow {
        emit(AuthResult.Loading)
        
        try {
            val response = apiService.refreshToken("Bearer $token")
            emit(AuthResult.Success(response))
        } catch (e: Exception) {
            emit(AuthResult.Error(mapException(e)))
        }
    }.flowOn(Dispatchers.IO)

    private fun mapException(exception: Exception): Exception {
        return when (exception) {
            is HttpException -> {
                when (exception.code()) {
                    401 -> InvalidCredentialsException()
                    in 500..599 -> ServerException()
                    else -> exception
                }
            }
            is IOException -> NetworkException()
            else -> exception
        }
    }
}

// Supporting repositories

@Singleton
class PreferencesRepository @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences
) {
    
    suspend fun saveCredentials(username: String, password: String) {
        encryptedPreferences.saveCredentials(username, password)
    }
    
    suspend fun getSavedCredentials(): Credentials? {
        return encryptedPreferences.getCredentials()
    }
    
    suspend fun clearCredentials() {
        encryptedPreferences.clearCredentials()
    }
    
    suspend fun setRememberMe(remember: Boolean) {
        encryptedPreferences.setRememberMe(remember)
    }
    
    suspend fun getRememberMe(): Boolean {
        return encryptedPreferences.getRememberMe()
    }
}

@Singleton
class TokenRepository @Inject constructor(
    private val tokenManager: TokenManager
) {
    
    suspend fun saveToken(token: String) {
        tokenManager.saveToken(token)
    }
    
    suspend fun getToken(): String? {
        return tokenManager.getToken()
    }
    
    suspend fun saveUserId(userId: String) {
        tokenManager.saveUserId(userId)
    }
    
    suspend fun getUserId(): String? {
        return tokenManager.getUserId()
    }
    
    suspend fun clearAll() {
        tokenManager.clearAll()
    }
}

// Data models

sealed class AuthResult {
    object Loading : AuthResult()
    data class Success(val data: AuthResponse) : AuthResult()
    data class Error(val exception: Exception) : AuthResult()
}

data class AuthResponse(
    val token: String,
    val userId: String,
    val userName: String,
    val expiresIn: Long
)

data class Credentials(
    val username: String,
    val password: String
)

// Network models

data class AuthRequest(
    val username: String,
    val password: String
)

// Mock implementations for the supporting classes

interface ApiService {
    suspend fun login(request: AuthRequest): AuthResponse
    suspend fun refreshToken(token: String): AuthResponse
}

interface EncryptedPreferences {
    suspend fun saveCredentials(username: String, password: String)
    suspend fun getCredentials(): Credentials?
    suspend fun clearCredentials()
    suspend fun setRememberMe(remember: Boolean)
    suspend fun getRememberMe(): Boolean
}

interface TokenManager {
    suspend fun saveToken(token: String)
    suspend fun getToken(): String?
    suspend fun saveUserId(userId: String)
    suspend fun getUserId(): String?
    suspend fun clearAll()
}

// Mock implementations for development/testing

class MockApiService : ApiService {
    override suspend fun login(request: AuthRequest): AuthResponse {
        // Simulate network delay
        kotlinx.coroutines.delay(1000)
        
        return if (request.username == "demo" && request.password == "demo123") {
            AuthResponse(
                token = "mock-jwt-token-android-12345",
                userId = "user-android-12345",
                userName = "デモユーザー",
                expiresIn = 3600000
            )
        } else {
            throw HttpException(retrofit2.Response.error<Any>(401, okhttp3.ResponseBody.create(null, "")))
        }
    }
    
    override suspend fun refreshToken(token: String): AuthResponse {
        kotlinx.coroutines.delay(500)
        
        return AuthResponse(
            token = "refreshed-jwt-token-android-12345",
            userId = "user-android-12345",
            userName = "デモユーザー",
            expiresIn = 3600000
        )
    }
}

class MockEncryptedPreferences : EncryptedPreferences {
    private var credentials: Credentials? = null
    private var rememberMe: Boolean = false
    
    override suspend fun saveCredentials(username: String, password: String) {
        credentials = Credentials(username, password)
    }
    
    override suspend fun getCredentials(): Credentials? = credentials
    
    override suspend fun clearCredentials() {
        credentials = null
    }
    
    override suspend fun setRememberMe(remember: Boolean) {
        rememberMe = remember
    }
    
    override suspend fun getRememberMe(): Boolean = rememberMe
}

class MockTokenManager : TokenManager {
    private var token: String? = null
    private var userId: String? = null
    
    override suspend fun saveToken(token: String) {
        this.token = token
    }
    
    override suspend fun getToken(): String? = token
    
    override suspend fun saveUserId(userId: String) {
        this.userId = userId
    }
    
    override suspend fun getUserId(): String? = userId
    
    override suspend fun clearAll() {
        token = null
        userId = null
    }
}