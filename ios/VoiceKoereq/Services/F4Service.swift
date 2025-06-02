import Foundation
import AVFoundation

enum F4ServiceError: Error {
    case invalidURL
    case networkError(String)
    case decodingError
    case audioProcessingError
    case azureAPIError(String)
}

class F4Service {
    private let azureOpenAIEndpoint = ProcessInfo.processInfo.environment["AZURE_OPENAI_ENDPOINT"] ?? ""
    private let azureOpenAIKey = ProcessInfo.processInfo.environment["AZURE_OPENAI_KEY"] ?? ""
    private let azureSpeechKey = ProcessInfo.processInfo.environment["AZURE_SPEECH_KEY"] ?? ""
    private let azureSpeechRegion = ProcessInfo.processInfo.environment["AZURE_SPEECH_REGION"] ?? "japaneast"
    private let deploymentName = "gpt-4"
    
    private let session = URLSession.shared
    
    func getMedicalAssistantResponse(for query: String) async throws -> String {
        guard !azureOpenAIEndpoint.isEmpty, !azureOpenAIKey.isEmpty else {
            throw F4ServiceError.azureAPIError("Azure OpenAI の認証情報が設定されていません")
        }
        
        let url = URL(string: "\(azureOpenAIEndpoint)/openai/deployments/\(deploymentName)/chat/completions?api-version=2024-02-01")!
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(azureOpenAIKey, forHTTPHeaderField: "api-key")
        
        let systemPrompt = """
        あなたは親切で知識豊富なAI医療アシスタントです。以下のガイドラインに従って応答してください：
        
        1. 医療情報は一般的な知識として提供し、個別の診断や治療法の推奨は避ける
        2. 必要に応じて医療機関の受診を勧める
        3. 患者の不安を和らげる優しい言葉遣いを心がける
        4. 症状について詳しく聞き、適切な情報を提供する
        5. 緊急性が高い症状の場合は、すぐに医療機関を受診するよう強く勧める
        6. 回答は日本語で、分かりやすく簡潔に
        
        重要：私は医師ではなく、AIアシスタントであることを明確にし、提供する情報は参考程度であることを伝える。
        """
        
        let messages = [
            ["role": "system", "content": systemPrompt],
            ["role": "user", "content": query]
        ]
        
        let requestBody = [
            "messages": messages,
            "temperature": 0.7,
            "max_tokens": 1000,
            "top_p": 0.95,
            "frequency_penalty": 0,
            "presence_penalty": 0
        ] as [String : Any]
        
        request.httpBody = try JSONSerialization.data(withJSONObject: requestBody)
        
        let (data, response) = try await session.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse,
              200..<300 ~= httpResponse.statusCode else {
            throw F4ServiceError.networkError("サーバーからの応答が正常ではありません")
        }
        
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let choices = json["choices"] as? [[String: Any]],
              let firstChoice = choices.first,
              let message = firstChoice["message"] as? [String: Any],
              let content = message["content"] as? String else {
            throw F4ServiceError.decodingError
        }
        
        return content.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    
    func transcribeAudio(at url: URL) async throws -> String {
        guard !azureSpeechKey.isEmpty else {
            throw F4ServiceError.azureAPIError("Azure Speech Services の認証情報が設定されていません")
        }
        
        // Azure Speech to Text API エンドポイント
        let endpoint = URL(string: "https://\(azureSpeechRegion).stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=ja-JP")!
        
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue(azureSpeechKey, forHTTPHeaderField: "Ocp-Apim-Subscription-Key")
        request.setValue("audio/wav", forHTTPHeaderField: "Content-Type")
        
        // 音声ファイルを読み込む
        let audioData = try Data(contentsOf: url)
        
        // WAV形式に変換（必要に応じて）
        let wavData = try convertToWAV(audioData: audioData, from: url)
        request.httpBody = wavData
        
        let (data, response) = try await session.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse,
              200..<300 ~= httpResponse.statusCode else {
            throw F4ServiceError.networkError("音声認識サービスでエラーが発生しました")
        }
        
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let displayText = json["DisplayText"] as? String else {
            throw F4ServiceError.decodingError
        }
        
        return displayText
    }
    
    private func convertToWAV(audioData: Data, from url: URL) throws -> Data {
        // 簡易的な実装 - 実際のプロダクションではより堅牢な変換が必要
        // ここでは、既にWAV形式であると仮定するか、
        // AVAudioFile等を使用して適切に変換する必要があります
        
        // M4A to WAV conversion would go here
        // For now, we'll return the original data
        // In production, use AVAudioFile and AVAudioConverter
        
        return audioData
    }
}