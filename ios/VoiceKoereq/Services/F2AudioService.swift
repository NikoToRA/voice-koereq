import Foundation
import AVFoundation
import MicrosoftCognitiveServicesSpeech

class F2AudioService: NSObject, ObservableObject {
    // MARK: - Published Properties
    @Published var isRecording = false
    @Published var isPaused = false
    @Published var audioLevel: Float = 0.0
    
    // MARK: - Private Properties
    private var audioEngine: AVAudioEngine?
    private var audioRecorder: AVAudioRecorder?
    private var audioPlayer: AVAudioPlayer?
    private var recordingSession: AVAudioSession?
    private var recordingURL: URL?
    
    // Azure Speech Services設定
    private var speechConfig: SPXSpeechConfiguration?
    private var audioConfig: SPXAudioConfiguration?
    private var speechRecognizer: SPXSpeechRecognizer?
    
    // MARK: - Initialization
    override init() {
        super.init()
        setupAudioSession()
        setupAzureSpeechServices()
    }
    
    // MARK: - Audio Session Setup
    private func setupAudioSession() {
        recordingSession = AVAudioSession.sharedInstance()
        
        do {
            try recordingSession?.setCategory(.playAndRecord, mode: .default)
            try recordingSession?.setActive(true)
        } catch {
            print("オーディオセッションの設定に失敗しました: \(error)")
        }
    }
    
    private func setupAzureSpeechServices() {
        // Azure Speech Services の設定
        // 注意: 実際のキーとリージョンは環境変数または設定ファイルから読み込む
        if let subscriptionKey = ProcessInfo.processInfo.environment["AZURE_SPEECH_KEY"],
           let region = ProcessInfo.processInfo.environment["AZURE_SPEECH_REGION"] {
            speechConfig = try? SPXSpeechConfiguration(subscription: subscriptionKey, region: region)
            speechConfig?.speechRecognitionLanguage = "ja-JP"
        }
    }
    
    // MARK: - Permission Management
    func requestMicrophonePermission() async {
        await withCheckedContinuation { continuation in
            AVAudioSession.sharedInstance().requestRecordPermission { granted in
                if granted {
                    print("マイクの使用が許可されました")
                } else {
                    print("マイクの使用が拒否されました")
                }
                continuation.resume()
            }
        }
    }
    
    // MARK: - Recording Control
    func startRecording() async throws {
        guard await checkMicrophonePermission() else {
            throw RecordingError.microphonePermissionDenied
        }
        
        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let fileName = "temp_recording_\(Date().timeIntervalSince1970).m4a"
        recordingURL = documentsPath.appendingPathComponent(fileName)
        
        guard let recordingURL = recordingURL else {
            throw RecordingError.failedToStart("録音URLの作成に失敗しました")
        }
        
        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 44100,
            AVNumberOfChannelsKey: 2,
            AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
        ]
        
        do {
            audioRecorder = try AVAudioRecorder(url: recordingURL, settings: settings)
            audioRecorder?.delegate = self
            audioRecorder?.isMeteringEnabled = true
            audioRecorder?.prepareToRecord()
            audioRecorder?.record()
            
            isRecording = true
            isPaused = false
            
            // Azure Speech Services でリアルタイム認識を開始する場合
            // startContinuousRecognition()
        } catch {
            throw RecordingError.failedToStart(error.localizedDescription)
        }
    }
    
    func pauseRecording() {
        audioRecorder?.pause()
        isPaused = true
    }
    
    func resumeRecording() {
        audioRecorder?.record()
        isPaused = false
    }
    
    func stopRecording() async throws -> URL {
        audioRecorder?.stop()
        isRecording = false
        isPaused = false
        
        guard let url = recordingURL else {
            throw RecordingError.failedToSave("録音URLが見つかりません")
        }
        
        return url
    }
    
    // MARK: - Audio Level Monitoring
    func updateAudioLevel() {
        guard let recorder = audioRecorder, recorder.isRecording else {
            audioLevel = 0.0
            return
        }
        
        recorder.updateMeters()
        let level = recorder.averagePower(forChannel: 0)
        let normalizedLevel = pow(10, level / 20) // デシベルを線形スケールに変換
        audioLevel = max(0.0, min(1.0, normalizedLevel))
    }
    
    // MARK: - Helper Methods
    private func checkMicrophonePermission() async -> Bool {
        switch AVAudioSession.sharedInstance().recordPermission {
        case .granted:
            return true
        case .denied:
            return false
        case .undetermined:
            return await withCheckedContinuation { continuation in
                AVAudioSession.sharedInstance().requestRecordPermission { granted in
                    continuation.resume(returning: granted)
                }
            }
        @unknown default:
            return false
        }
    }
    
    // MARK: - Azure Speech Services Methods
    private func startContinuousRecognition() {
        guard let speechConfig = speechConfig,
              let recordingURL = recordingURL else { return }
        
        do {
            audioConfig = try SPXAudioConfiguration(wavFileInput: recordingURL.path)
            speechRecognizer = try SPXSpeechRecognizer(speechConfiguration: speechConfig, audioConfiguration: audioConfig!)
            
            speechRecognizer?.addRecognizingEventHandler { [weak self] (recognizer, event) in
                print("認識中: \(event.result.text ?? "")")
            }
            
            speechRecognizer?.addRecognizedEventHandler { [weak self] (recognizer, event) in
                print("認識完了: \(event.result.text ?? "")")
            }
            
            try speechRecognizer?.startContinuousRecognition()
        } catch {
            print("音声認識の開始に失敗しました: \(error)")
        }
    }
    
    private func stopContinuousRecognition() {
        do {
            try speechRecognizer?.stopContinuousRecognition()
        } catch {
            print("音声認識の停止に失敗しました: \(error)")
        }
    }
}

// MARK: - AVAudioRecorderDelegate
extension F2AudioService: AVAudioRecorderDelegate {
    func audioRecorderDidFinishRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {
        if flag {
            print("録音が正常に終了しました")
        } else {
            print("録音の終了中にエラーが発生しました")
        }
    }
    
    func audioRecorderEncodeErrorDidOccur(_ recorder: AVAudioRecorder, error: Error?) {
        if let error = error {
            print("録音エンコードエラー: \(error.localizedDescription)")
        }
    }
}