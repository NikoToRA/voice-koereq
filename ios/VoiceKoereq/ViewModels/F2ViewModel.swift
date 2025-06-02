import Foundation
import Combine
import AVFoundation
import MicrosoftCognitiveServicesSpeech

@MainActor
class F2ViewModel: ObservableObject {
    // MARK: - Published Properties
    @Published var isRecording = false
    @Published var isPaused = false
    @Published var isLoading = false
    @Published var recordingTime: TimeInterval = 0
    @Published var audioLevel: Float = 0.0
    @Published var hasRecordings = false
    @Published var statusText = "準備完了"
    
    // MARK: - Private Properties
    private let audioService = F2AudioService()
    private var recordingTimer: Timer?
    private var audioLevelTimer: Timer?
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Computed Properties
    var recordingTimeText: String {
        let formatter = DateComponentsFormatter()
        formatter.allowedUnits = [.minute, .second]
        formatter.zeroFormattingBehavior = .pad
        return formatter.string(from: recordingTime) ?? "00:00"
    }
    
    // MARK: - Initialization
    init() {
        setupBindings()
    }
    
    private func setupBindings() {
        // オーディオサービスの状態をバインディング
        audioService.$isRecording
            .receive(on: DispatchQueue.main)
            .sink { [weak self] isRecording in
                self?.isRecording = isRecording
                self?.updateStatus()
            }
            .store(in: &cancellables)
        
        audioService.$isPaused
            .receive(on: DispatchQueue.main)
            .sink { [weak self] isPaused in
                self?.isPaused = isPaused
                self?.updateStatus()
            }
            .store(in: &cancellables)
        
        audioService.$audioLevel
            .receive(on: DispatchQueue.main)
            .sink { [weak self] level in
                self?.audioLevel = level
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Public Methods
    func requestMicrophonePermission() async {
        await audioService.requestMicrophonePermission()
    }
    
    func startRecording() async throws {
        isLoading = true
        defer { isLoading = false }
        
        do {
            try await audioService.startRecording()
            startRecordingTimer()
            startAudioLevelMonitoring()
            statusText = "録音中"
        } catch {
            statusText = "エラーが発生しました"
            throw RecordingError.failedToStart(error.localizedDescription)
        }
    }
    
    func pauseRecording() {
        audioService.pauseRecording()
        recordingTimer?.invalidate()
        audioLevelTimer?.invalidate()
        statusText = "一時停止中"
    }
    
    func resumeRecording() {
        audioService.resumeRecording()
        startRecordingTimer()
        startAudioLevelMonitoring()
        statusText = "録音中"
    }
    
    func stopRecording() async throws {
        isLoading = true
        defer { isLoading = false }
        
        recordingTimer?.invalidate()
        audioLevelTimer?.invalidate()
        
        do {
            let recordingURL = try await audioService.stopRecording()
            recordingTime = 0
            hasRecordings = true
            statusText = "録音完了"
            
            // 録音ファイルを保存・処理
            try await processRecording(at: recordingURL)
        } catch {
            statusText = "保存に失敗しました"
            throw RecordingError.failedToSave(error.localizedDescription)
        }
    }
    
    // MARK: - Private Methods
    private func startRecordingTimer() {
        recordingTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            Task { @MainActor in
                self.recordingTime += 1
            }
        }
    }
    
    private func startAudioLevelMonitoring() {
        audioLevelTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            self.audioService.updateAudioLevel()
        }
    }
    
    private func updateStatus() {
        if isRecording {
            statusText = isPaused ? "一時停止中" : "録音中"
        } else {
            statusText = "準備完了"
        }
    }
    
    private func processRecording(at url: URL) async throws {
        // ここで録音ファイルの処理を行う
        // 例: Azure Blob Storageへのアップロード、ローカル保存など
        
        // 仮の実装
        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let fileName = "recording_\(Date().timeIntervalSince1970).m4a"
        let destinationURL = documentsPath.appendingPathComponent(fileName)
        
        try FileManager.default.copyItem(at: url, to: destinationURL)
        
        // Azure Speech Services で音声認識を行う場合はここで実装
        // await transcribeAudio(at: destinationURL)
    }
}

// MARK: - Error Types
enum RecordingError: LocalizedError {
    case failedToStart(String)
    case failedToSave(String)
    case microphonePermissionDenied
    
    var errorDescription: String? {
        switch self {
        case .failedToStart(let reason):
            return "録音を開始できませんでした: \(reason)"
        case .failedToSave(let reason):
            return "録音を保存できませんでした: \(reason)"
        case .microphonePermissionDenied:
            return "マイクへのアクセスが拒否されました"
        }
    }
}