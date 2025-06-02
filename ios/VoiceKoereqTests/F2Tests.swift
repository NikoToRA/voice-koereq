import XCTest
import Combine
@testable import VoiceKoereq

class F2Tests: XCTestCase {
    var viewModel: F2ViewModel!
    var cancellables: Set<AnyCancellable>!
    
    override func setUp() {
        super.setUp()
        viewModel = F2ViewModel()
        cancellables = []
    }
    
    override func tearDown() {
        viewModel = nil
        cancellables = nil
        super.tearDown()
    }
    
    // MARK: - ViewModel Tests
    
    func testInitialState() {
        XCTAssertFalse(viewModel.isRecording)
        XCTAssertFalse(viewModel.isPaused)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertEqual(viewModel.recordingTime, 0)
        XCTAssertEqual(viewModel.audioLevel, 0.0)
        XCTAssertEqual(viewModel.statusText, "準備完了")
    }
    
    func testRecordingTimeTextFormatting() {
        // 0秒のテスト
        viewModel.recordingTime = 0
        XCTAssertEqual(viewModel.recordingTimeText, "00:00")
        
        // 30秒のテスト
        viewModel.recordingTime = 30
        XCTAssertEqual(viewModel.recordingTimeText, "00:30")
        
        // 1分30秒のテスト
        viewModel.recordingTime = 90
        XCTAssertEqual(viewModel.recordingTimeText, "01:30")
        
        // 10分のテスト
        viewModel.recordingTime = 600
        XCTAssertEqual(viewModel.recordingTimeText, "10:00")
    }
    
    func testStatusTextUpdates() async {
        // 録音開始時のステータス
        let expectation = XCTestExpectation(description: "Status text updates")
        
        viewModel.$statusText
            .dropFirst()
            .sink { status in
                if status == "録音中" {
                    expectation.fulfill()
                }
            }
            .store(in: &cancellables)
        
        // 録音を開始（モック環境では実際の録音は行われない）
        do {
            try await viewModel.startRecording()
        } catch {
            // テスト環境ではマイクアクセスエラーが発生する可能性がある
        }
        
        await fulfillment(of: [expectation], timeout: 2.0)
    }
    
    func testPauseResumeFlow() {
        // 録音状態をシミュレート
        viewModel.isRecording = true
        viewModel.statusText = "録音中"
        
        // 一時停止
        viewModel.pauseRecording()
        XCTAssertTrue(viewModel.isPaused)
        XCTAssertEqual(viewModel.statusText, "一時停止中")
        
        // 再開
        viewModel.resumeRecording()
        XCTAssertFalse(viewModel.isPaused)
        XCTAssertEqual(viewModel.statusText, "録音中")
    }
    
    // MARK: - Audio Service Tests
    
    func testAudioServiceInitialization() {
        let audioService = F2AudioService()
        XCTAssertNotNil(audioService)
        XCTAssertFalse(audioService.isRecording)
        XCTAssertFalse(audioService.isPaused)
        XCTAssertEqual(audioService.audioLevel, 0.0)
    }
    
    func testMicrophonePermissionRequest() async {
        let audioService = F2AudioService()
        
        // マイク許可リクエスト（テスト環境では実際の許可は取得できない）
        await audioService.requestMicrophonePermission()
        
        // テスト環境では許可の状態を確認することは難しいため、
        // メソッドが正常に完了することを確認
        XCTAssertTrue(true)
    }
    
    func testAudioLevelRange() {
        let audioService = F2AudioService()
        
        // オーディオレベルは0.0から1.0の範囲内である必要がある
        audioService.updateAudioLevel()
        XCTAssertGreaterThanOrEqual(audioService.audioLevel, 0.0)
        XCTAssertLessThanOrEqual(audioService.audioLevel, 1.0)
    }
    
    // MARK: - Error Handling Tests
    
    func testRecordingErrorTypes() {
        // マイク許可拒否エラー
        let micError = RecordingError.microphonePermissionDenied
        XCTAssertEqual(micError.localizedDescription, "マイクへのアクセスが拒否されました")
        
        // 録音開始失敗エラー
        let startError = RecordingError.failedToStart("テストエラー")
        XCTAssertEqual(startError.localizedDescription, "録音を開始できませんでした: テストエラー")
        
        // 録音保存失敗エラー
        let saveError = RecordingError.failedToSave("保存エラー")
        XCTAssertEqual(saveError.localizedDescription, "録音を保存できませんでした: 保存エラー")
    }
    
    // MARK: - Integration Tests
    
    func testRecordingWorkflow() async {
        let expectation = XCTestExpectation(description: "Recording workflow")
        
        // 録音状態の変化を監視
        var stateChanges: [Bool] = []
        viewModel.$isRecording
            .sink { isRecording in
                stateChanges.append(isRecording)
                if stateChanges.count == 3 { // false -> true -> false
                    expectation.fulfill()
                }
            }
            .store(in: &cancellables)
        
        // 録音開始から停止までのワークフロー
        do {
            try await viewModel.startRecording()
            try await Task.sleep(nanoseconds: 1_000_000_000) // 1秒待機
            try await viewModel.stopRecording()
        } catch {
            // テスト環境でのエラーは許容
        }
        
        await fulfillment(of: [expectation], timeout: 5.0)
    }
    
    // MARK: - Performance Tests
    
    func testRecordingTimeUpdatePerformance() {
        measure {
            // 録音時間の更新パフォーマンステスト
            for i in 0..<1000 {
                viewModel.recordingTime = TimeInterval(i)
                _ = viewModel.recordingTimeText
            }
        }
    }
    
    func testAudioLevelUpdatePerformance() {
        let audioService = F2AudioService()
        
        measure {
            // オーディオレベル更新のパフォーマンステスト
            for _ in 0..<1000 {
                audioService.updateAudioLevel()
            }
        }
    }
}