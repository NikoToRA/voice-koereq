import Foundation
import Combine
import AVFoundation

struct Message: Identifiable {
    let id = UUID()
    let content: String
    let isUser: Bool
    let timestamp: Date = Date()
}

@MainActor
class F4ViewModel: ObservableObject {
    @Published var messages: [Message] = []
    @Published var isProcessing = false
    @Published var showError = false
    @Published var errorMessage = ""
    
    private let service = F4Service()
    private var cancellables = Set<AnyCancellable>()
    private var audioRecorder: AVAudioRecorder?
    private var audioSession = AVAudioSession.sharedInstance()
    private var recordingURL: URL?
    
    init() {
        setupInitialMessage()
        setupAudioSession()
    }
    
    private func setupInitialMessage() {
        let welcomeMessage = Message(
            content: "こんにちは！AI医療アシスタントです。どのような症状や健康に関する質問がありますか？お気軽にお聞きください。",
            isUser: false
        )
        messages.append(welcomeMessage)
    }
    
    private func setupAudioSession() {
        do {
            try audioSession.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetooth])
            try audioSession.setActive(true)
        } catch {
            print("オーディオセッションの設定に失敗しました: \(error)")
        }
    }
    
    func sendMessage(_ text: String) async {
        // ユーザーメッセージを追加
        let userMessage = Message(content: text, isUser: true)
        messages.append(userMessage)
        
        isProcessing = true
        
        do {
            // AI応答を取得
            let response = try await service.getMedicalAssistantResponse(for: text)
            
            // AI応答メッセージを追加
            let assistantMessage = Message(content: response, isUser: false)
            messages.append(assistantMessage)
        } catch {
            showError = true
            errorMessage = "応答の取得中にエラーが発生しました: \(error.localizedDescription)"
        }
        
        isProcessing = false
    }
    
    func startRecording() {
        guard audioRecorder == nil else { return }
        
        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let audioFilename = documentsPath.appendingPathComponent("recording_\(Date().timeIntervalSince1970).m4a")
        recordingURL = audioFilename
        
        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 44100,
            AVNumberOfChannelsKey: 1,
            AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
        ]
        
        do {
            audioRecorder = try AVAudioRecorder(url: audioFilename, settings: settings)
            audioRecorder?.record()
        } catch {
            showError = true
            errorMessage = "録音の開始に失敗しました: \(error.localizedDescription)"
        }
    }
    
    func stopRecording() {
        guard let recorder = audioRecorder else { return }
        
        recorder.stop()
        audioRecorder = nil
        
        // 録音したファイルを処理
        if let url = recordingURL {
            processRecordedAudio(at: url)
        }
    }
    
    private func processRecordedAudio(at url: URL) {
        Task {
            isProcessing = true
            
            do {
                // 音声をテキストに変換
                let transcribedText = try await service.transcribeAudio(at: url)
                
                // テキストとして送信
                await sendMessage(transcribedText)
                
                // 録音ファイルを削除
                try? FileManager.default.removeItem(at: url)
            } catch {
                await MainActor.run {
                    showError = true
                    errorMessage = "音声の処理中にエラーが発生しました: \(error.localizedDescription)"
                }
            }
            
            isProcessing = false
        }
    }
    
    func clearConversation() {
        messages.removeAll()
        setupInitialMessage()
    }
}