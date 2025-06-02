import XCTest
import Combine
@testable import VoiceKoereq

class F7Tests: XCTestCase {
    var cancellables: Set<AnyCancellable>!
    var mockService: MockF7Service!
    var viewModel: F7ViewModel!
    
    override func setUp() {
        super.setUp()
        cancellables = []
        mockService = MockF7Service()
        viewModel = F7ViewModel(service: mockService)
    }
    
    override func tearDown() {
        cancellables = nil
        mockService = nil
        viewModel = nil
        super.tearDown()
    }
    
    func testLoadSummariesSuccess() {
        let expectation = XCTestExpectation(description: "Load summaries")
        
        viewModel.loadSummaries()
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            XCTAssertFalse(self.viewModel.isLoading)
            XCTAssertEqual(self.viewModel.summaries.count, 2)
            XCTAssertNil(self.viewModel.errorMessage)
            expectation.fulfill()
        }
        
        wait(for: [expectation], timeout: 1.0)
    }
    
    func testLoadSummariesFailure() {
        let expectation = XCTestExpectation(description: "Load summaries failure")
        mockService.shouldFail = true
        
        viewModel.loadSummaries()
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            XCTAssertFalse(self.viewModel.isLoading)
            XCTAssertTrue(self.viewModel.summaries.isEmpty)
            XCTAssertNotNil(self.viewModel.errorMessage)
            XCTAssertTrue(self.viewModel.showError)
            expectation.fulfill()
        }
        
        wait(for: [expectation], timeout: 1.0)
    }
    
    func testGenerateSummarySuccess() {
        let expectation = XCTestExpectation(description: "Generate summary")
        let transcriptionId = "test-trans-123"
        
        viewModel.generateSummary(from: transcriptionId)
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
            XCTAssertFalse(self.viewModel.isLoading)
            XCTAssertEqual(self.viewModel.summaries.count, 1)
            XCTAssertNotNil(self.viewModel.selectedSummary)
            XCTAssertEqual(self.viewModel.selectedSummary?.transcriptionId, transcriptionId)
            expectation.fulfill()
        }
        
        wait(for: [expectation], timeout: 1.0)
    }
    
    func testGenerateSummaryFailure() {
        let expectation = XCTestExpectation(description: "Generate summary failure")
        mockService.shouldFail = true
        
        viewModel.generateSummary(from: "test-trans")
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            XCTAssertFalse(self.viewModel.isLoading)
            XCTAssertNotNil(self.viewModel.errorMessage)
            XCTAssertTrue(self.viewModel.showError)
            expectation.fulfill()
        }
        
        wait(for: [expectation], timeout: 1.0)
    }
    
    func testDeleteSummarySuccess() {
        let expectation = XCTestExpectation(description: "Delete summary")
        
        // First load summaries
        viewModel.loadSummaries()
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            let summaryToDelete = self.viewModel.summaries.first!
            self.viewModel.selectSummary(summaryToDelete)
            self.viewModel.deleteSummary(summaryToDelete)
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                XCTAssertFalse(self.viewModel.isLoading)
                XCTAssertEqual(self.viewModel.summaries.count, 1)
                XCTAssertNil(self.viewModel.selectedSummary)
                expectation.fulfill()
            }
        }
        
        wait(for: [expectation], timeout: 2.0)
    }
    
    func testDeleteSummaryFailure() {
        let expectation = XCTestExpectation(description: "Delete summary failure")
        
        viewModel.loadSummaries()
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            self.mockService.shouldFail = true
            let summaryToDelete = self.viewModel.summaries.first!
            self.viewModel.deleteSummary(summaryToDelete)
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                XCTAssertFalse(self.viewModel.isLoading)
                XCTAssertEqual(self.viewModel.summaries.count, 2) // Not deleted
                XCTAssertNotNil(self.viewModel.errorMessage)
                expectation.fulfill()
            }
        }
        
        wait(for: [expectation], timeout: 2.0)
    }
    
    func testFilteredSummaries() {
        viewModel.summaries = mockService.summaries
        
        // Test no filter
        XCTAssertEqual(viewModel.filteredSummaries.count, 2)
        
        // Test filter by title
        viewModel.searchText = "初診"
        XCTAssertEqual(viewModel.filteredSummaries.count, 1)
        XCTAssertEqual(viewModel.filteredSummaries.first?.title, "初診患者の症状相談")
        
        // Test filter by content
        viewModel.searchText = "薬"
        XCTAssertEqual(viewModel.filteredSummaries.count, 2) // Both contain "薬"
        
        // Test filter with no results
        viewModel.searchText = "存在しないテキスト"
        XCTAssertEqual(viewModel.filteredSummaries.count, 0)
        XCTAssertTrue(viewModel.hasNoFilteredResults)
    }
    
    func testSortOrder() {
        viewModel.summaries = mockService.summaries
        
        // Test date descending (default)
        let sortedDefault = viewModel.filteredSummaries
        XCTAssertTrue(sortedDefault[0].generatedAt > sortedDefault[1].generatedAt)
        
        // Test date ascending
        viewModel.changeSortOrder(to: .dateAscending)
        let sortedAsc = viewModel.filteredSummaries
        XCTAssertTrue(sortedAsc[0].generatedAt < sortedAsc[1].generatedAt)
        
        // Test title ascending
        viewModel.changeSortOrder(to: .titleAscending)
        let sortedTitleAsc = viewModel.filteredSummaries
        XCTAssertTrue(sortedTitleAsc[0].title < sortedTitleAsc[1].title)
        
        // Test title descending
        viewModel.changeSortOrder(to: .titleDescending)
        let sortedTitleDesc = viewModel.filteredSummaries
        XCTAssertTrue(sortedTitleDesc[0].title > sortedTitleDesc[1].title)
    }
    
    func testExportSummary() {
        let summary = mockService.summaries.first!
        let exportedText = viewModel.exportSummary(summary)
        
        XCTAssertTrue(exportedText.contains("【診察サマリー】"))
        XCTAssertTrue(exportedText.contains(summary.title))
        XCTAssertTrue(exportedText.contains(summary.summary))
        XCTAssertTrue(exportedText.contains("【要点】"))
        
        for point in summary.keyPoints {
            XCTAssertTrue(exportedText.contains(point))
        }
    }
    
    func testRefreshSummary() {
        let expectation = XCTestExpectation(description: "Refresh summary")
        
        viewModel.summaries = mockService.summaries
        let summaryId = mockService.summaries.first!.id
        
        viewModel.refreshSummary(summaryId)
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            XCTAssertFalse(self.viewModel.isLoading)
            XCTAssertNil(self.viewModel.errorMessage)
            expectation.fulfill()
        }
        
        wait(for: [expectation], timeout: 1.0)
    }
    
    func testServiceErrorHandling() {
        let expectation = XCTestExpectation(description: "Service error handling")
        
        let service = F7Service()
        
        // Test invalid transcription ID
        service.generateSummary(from: "")
            .sink(
                receiveCompletion: { completion in
                    if case .failure(let error) = completion {
                        XCTAssertEqual(error, F7ServiceError.invalidTranscriptionId)
                        expectation.fulfill()
                    }
                },
                receiveValue: { _ in
                    XCTFail("Should not receive value")
                }
            )
            .store(in: &cancellables)
        
        wait(for: [expectation], timeout: 1.0)
    }
}