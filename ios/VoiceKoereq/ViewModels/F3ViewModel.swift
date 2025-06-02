import Foundation
import Combine
import AVFoundation
import MicrosoftCognitiveServicesSpeech

@MainActor
class F3ViewModel: ObservableObject {
    @Published var transcribedText: String = ""
    @Published var isTranscribing: Bool = false
    @Published var transcriptionProgress: Double = 0.0
    @Published var error: Error?
    @Published var hasAudioData: Bool = false
    @Published var audioDuration: TimeInterval?
    
    private let transcriptionService: F3Service
    private var cancellables = Set<AnyCancellable>()
    private var audioFileURL: URL?
    
    init(transcriptionService: F3Service = F3Service()) {
        self.transcriptionService = transcriptionService
        setupBindings()
    }
    
    private func setupBindings() {
        transcriptionService.$transcriptionResult
            .receive(on: DispatchQueue.main)
            .sink { [weak self] result in
                self?.transcribedText = result
            }
            .store(in: &cancellables)
        
        transcriptionService.$isProcessing
            .receive(on: DispatchQueue.main)
            .sink { [weak self] isProcessing in
                self?.isTranscribing = isProcessing
            }
            .store(in: &cancellables)
        
        transcriptionService.$progress
            .receive(on: DispatchQueue.main)
            .sink { [weak self] progress in
                self?.transcriptionProgress = progress
            }
            .store(in: &cancellables)
        
        transcriptionService.$error
            .receive(on: DispatchQueue.main)
            .compactMap { $0 }
            .sink { [weak self] error in
                self?.error = error
                self?.isTranscribing = false
            }
            .store(in: &cancellables)
    }
    
    func setAudioData(from url: URL) {
        audioFileURL = url
        hasAudioData = true
        
        // Calculate audio duration
        let asset = AVURLAsset(url: url)
        let duration = CMTimeGetSeconds(asset.duration)
        if duration.isFinite && duration > 0 {
            audioDuration = duration
        }
    }
    
    func startTranscription() async {
        guard let audioURL = audioFileURL else {
            error = TranscriptionError.noAudioData
            return
        }
        
        error = nil
        transcribedText = ""
        transcriptionProgress = 0.0
        
        await transcriptionService.transcribeAudio(from: audioURL)
    }
    
    func clearTranscription() {
        transcribedText = ""
        transcriptionProgress = 0.0
        error = nil
    }
    
    func cancelTranscription() {
        transcriptionService.cancelTranscription()
        isTranscribing = false
        transcriptionProgress = 0.0
    }
}

enum TranscriptionError: LocalizedError {
    case noAudioData
    case transcriptionFailed(String)
    case networkError
    case invalidConfiguration
    
    var errorDescription: String? {
        switch self {
        case .noAudioData:
            return "音声データがありません。先に録音を行ってください。"
        case .transcriptionFailed(let message):
            return "文字起こしに失敗しました: \(message)"
        case .networkError:
            return "ネットワークエラーが発生しました。接続を確認してください。"
        case .invalidConfiguration:
            return "設定エラーが発生しました。管理者に連絡してください。"
        }
    }
}