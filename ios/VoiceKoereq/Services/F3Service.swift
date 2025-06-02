import Foundation
import Combine
import MicrosoftCognitiveServicesSpeech

class F3Service: NSObject, ObservableObject {
    @Published var transcriptionResult: String = ""
    @Published var isProcessing: Bool = false
    @Published var progress: Double = 0.0
    @Published var error: TranscriptionError?
    
    private var speechConfig: SPXSpeechConfiguration?
    private var audioConfig: SPXAudioConfiguration?
    private var speechRecognizer: SPXSpeechRecognizer?
    private var recognitionTask: Task<Void, Never>?
    
    private let subscriptionKey: String
    private let region: String
    private let language: String = "ja-JP"
    
    override init() {
        // Azure Speech Service credentials should be configured in app settings
        self.subscriptionKey = ProcessInfo.processInfo.environment["AZURE_SPEECH_KEY"] ?? ""
        self.region = ProcessInfo.processInfo.environment["AZURE_SPEECH_REGION"] ?? "japaneast"
        
        super.init()
        setupSpeechConfig()
    }
    
    private func setupSpeechConfig() {
        guard !subscriptionKey.isEmpty else {
            error = .invalidConfiguration
            return
        }
        
        do {
            speechConfig = try SPXSpeechConfiguration(subscription: subscriptionKey, region: region)
            speechConfig?.speechRecognitionLanguage = language
            
            // Enable detailed results for better accuracy
            speechConfig?.setPropertyTo("Detailed", 
                                      by: SPXPropertyId.speechServiceResponseJsonResult)
            
            // Enable continuous recognition
            speechConfig?.setPropertyTo("true", 
                                      by: SPXPropertyId.speechServiceConnectionRecoAutoDetectSourceLanguages)
        } catch {
            self.error = .invalidConfiguration
        }
    }
    
    func transcribeAudio(from url: URL) async {
        await MainActor.run {
            isProcessing = true
            progress = 0.0
            transcriptionResult = ""
            error = nil
        }
        
        do {
            // Configure audio input from file
            audioConfig = try SPXAudioConfiguration(wavFileInput: url.path)
            
            guard let speechConfig = speechConfig,
                  let audioConfig = audioConfig else {
                throw TranscriptionError.invalidConfiguration
            }
            
            // Create speech recognizer
            speechRecognizer = try SPXSpeechRecognizer(speechConfiguration: speechConfig, 
                                                      audioConfiguration: audioConfig)
            
            // Setup event handlers
            setupRecognitionHandlers()
            
            // Start continuous recognition
            try await performContinuousRecognition()
            
        } catch {
            await MainActor.run {
                self.error = .transcriptionFailed(error.localizedDescription)
                self.isProcessing = false
            }
        }
    }
    
    private func setupRecognitionHandlers() {
        guard let recognizer = speechRecognizer else { return }
        
        // Handle intermediate results
        recognizer.addRecognizingEventHandler { [weak self] _, event in
            guard let self = self,
                  let result = event.result else { return }
            
            Task { @MainActor in
                // Update with partial results
                if !result.text.isEmpty {
                    self.transcriptionResult = result.text
                    
                    // Update progress based on audio position
                    if let duration = result.duration,
                       let offset = result.offset,
                       duration > 0 {
                        let progressValue = Double(offset) / Double(duration)
                        self.progress = min(progressValue, 0.9) // Cap at 90% during processing
                    }
                }
            }
        }
        
        // Handle final results
        recognizer.addRecognizedEventHandler { [weak self] _, event in
            guard let self = self,
                  let result = event.result else { return }
            
            Task { @MainActor in
                if result.reason == .recognizedSpeech {
                    // Append final recognized text
                    if !self.transcriptionResult.isEmpty && !result.text.isEmpty {
                        self.transcriptionResult += " "
                    }
                    self.transcriptionResult += result.text
                }
            }
        }
        
        // Handle cancellation
        recognizer.addCanceledEventHandler { [weak self] _, event in
            guard let self = self else { return }
            
            Task { @MainActor in
                if let details = event.cancellationDetails {
                    if details.reason == .error {
                        self.error = .transcriptionFailed(details.errorDetails ?? "Unknown error")
                    }
                }
                self.isProcessing = false
                self.progress = 0.0
            }
        }
        
        // Handle session stopped
        recognizer.addSessionStoppedEventHandler { [weak self] _, _ in
            guard let self = self else { return }
            
            Task { @MainActor in
                self.isProcessing = false
                self.progress = 1.0
            }
        }
    }
    
    private func performContinuousRecognition() async throws {
        guard let recognizer = speechRecognizer else {
            throw TranscriptionError.invalidConfiguration
        }
        
        return try await withCheckedThrowingContinuation { continuation in
            do {
                // Start continuous recognition
                try recognizer.startContinuousRecognition()
                
                // Wait for recognition to complete
                recognizer.addSessionStoppedEventHandler { _, _ in
                    continuation.resume()
                }
                
                // Also handle the case where all audio has been processed
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
                    guard let self = self else { return }
                    
                    // Monitor for completion
                    self.checkForCompletion(recognizer: recognizer) {
                        continuation.resume()
                    }
                }
                
            } catch {
                continuation.resume(throwing: error)
            }
        }
    }
    
    private func checkForCompletion(recognizer: SPXSpeechRecognizer, completion: @escaping () -> Void) {
        // Check if recognition has naturally completed
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak self] in
            guard let self = self else { return }
            
            if self.isProcessing {
                do {
                    try recognizer.stopContinuousRecognition()
                    completion()
                } catch {
                    Task { @MainActor in
                        self.error = .transcriptionFailed(error.localizedDescription)
                        self.isProcessing = false
                    }
                    completion()
                }
            }
        }
    }
    
    func cancelTranscription() {
        recognitionTask?.cancel()
        
        if let recognizer = speechRecognizer {
            do {
                try recognizer.stopContinuousRecognition()
            } catch {
                // Handle error silently as we're cancelling
            }
        }
        
        isProcessing = false
        progress = 0.0
    }
    
    deinit {
        cancelTranscription()
    }
}