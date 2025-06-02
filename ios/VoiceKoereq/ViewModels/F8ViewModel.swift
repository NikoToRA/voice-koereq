import Foundation
import Combine
import SwiftUI

class F8ViewModel: ObservableObject {
    @Published var offlineRequests: [MedicalRequest] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var showError = false
    @Published var syncStatus: SyncStatus = .idle
    @Published var selectedRequest: MedicalRequest?
    @Published var showDeleteConfirmation = false
    @Published var requestToDelete: MedicalRequest?
    @Published var syncResult: SyncResult?
    @Published var showSyncResult = false
    
    enum SyncStatus {
        case idle
        case syncing
        case success(String)
        case failure(String)
    }
    
    private let service: F8ServiceProtocol
    private var cancellables = Set<AnyCancellable>()
    
    init(service: F8ServiceProtocol = F8Service.shared) {
        self.service = service
        loadOfflineRequests()
        setupNetworkMonitoring()
    }
    
    private func setupNetworkMonitoring() {
        // Monitor network status and auto-sync when online
        Timer.publish(every: 30, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                if NetworkMonitor.shared.isConnected && self?.hasUnsyncedData() == true {
                    self?.syncData()
                }
            }
            .store(in: &cancellables)
    }
    
    private func hasUnsyncedData() -> Bool {
        return offlineRequests.contains { !$0.isSynced }
    }
    
    func loadOfflineRequests() {
        isLoading = true
        errorMessage = nil
        
        service.getOfflineRequests()
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { [weak self] completion in
                    self?.isLoading = false
                    if case .failure(let error) = completion {
                        self?.errorMessage = error.localizedDescription
                        self?.showError = true
                    }
                },
                receiveValue: { [weak self] requests in
                    self?.offlineRequests = requests
                }
            )
            .store(in: &cancellables)
    }
    
    func saveRequest(patientName: String, symptoms: String, transcriptionText: String?, audioFilePath: String?) {
        let request = MedicalRequest(
            id: UUID(),
            patientName: patientName,
            symptoms: symptoms,
            timestamp: Date(),
            transcriptionText: transcriptionText,
            audioFilePath: audioFilePath,
            isSynced: false,
            lastModified: Date()
        )
        
        isLoading = true
        
        service.saveMedicalRequest(request)
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { [weak self] completion in
                    self?.isLoading = false
                    if case .failure(let error) = completion {
                        self?.errorMessage = error.localizedDescription
                        self?.showError = true
                    }
                },
                receiveValue: { [weak self] success in
                    if success {
                        self?.loadOfflineRequests()
                        
                        // Try to sync immediately if online
                        if NetworkMonitor.shared.isConnected {
                            self?.syncData()
                        }
                    }
                }
            )
            .store(in: &cancellables)
    }
    
    func syncData() {
        guard syncStatus != .syncing else { return }
        
        syncStatus = .syncing
        
        service.syncOfflineData()
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { [weak self] completion in
                    if case .failure(let error) = completion {
                        self?.syncStatus = .failure(error.localizedDescription)
                    }
                },
                receiveValue: { [weak self] result in
                    self?.syncResult = result
                    self?.showSyncResult = true
                    
                    if result.failureCount == 0 {
                        self?.syncStatus = .success("\(result.successCount)件のデータを同期しました")
                    } else {
                        self?.syncStatus = .failure("\(result.successCount)件成功、\(result.failureCount)件失敗")
                    }
                    
                    // Reload data after sync
                    self?.loadOfflineRequests()
                    
                    // Reset status after delay
                    DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                        self?.syncStatus = .idle
                    }
                }
            )
            .store(in: &cancellables)
    }
    
    func deleteRequest(_ request: MedicalRequest) {
        requestToDelete = request
        showDeleteConfirmation = true
    }
    
    func confirmDelete() {
        guard let request = requestToDelete else { return }
        
        isLoading = true
        
        service.deleteOfflineRequest(id: request.id)
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { [weak self] completion in
                    self?.isLoading = false
                    if case .failure(let error) = completion {
                        self?.errorMessage = error.localizedDescription
                        self?.showError = true
                    }
                },
                receiveValue: { [weak self] success in
                    if success {
                        self?.loadOfflineRequests()
                    }
                    self?.requestToDelete = nil
                    self?.showDeleteConfirmation = false
                }
            )
            .store(in: &cancellables)
    }
    
    func clearAllData() {
        isLoading = true
        
        service.clearAllOfflineData()
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { [weak self] completion in
                    self?.isLoading = false
                    if case .failure(let error) = completion {
                        self?.errorMessage = error.localizedDescription
                        self?.showError = true
                    }
                },
                receiveValue: { [weak self] success in
                    if success {
                        self?.offlineRequests = []
                    }
                }
            )
            .store(in: &cancellables)
    }
    
    func selectRequest(_ request: MedicalRequest) {
        selectedRequest = request
    }
    
    var unsyncedCount: Int {
        offlineRequests.filter { !$0.isSynced }.count
    }
    
    var totalCount: Int {
        offlineRequests.count
    }
    
    var isOnline: Bool {
        NetworkMonitor.shared.isConnected
    }
    
    func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ja_JP")
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}