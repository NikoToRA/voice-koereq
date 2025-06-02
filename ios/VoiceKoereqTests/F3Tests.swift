import XCTest
import Combine
@testable import VoiceKoereq

class F3Tests: XCTestCase {
    var viewModel: F3ViewModel!
    var mockService: MockF3Service!
    var cancellables: Set<AnyCancellable>!
    
    override func setUp() {
        super.setUp()
        mockService = MockF3Service()
        viewModel = F3ViewModel(transcriptionService: mockService)
        cancellables = Set<AnyCancellable>()
    }
    
    override func tearDown() {
        viewModel = nil
        mockService = nil
        cancellables = nil
        super.tearDown()
    }
    
    func testInitialState() {
        XCTAssertEqual(viewModel.transcribedText, "")
        XCTAssertFalse(viewModel.isTranscribing)
        XCTAssertEqual(viewModel.transcriptionProgress, 0.0)
        XCTAssertNil(viewModel.error)
        XCTAssertFalse(viewModel.hasAudioData)
        XCTAssertNil(viewModel.audioDuration)
    }
    
    func testSetAudioData() {
        // Given
        let testURL = URL(fileURLWithPath: "/test/audio.wav")
        
        // When
        viewModel.setAudioData(from: testURL)
        
        // Then
        XCTAssertTrue(viewModel.hasAudioData)
    }
    
    func testStartTranscriptionSuccess() async {
        // Given
        let testURL = URL(fileURLWithPath: "/test/audio.wav")
        let expectedText = "これはテストの音声です"
        mockService.mockTranscriptionResult = expectedText
        viewModel.setAudioData(from: testURL)
        
        // When
        await viewModel.startTranscription()
        
        // Wait for async updates
        let expectation = XCTestExpectation(description: "Transcription completes")
        
        viewModel.$transcribedText
            .dropFirst()
            .sink { text in
                if text == expectedText {
                    expectation.fulfill()
                }
            }
            .store(in: &cancellables)
        
        mockService.simulateTranscriptionSuccess()
        
        // Then
        await fulfillment(of: [expectation], timeout: 2.0)
        XCTAssertEqual(viewModel.transcribedText, expectedText)
        XCTAssertFalse(viewModel.isTranscribing)
        XCTAssertNil(viewModel.error)
    }
    
    func testStartTranscriptionWithoutAudioData() async {
        // When
        await viewModel.startTranscription()
        
        // Then
        XCTAssertNotNil(viewModel.error)
        if let error = viewModel.error as? TranscriptionError {
            switch error {
            case .noAudioData:
                XCTAssertTrue(true)
            default:
                XCTFail("Expected noAudioData error")
            }
        }
    }
    
    func testStartTranscriptionFailure() async {
        // Given
        let testURL = URL(fileURLWithPath: "/test/audio.wav")
        viewModel.setAudioData(from: testURL)
        mockService.shouldFailTranscription = true
        
        // When
        await viewModel.startTranscription()
        
        // Wait for async updates
        let expectation = XCTestExpectation(description: "Transcription fails")
        
        viewModel.$error
            .compactMap { $0 }
            .sink { _ in
                expectation.fulfill()
            }
            .store(in: &cancellables)
        
        mockService.simulateTranscriptionFailure()
        
        // Then
        await fulfillment(of: [expectation], timeout: 2.0)
        XCTAssertNotNil(viewModel.error)
        XCTAssertFalse(viewModel.isTranscribing)
    }
    
    func testClearTranscription() {
        // Given
        viewModel.transcribedText = "Some text"
        viewModel.transcriptionProgress = 0.5
        viewModel.error = TranscriptionError.networkError
        
        // When
        viewModel.clearTranscription()
        
        // Then
        XCTAssertEqual(viewModel.transcribedText, "")
        XCTAssertEqual(viewModel.transcriptionProgress, 0.0)
        XCTAssertNil(viewModel.error)
    }
    
    func testCancelTranscription() {
        // Given
        mockService.isProcessing = true
        
        // When
        viewModel.cancelTranscription()
        
        // Then
        XCTAssertTrue(mockService.cancelTranscriptionCalled)
        XCTAssertFalse(viewModel.isTranscribing)
        XCTAssertEqual(viewModel.transcriptionProgress, 0.0)
    }
    
    func testProgressUpdates() async {
        // Given
        let testURL = URL(fileURLWithPath: "/test/audio.wav")
        viewModel.setAudioData(from: testURL)
        
        let progressExpectation = XCTestExpectation(description: "Progress updates")
        var progressValues: [Double] = []
        
        viewModel.$transcriptionProgress
            .dropFirst()
            .sink { progress in
                progressValues.append(progress)
                if progress >= 1.0 {
                    progressExpectation.fulfill()
                }
            }
            .store(in: &cancellables)
        
        // When
        await viewModel.startTranscription()
        mockService.simulateProgressUpdates()
        
        // Then
        await fulfillment(of: [progressExpectation], timeout: 2.0)
        XCTAssertTrue(progressValues.contains { $0 > 0 && $0 < 1 })
        XCTAssertTrue(progressValues.last == 1.0)
    }
}

// MARK: - Mock Service

class MockF3Service: F3Service {
    var mockTranscriptionResult: String = ""
    var shouldFailTranscription: Bool = false
    var cancelTranscriptionCalled: Bool = false
    
    override func transcribeAudio(from url: URL) async {
        await MainActor.run {
            isProcessing = true
            progress = 0.0
        }
        
        if shouldFailTranscription {
            return // Will be triggered by simulateTranscriptionFailure
        }
        
        // Simulate async processing
        try? await Task.sleep(nanoseconds: 100_000_000) // 0.1 seconds
    }
    
    override func cancelTranscription() {
        cancelTranscriptionCalled = true
        super.cancelTranscription()
    }
    
    func simulateTranscriptionSuccess() {
        Task { @MainActor in
            self.transcriptionResult = mockTranscriptionResult
            self.isProcessing = false
            self.progress = 1.0
        }
    }
    
    func simulateTranscriptionFailure() {
        Task { @MainActor in
            self.error = .transcriptionFailed("Mock error")
            self.isProcessing = false
            self.progress = 0.0
        }
    }
    
    func simulateProgressUpdates() {
        Task { @MainActor in
            for i in 1...10 {
                self.progress = Double(i) / 10.0
                try? await Task.sleep(nanoseconds: 50_000_000) // 0.05 seconds
            }
            self.isProcessing = false
        }
    }
}