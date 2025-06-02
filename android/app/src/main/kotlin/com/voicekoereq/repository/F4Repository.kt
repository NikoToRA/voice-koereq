package com.voicekoereq.repository

import android.content.Context
import com.voicekoereq.data.AzureConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class F4Repository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val azureConfig: AzureConfig
) {
    
    fun getContext(): Context = context
    
    suspend fun getMedicalAssistantResponse(query: String): String = withContext(Dispatchers.IO) {
        val url = "${azureConfig.openAIEndpoint}/openai/deployments/${azureConfig.deploymentName}/chat/completions?api-version=2024-02-01"
        
        val systemPrompt = """
        あなたは親切で知識豊富なAI医療アシスタントです。以下のガイドラインに従って応答してください：
        
        1. 医療情報は一般的な知識として提供し、個別の診断や治療法の推奨は避ける
        2. 必要に応じて医療機関の受診を勧める
        3. 患者の不安を和らげる優しい言葉遣いを心がける
        4. 症状について詳しく聞き、適切な情報を提供する
        5. 緊急性が高い症状の場合は、すぐに医療機関を受診するよう強く勧める
        6. 回答は日本語で、分かりやすく簡潔に
        
        重要：私は医師ではなく、AIアシスタントであることを明確にし、提供する情報は参考程度であることを伝える。
        """.trimIndent()
        
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", query)
            })
        }
        
        val requestBody = JSONObject().apply {
            put("messages", messages)
            put("temperature", 0.7)
            put("max_tokens", 1000)
            put("top_p", 0.95)
            put("frequency_penalty", 0)
            put("presence_penalty", 0)
        }
        
        val request = Request.Builder()
            .url(url)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("api-key", azureConfig.openAIKey)
            .addHeader("Content-Type", "application/json")
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("Azure OpenAI API error: ${response.code}")
        }
        
        val responseBody = response.body?.string() ?: throw IOException("Empty response")
        val responseJson = JSONObject(responseBody)
        
        return responseJson
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }
    
    suspend fun transcribeAudio(audioFile: File): String = withContext(Dispatchers.IO) {
        val url = "https://${azureConfig.speechRegion}.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=ja-JP"
        
        val requestBody = audioFile.asRequestBody("audio/wav".toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Ocp-Apim-Subscription-Key", azureConfig.speechKey)
            .addHeader("Content-Type", "audio/wav")
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("Azure Speech Services error: ${response.code}")
        }
        
        val responseBody = response.body?.string() ?: throw IOException("Empty response")
        val responseJson = JSONObject(responseBody)
        
        return responseJson.getString("DisplayText")
    }
}

// Azure構成データクラス
data class AzureConfig(
    val openAIEndpoint: String,
    val openAIKey: String,
    val speechKey: String,
    val speechRegion: String,
    val deploymentName: String = "gpt-4"
)