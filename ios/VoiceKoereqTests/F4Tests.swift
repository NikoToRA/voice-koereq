import XCTest
import Combine
@testable import VoiceKoereq

class F4Tests: XCTestCase {
    var viewModel: F4ViewModel!
    var cancellables: Set<AnyCancellable>!
    
    @MainActor override func setUp() {
        super.setUp()
        viewModel = F4ViewModel()
        cancellables = Set<AnyCancellable>()
    }
    
    override func tearDown() {
        viewModel = nil
        cancellables = nil
        super.tearDown()
    }
    
    @MainActor func testInitialMessage() {
        // 初期メッセージが表示されることを確認
        XCTAssertEqual(viewModel.messages.count, 1)
        XCTAssertFalse(viewModel.messages[0].isUser)
        XCTAssertTrue(viewModel.messages[0].content.contains("AI医療アシスタント"))
    }
    
    @MainActor func testSendMessage() async {
        // Given
        let testMessage = "頭痛がします"
        let initialMessageCount = viewModel.messages.count
        
        // When
        await viewModel.sendMessage(testMessage)
        
        // Then
        XCTAssertGreaterThan(viewModel.messages.count, initialMessageCount)
        
        // ユーザーメッセージが追加されていることを確認
        let userMessage = viewModel.messages[viewModel.messages.count - 2]
        XCTAssertTrue(userMessage.isUser)
        XCTAssertEqual(userMessage.content, testMessage)
    }
    
    @MainActor func testClearConversation() {
        // Given
        Task {
            await viewModel.sendMessage("テストメッセージ")
        }
        
        // When
        viewModel.clearConversation()
        
        // Then
        XCTAssertEqual(viewModel.messages.count, 1)
        XCTAssertFalse(viewModel.messages[0].isUser)
    }
    
    @MainActor func testProcessingState() async {
        // Given
        let expectation = XCTestExpectation(description: "Processing state changes")
        var processingStates: [Bool] = []
        
        viewModel.$isProcessing
            .sink { isProcessing in
                processingStates.append(isProcessing)
                if processingStates.count >= 3 {
                    expectation.fulfill()
                }
            }
            .store(in: &cancellables)
        
        // When
        await viewModel.sendMessage("テスト")
        
        // Then
        await fulfillment(of: [expectation], timeout: 5.0)
        
        // 処理中フラグが適切に変更されることを確認
        XCTAssertTrue(processingStates.contains(true))
        XCTAssertTrue(processingStates.contains(false))
    }
    
    func testMessageModel() {
        // Given
        let content = "テストメッセージ"
        let isUser = true
        
        // When
        let message = Message(content: content, isUser: isUser)
        
        // Then
        XCTAssertNotNil(message.id)
        XCTAssertEqual(message.content, content)
        XCTAssertEqual(message.isUser, isUser)
        XCTAssertNotNil(message.timestamp)
    }
    
    func testF4ServiceError() {
        // エラーケースのテスト
        let errors: [F4ServiceError] = [
            .invalidURL,
            .networkError("ネットワークエラー"),
            .decodingError,
            .audioProcessingError,
            .azureAPIError("APIエラー")
        ]
        
        for error in errors {
            XCTAssertNotNil(error)
        }
    }
}

// MARK: - Mock Service for Testing
class MockF4Service: F4Service {
    var shouldThrowError = false
    var mockResponse = "これはテスト応答です。"
    var mockTranscription = "これはテスト音声認識結果です。"
    
    override func getMedicalAssistantResponse(for query: String) async throws -> String {
        if shouldThrowError {
            throw F4ServiceError.networkError("Mock error")
        }
        return mockResponse
    }
    
    override func transcribeAudio(at url: URL) async throws -> String {
        if shouldThrowError {
            throw F4ServiceError.audioProcessingError
        }
        return mockTranscription
    }
}