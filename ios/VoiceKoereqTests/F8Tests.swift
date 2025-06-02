import XCTest
import Combine
@testable import VoiceKoereq

class F8Tests: XCTestCase {
    var cancellables: Set<AnyCancellable>!
    var mockService: MockF8Service!
    var viewModel: F8ViewModel!
    
    override func setUp() {
        super.setUp()
        cancellables = Set<AnyCancellable>()
        mockService = MockF8Service()
        viewModel = F8ViewModel(service: mockService)
    }
    
    override func tearDown() {
        cancellables = nil
        mockService = nil
        viewModel = nil
        super.tearDown()
    }
    
    // MARK: - Service Tests
    
    func testSaveMedicalRequest() {
        let expectation = XCTestExpectation(description: "Save medical request")
        let request = MedicalRequest(
            id: UUID(),
            patientName: "テスト患者",
            symptoms: "頭痛",
            timestamp: Date(),
            transcriptionText: nil,
            audioFilePath: nil,
            isSynced: false,
            lastModified: Date()
        )
        
        mockService.saveMedicalRequest(request)
            .sink(
                receiveCompletion: { completion in
                    if case .failure = completion {
                        XCTFail("Save request should not fail")
                    }
                },
                receiveValue: { success in
                    XCTAssertTrue(success, "Save should return true")
                    expectation.fulfill()
                }
            )
            .store(in: &cancellables)
        
        wait(for: [expectation], timeout: 1.0)
    }
    
    func testGetOfflineRequests() {
        let expectation = XCTestExpectation(description: "Get offline requests")
        
        mockService.getOfflineRequests()
            .sink(
                receiveCompletion: { completion in
                    if case .failure = completion {
                        XCTFail("Get requests should not fail")
                    }
                },
                receiveValue: { requests in
                    XCTAssertEqual(requests.count, 2, "Should return 2 mock requests")
                    XCTAssertEqual(requests[0].patientName, "テスト患者")
                    XCTAssertEqual(requests[1].patientName, "サンプル患者")
                    expectation.fulfill()
                }
            )
            .store(in: &cancellables)
        
        wait(for: [expectation], timeout: 1.0)
    }
    
    func testSyncOfflineData() {
        let expectation = XCTestExpectation(description: "Sync offline data")
        
        mockService.syncOfflineData()
            .sink(
                receiveCompletion: { completion in
                    if case .failure = completion {
                        XCTFail("Sync should not fail")
                    }
                },
                receiveValue: { result in
                    XCTAssertEqual(result.successCount, 2, "Should sync 2 requests successfully")
                    XCTAssertEqual(result.failureCount, 0, "Should have no failures")
                    XCTAssertTrue(result.conflicts.isEmpty, "Should have no conflicts")
                    expectation.fulfill()
                }
            )
            .store(in: &cancellables)
        
        wait(for: [expectation], timeout: 1.0)
    }
    
    func testDeleteOfflineRequest() {
        let expectation = XCTestExpectation(description: "Delete offline request")
        let requestId = UUID()
        
        mockService.deleteOfflineRequest(id: requestId)
            .sink(
                receiveCompletion: { completion in
                    if case .failure = completion {
                        XCTFail("Delete should not fail")
                    }
                },
                receiveValue: { success in
                    XCTAssertTrue(success, "Delete should return true")
                    expectation.fulfill()
                }
            )
            .store(in: &cancellables)
        
        wait(for: [expectation], timeout: 1.0)
    }
    
    func testClearAllOfflineData() {
        let expectation = XCTestExpectation(description: "Clear all offline data")
        
        mockService.clearAllOfflineData()
            .sink(
                receiveCompletion: { completion in
                    if case .failure = completion {
                        XCTFail("Clear all should not fail")
                    }
                },
                receiveValue: { success in
                    XCTAssertTrue(success, "Clear all should return true")
                    expectation.fulfill()
                }
            )
            .store(in: &cancellables)
        
        wait(for: [expectation], timeout: 1.0)
    }
    
    // MARK: - ViewModel Tests
    
    func testViewModelInitialState() {
        XCTAssertFalse(viewModel.isLoading, "Should not be loading initially")
        XCTAssertNil(viewModel.errorMessage, "Should have no error initially")
        XCTAssertEqual(viewModel.syncStatus, .idle, "Sync status should be idle")
        XCTAssertNil(viewModel.selectedRequest, "Should have no selected request")
        XCTAssertFalse(viewModel.showDeleteConfirmation, "Should not show delete confirmation")
    }
    
    func testViewModelLoadRequests() {
        let expectation = XCTestExpectation(description: "Load requests in view model")
        
        // Wait for initial load to complete
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            XCTAssertEqual(self.viewModel.offlineRequests.count, 2, "Should load 2 requests")
            XCTAssertEqual(self.viewModel.unsyncedCount, 1, "Should have 1 unsynced request")
            XCTAssertEqual(self.viewModel.totalCount, 2, "Should have 2 total requests")
            expectation.fulfill()
        }
        
        wait(for: [expectation], timeout: 1.0)
    }
    
    func testViewModelSaveRequest() {
        let expectation = XCTestExpectation(description: "Save request via view model")
        
        viewModel.saveRequest(
            patientName: "新規患者",
            symptoms: "発熱",
            transcriptionText: "38度の熱があります",
            audioFilePath: nil
        )
        
        // Wait for save to complete
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            XCTAssertFalse(self.viewModel.isLoading, "Should not be loading after save")
            expectation.fulfill()
        }
        
        wait(for: [expectation], timeout: 1.0)
    }
    
    func testViewModelSyncData() {
        let expectation = XCTestExpectation(description: "Sync data via view model")
        
        viewModel.syncData()
        
        // Check sync status changes
        XCTAssertEqual(viewModel.syncStatus, .syncing, "Should be syncing")
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            if case .success(let message) = self.viewModel.syncStatus {
                XCTAssertTrue(message.contains("2件"), "Success message should contain count")
            } else {
                XCTFail("Sync should succeed")
            }
            expectation.fulfill()
        }
        
        wait(for: [expectation], timeout: 1.0)
    }
    
    func testViewModelDeleteRequest() {
        let expectation = XCTestExpectation(description: "Delete request via view model")
        
        // First load requests
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            guard let firstRequest = self.viewModel.offlineRequests.first else {
                XCTFail("Should have requests loaded")
                return
            }
            
            // Trigger delete
            self.viewModel.deleteRequest(firstRequest)
            XCTAssertTrue(self.viewModel.showDeleteConfirmation, "Should show delete confirmation")
            XCTAssertEqual(self.viewModel.requestToDelete?.id, firstRequest.id, "Should set request to delete")
            
            // Confirm delete
            self.viewModel.confirmDelete()
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                XCTAssertFalse(self.viewModel.showDeleteConfirmation, "Should hide delete confirmation")
                XCTAssertNil(self.viewModel.requestToDelete, "Should clear request to delete")
                expectation.fulfill()
            }
        }
        
        wait(for: [expectation], timeout: 2.0)
    }
    
    func testViewModelClearAllData() {
        let expectation = XCTestExpectation(description: "Clear all data via view model")
        
        viewModel.clearAllData()
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            XCTAssertFalse(self.viewModel.isLoading, "Should not be loading after clear")
            expectation.fulfill()
        }
        
        wait(for: [expectation], timeout: 1.0)
    }
    
    func testViewModelDateFormatting() {
        let date = Date(timeIntervalSince1970: 1609459200) // 2021-01-01 00:00:00 UTC
        let formatted = viewModel.formatDate(date)
        
        // The exact format depends on the locale and timezone
        XCTAssertFalse(formatted.isEmpty, "Formatted date should not be empty")
        XCTAssertTrue(formatted.contains("2021") || formatted.contains("21"), "Should contain year")
    }
    
    // MARK: - Error Handling Tests
    
    func testF8ServiceErrorDescriptions() {
        let persistenceError = F8ServiceError.persistenceError("Test error")
        XCTAssertEqual(persistenceError.errorDescription, "データ保存エラー: Test error")
        
        let syncError = F8ServiceError.syncError("Sync failed")
        XCTAssertEqual(syncError.errorDescription, "同期エラー: Sync failed")
        
        let networkError = F8ServiceError.networkUnavailable
        XCTAssertEqual(networkError.errorDescription, "ネットワーク接続がありません")
        
        let corruptionError = F8ServiceError.dataCorruption
        XCTAssertEqual(corruptionError.errorDescription, "データが破損しています")
    }
    
    // MARK: - Model Tests
    
    func testMedicalRequestModel() {
        let id = UUID()
        let now = Date()
        let request = MedicalRequest(
            id: id,
            patientName: "患者A",
            symptoms: "症状",
            timestamp: now,
            transcriptionText: "詳細",
            audioFilePath: "/path/to/audio",
            isSynced: true,
            lastModified: now
        )
        
        XCTAssertEqual(request.id, id)
        XCTAssertEqual(request.patientName, "患者A")
        XCTAssertEqual(request.symptoms, "症状")
        XCTAssertEqual(request.timestamp, now)
        XCTAssertEqual(request.transcriptionText, "詳細")
        XCTAssertEqual(request.audioFilePath, "/path/to/audio")
        XCTAssertTrue(request.isSynced)
        XCTAssertEqual(request.lastModified, now)
    }
    
    func testSyncResultModel() {
        let conflict = SyncConflict(
            localData: MedicalRequest(
                id: UUID(),
                patientName: "Local",
                symptoms: "Local symptoms",
                timestamp: Date(),
                transcriptionText: nil,
                audioFilePath: nil,
                isSynced: false,
                lastModified: Date()
            ),
            serverData: MedicalRequest(
                id: UUID(),
                patientName: "Server",
                symptoms: "Server symptoms",
                timestamp: Date(),
                transcriptionText: nil,
                audioFilePath: nil,
                isSynced: true,
                lastModified: Date()
            ),
            resolution: .useServer
        )
        
        let result = SyncResult(
            successCount: 5,
            failureCount: 2,
            conflicts: [conflict]
        )
        
        XCTAssertEqual(result.successCount, 5)
        XCTAssertEqual(result.failureCount, 2)
        XCTAssertEqual(result.conflicts.count, 1)
        XCTAssertEqual(result.conflicts[0].resolution, .useServer)
    }
}