import Foundation
import Combine

class F7ViewModel: ObservableObject {
    @Published var summaries: [F7Summary] = []
    @Published var selectedSummary: F7Summary?
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var showError = false
    @Published var searchText = ""
    @Published var sortOrder: SortOrder = .dateDescending
    
    enum SortOrder {
        case dateAscending
        case dateDescending
        case titleAscending
        case titleDescending
    }
    
    private let service: F7ServiceProtocol
    private var cancellables = Set<AnyCancellable>()
    
    var filteredSummaries: [F7Summary] {
        let filtered = searchText.isEmpty ? summaries : summaries.filter { summary in
            summary.title.localizedCaseInsensitiveContains(searchText) ||
            summary.summary.localizedCaseInsensitiveContains(searchText) ||
            summary.keyPoints.joined().localizedCaseInsensitiveContains(searchText)
        }
        
        return filtered.sorted { lhs, rhs in
            switch sortOrder {
            case .dateAscending:
                return lhs.generatedAt < rhs.generatedAt
            case .dateDescending:
                return lhs.generatedAt > rhs.generatedAt
            case .titleAscending:
                return lhs.title < rhs.title
            case .titleDescending:
                return lhs.title > rhs.title
            }
        }
    }
    
    var hasNoResults: Bool {
        !isLoading && summaries.isEmpty
    }
    
    var hasNoFilteredResults: Bool {
        !isLoading && !summaries.isEmpty && filteredSummaries.isEmpty
    }
    
    init(service: F7ServiceProtocol = F7Service()) {
        self.service = service
    }
    
    func loadSummaries() {
        isLoading = true
        errorMessage = nil
        
        service.getSummaries()
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { [weak self] completion in
                    self?.isLoading = false
                    if case .failure(let error) = completion {
                        self?.errorMessage = error.localizedDescription
                        self?.showError = true
                    }
                },
                receiveValue: { [weak self] summaries in
                    self?.summaries = summaries
                }
            )
            .store(in: &cancellables)
    }
    
    func generateSummary(from transcriptionId: String) {
        isLoading = true
        errorMessage = nil
        
        service.generateSummary(from: transcriptionId)
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { [weak self] completion in
                    self?.isLoading = false
                    if case .failure(let error) = completion {
                        self?.errorMessage = error.localizedDescription
                        self?.showError = true
                    }
                },
                receiveValue: { [weak self] summary in
                    self?.summaries.insert(summary, at: 0)
                    self?.selectedSummary = summary
                }
            )
            .store(in: &cancellables)
    }
    
    func selectSummary(_ summary: F7Summary) {
        selectedSummary = summary
    }
    
    func deleteSummary(_ summary: F7Summary) {
        isLoading = true
        errorMessage = nil
        
        service.deleteSummary(id: summary.id)
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { [weak self] completion in
                    self?.isLoading = false
                    if case .failure(let error) = completion {
                        self?.errorMessage = error.localizedDescription
                        self?.showError = true
                    }
                },
                receiveValue: { [weak self] _ in
                    self?.summaries.removeAll { $0.id == summary.id }
                    if self?.selectedSummary?.id == summary.id {
                        self?.selectedSummary = nil
                    }
                }
            )
            .store(in: &cancellables)
    }
    
    func refreshSummary(_ summaryId: String) {
        isLoading = true
        errorMessage = nil
        
        service.getSummary(id: summaryId)
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { [weak self] completion in
                    self?.isLoading = false
                    if case .failure(let error) = completion {
                        self?.errorMessage = error.localizedDescription
                        self?.showError = true
                    }
                },
                receiveValue: { [weak self] updatedSummary in
                    guard let self = self else { return }
                    
                    if let index = self.summaries.firstIndex(where: { $0.id == updatedSummary.id }) {
                        self.summaries[index] = updatedSummary
                    }
                    
                    if self.selectedSummary?.id == updatedSummary.id {
                        self.selectedSummary = updatedSummary
                    }
                }
            )
            .store(in: &cancellables)
    }
    
    func clearSearch() {
        searchText = ""
    }
    
    func changeSortOrder(to order: SortOrder) {
        sortOrder = order
    }
    
    func exportSummary(_ summary: F7Summary) -> String {
        let dateFormatter = DateFormatter()
        dateFormatter.dateStyle = .long
        dateFormatter.timeStyle = .short
        dateFormatter.locale = Locale(identifier: "ja_JP")
        
        var exportText = "【診察サマリー】\n"
        exportText += "生成日時: \(dateFormatter.string(from: summary.generatedAt))\n"
        exportText += "タイトル: \(summary.title)\n\n"
        exportText += "【サマリー】\n\(summary.summary)\n\n"
        exportText += "【要点】\n"
        for (index, point) in summary.keyPoints.enumerated() {
            exportText += "\(index + 1). \(point)\n"
        }
        
        return exportText
    }
}