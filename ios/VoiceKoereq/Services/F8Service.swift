import Foundation
import CoreData
import Combine

protocol F8ServiceProtocol {
    func saveMedicalRequest(_ request: MedicalRequest) -> AnyPublisher<Bool, Error>
    func getOfflineRequests() -> AnyPublisher<[MedicalRequest], Error>
    func syncOfflineData() -> AnyPublisher<SyncResult, Error>
    func deleteOfflineRequest(id: UUID) -> AnyPublisher<Bool, Error>
    func clearAllOfflineData() -> AnyPublisher<Bool, Error>
}

struct MedicalRequest: Codable, Identifiable {
    let id: UUID
    let patientName: String
    let symptoms: String
    let timestamp: Date
    let transcriptionText: String?
    let audioFilePath: String?
    let isSynced: Bool
    let lastModified: Date
}

struct SyncResult {
    let successCount: Int
    let failureCount: Int
    let conflicts: [SyncConflict]
}

struct SyncConflict {
    let localData: MedicalRequest
    let serverData: MedicalRequest
    let resolution: ConflictResolution
}

enum ConflictResolution {
    case useLocal
    case useServer
    case merge
}

enum F8ServiceError: LocalizedError {
    case persistenceError(String)
    case syncError(String)
    case networkUnavailable
    case dataCorruption
    
    var errorDescription: String? {
        switch self {
        case .persistenceError(let message):
            return "データ保存エラー: \(message)"
        case .syncError(let message):
            return "同期エラー: \(message)"
        case .networkUnavailable:
            return "ネットワーク接続がありません"
        case .dataCorruption:
            return "データが破損しています"
        }
    }
}

class F8Service: F8ServiceProtocol {
    static let shared = F8Service()
    
    private let persistentContainer: NSPersistentContainer
    private let networkService: NetworkService
    private let syncQueue = DispatchQueue(label: "com.voicekoereq.sync", qos: .background)
    
    private init() {
        self.networkService = NetworkService.shared
        
        persistentContainer = NSPersistentContainer(name: "VoiceKoeReqOffline")
        persistentContainer.loadPersistentStores { storeDescription, error in
            if let error = error as NSError? {
                fatalError("Core Data初期化エラー: \(error), \(error.userInfo)")
            }
        }
        
        setupCoreDataModel()
    }
    
    private func setupCoreDataModel() {
        let model = NSManagedObjectModel()
        
        // MedicalRequestEntity
        let requestEntity = NSEntityDescription()
        requestEntity.name = "MedicalRequestEntity"
        requestEntity.managedObjectClassName = NSStringFromClass(MedicalRequestEntity.self)
        
        let idAttribute = NSAttributeDescription()
        idAttribute.name = "id"
        idAttribute.attributeType = .UUIDAttributeType
        idAttribute.isOptional = false
        
        let patientNameAttribute = NSAttributeDescription()
        patientNameAttribute.name = "patientName"
        patientNameAttribute.attributeType = .stringAttributeType
        patientNameAttribute.isOptional = false
        
        let symptomsAttribute = NSAttributeDescription()
        symptomsAttribute.name = "symptoms"
        symptomsAttribute.attributeType = .stringAttributeType
        symptomsAttribute.isOptional = false
        
        let timestampAttribute = NSAttributeDescription()
        timestampAttribute.name = "timestamp"
        timestampAttribute.attributeType = .dateAttributeType
        timestampAttribute.isOptional = false
        
        let transcriptionTextAttribute = NSAttributeDescription()
        transcriptionTextAttribute.name = "transcriptionText"
        transcriptionTextAttribute.attributeType = .stringAttributeType
        transcriptionTextAttribute.isOptional = true
        
        let audioFilePathAttribute = NSAttributeDescription()
        audioFilePathAttribute.name = "audioFilePath"
        audioFilePathAttribute.attributeType = .stringAttributeType
        audioFilePathAttribute.isOptional = true
        
        let isSyncedAttribute = NSAttributeDescription()
        isSyncedAttribute.name = "isSynced"
        isSyncedAttribute.attributeType = .booleanAttributeType
        isSyncedAttribute.isOptional = false
        isSyncedAttribute.defaultValue = false
        
        let lastModifiedAttribute = NSAttributeDescription()
        lastModifiedAttribute.name = "lastModified"
        lastModifiedAttribute.attributeType = .dateAttributeType
        lastModifiedAttribute.isOptional = false
        
        requestEntity.properties = [
            idAttribute,
            patientNameAttribute,
            symptomsAttribute,
            timestampAttribute,
            transcriptionTextAttribute,
            audioFilePathAttribute,
            isSyncedAttribute,
            lastModifiedAttribute
        ]
        
        model.entities = [requestEntity]
        persistentContainer.managedObjectModel = model
    }
    
    func saveMedicalRequest(_ request: MedicalRequest) -> AnyPublisher<Bool, Error> {
        Future<Bool, Error> { [weak self] promise in
            guard let self = self else {
                promise(.failure(F8ServiceError.persistenceError("サービスが利用できません")))
                return
            }
            
            self.syncQueue.async {
                let context = self.persistentContainer.viewContext
                
                let fetchRequest = NSFetchRequest<MedicalRequestEntity>(entityName: "MedicalRequestEntity")
                fetchRequest.predicate = NSPredicate(format: "id == %@", request.id as CVarArg)
                
                do {
                    let existingRequests = try context.fetch(fetchRequest)
                    let entity: MedicalRequestEntity
                    
                    if let existing = existingRequests.first {
                        entity = existing
                    } else {
                        entity = MedicalRequestEntity(context: context)
                    }
                    
                    entity.id = request.id
                    entity.patientName = request.patientName
                    entity.symptoms = request.symptoms
                    entity.timestamp = request.timestamp
                    entity.transcriptionText = request.transcriptionText
                    entity.audioFilePath = request.audioFilePath
                    entity.isSynced = false
                    entity.lastModified = Date()
                    
                    try context.save()
                    promise(.success(true))
                } catch {
                    promise(.failure(F8ServiceError.persistenceError(error.localizedDescription)))
                }
            }
        }
        .eraseToAnyPublisher()
    }
    
    func getOfflineRequests() -> AnyPublisher<[MedicalRequest], Error> {
        Future<[MedicalRequest], Error> { [weak self] promise in
            guard let self = self else {
                promise(.failure(F8ServiceError.persistenceError("サービスが利用できません")))
                return
            }
            
            let context = self.persistentContainer.viewContext
            let fetchRequest = NSFetchRequest<MedicalRequestEntity>(entityName: "MedicalRequestEntity")
            fetchRequest.sortDescriptors = [NSSortDescriptor(key: "timestamp", ascending: false)]
            
            do {
                let entities = try context.fetch(fetchRequest)
                let requests = entities.map { entity in
                    MedicalRequest(
                        id: entity.id ?? UUID(),
                        patientName: entity.patientName ?? "",
                        symptoms: entity.symptoms ?? "",
                        timestamp: entity.timestamp ?? Date(),
                        transcriptionText: entity.transcriptionText,
                        audioFilePath: entity.audioFilePath,
                        isSynced: entity.isSynced,
                        lastModified: entity.lastModified ?? Date()
                    )
                }
                promise(.success(requests))
            } catch {
                promise(.failure(F8ServiceError.persistenceError(error.localizedDescription)))
            }
        }
        .eraseToAnyPublisher()
    }
    
    func syncOfflineData() -> AnyPublisher<SyncResult, Error> {
        Future<SyncResult, Error> { [weak self] promise in
            guard let self = self else {
                promise(.failure(F8ServiceError.syncError("サービスが利用できません")))
                return
            }
            
            // Check network availability
            guard NetworkMonitor.shared.isConnected else {
                promise(.failure(F8ServiceError.networkUnavailable))
                return
            }
            
            self.syncQueue.async {
                let context = self.persistentContainer.viewContext
                let fetchRequest = NSFetchRequest<MedicalRequestEntity>(entityName: "MedicalRequestEntity")
                fetchRequest.predicate = NSPredicate(format: "isSynced == false")
                
                do {
                    let unsyncedEntities = try context.fetch(fetchRequest)
                    var successCount = 0
                    var failureCount = 0
                    var conflicts: [SyncConflict] = []
                    
                    let group = DispatchGroup()
                    
                    for entity in unsyncedEntities {
                        group.enter()
                        
                        let request = MedicalRequest(
                            id: entity.id ?? UUID(),
                            patientName: entity.patientName ?? "",
                            symptoms: entity.symptoms ?? "",
                            timestamp: entity.timestamp ?? Date(),
                            transcriptionText: entity.transcriptionText,
                            audioFilePath: entity.audioFilePath,
                            isSynced: entity.isSynced,
                            lastModified: entity.lastModified ?? Date()
                        )
                        
                        // Simulate sync with server
                        DispatchQueue.global().asyncAfter(deadline: .now() + 0.5) {
                            // In real implementation, this would be an API call
                            let success = Bool.random()
                            
                            if success {
                                entity.isSynced = true
                                successCount += 1
                            } else {
                                failureCount += 1
                            }
                            
                            group.leave()
                        }
                    }
                    
                    group.wait()
                    
                    try context.save()
                    
                    let result = SyncResult(
                        successCount: successCount,
                        failureCount: failureCount,
                        conflicts: conflicts
                    )
                    
                    promise(.success(result))
                } catch {
                    promise(.failure(F8ServiceError.syncError(error.localizedDescription)))
                }
            }
        }
        .eraseToAnyPublisher()
    }
    
    func deleteOfflineRequest(id: UUID) -> AnyPublisher<Bool, Error> {
        Future<Bool, Error> { [weak self] promise in
            guard let self = self else {
                promise(.failure(F8ServiceError.persistenceError("サービスが利用できません")))
                return
            }
            
            self.syncQueue.async {
                let context = self.persistentContainer.viewContext
                let fetchRequest = NSFetchRequest<MedicalRequestEntity>(entityName: "MedicalRequestEntity")
                fetchRequest.predicate = NSPredicate(format: "id == %@", id as CVarArg)
                
                do {
                    let entities = try context.fetch(fetchRequest)
                    for entity in entities {
                        context.delete(entity)
                    }
                    try context.save()
                    promise(.success(true))
                } catch {
                    promise(.failure(F8ServiceError.persistenceError(error.localizedDescription)))
                }
            }
        }
        .eraseToAnyPublisher()
    }
    
    func clearAllOfflineData() -> AnyPublisher<Bool, Error> {
        Future<Bool, Error> { [weak self] promise in
            guard let self = self else {
                promise(.failure(F8ServiceError.persistenceError("サービスが利用できません")))
                return
            }
            
            self.syncQueue.async {
                let context = self.persistentContainer.viewContext
                let fetchRequest = NSFetchRequest<NSFetchRequestResult>(entityName: "MedicalRequestEntity")
                let deleteRequest = NSBatchDeleteRequest(fetchRequest: fetchRequest)
                
                do {
                    try context.execute(deleteRequest)
                    try context.save()
                    promise(.success(true))
                } catch {
                    promise(.failure(F8ServiceError.persistenceError(error.localizedDescription)))
                }
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - Core Data Entity
@objc(MedicalRequestEntity)
class MedicalRequestEntity: NSManagedObject {
    @NSManaged var id: UUID?
    @NSManaged var patientName: String?
    @NSManaged var symptoms: String?
    @NSManaged var timestamp: Date?
    @NSManaged var transcriptionText: String?
    @NSManaged var audioFilePath: String?
    @NSManaged var isSynced: Bool
    @NSManaged var lastModified: Date?
}

// MARK: - Network Monitor
class NetworkMonitor {
    static let shared = NetworkMonitor()
    private(set) var isConnected: Bool = true
    
    private init() {
        // In real implementation, this would use NWPathMonitor
        startMonitoring()
    }
    
    private func startMonitoring() {
        // Simplified for now
        isConnected = true
    }
}

// MARK: - Mock Service
class MockF8Service: F8ServiceProtocol {
    func saveMedicalRequest(_ request: MedicalRequest) -> AnyPublisher<Bool, Error> {
        Just(true)
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }
    
    func getOfflineRequests() -> AnyPublisher<[MedicalRequest], Error> {
        let mockRequests = [
            MedicalRequest(
                id: UUID(),
                patientName: "テスト患者",
                symptoms: "頭痛と発熱",
                timestamp: Date().addingTimeInterval(-3600),
                transcriptionText: "今朝から頭痛がひどく、熱も38度あります",
                audioFilePath: nil,
                isSynced: false,
                lastModified: Date()
            ),
            MedicalRequest(
                id: UUID(),
                patientName: "サンプル患者",
                symptoms: "咳と喉の痛み",
                timestamp: Date().addingTimeInterval(-7200),
                transcriptionText: "3日前から咳が続いています",
                audioFilePath: nil,
                isSynced: true,
                lastModified: Date()
            )
        ]
        
        return Just(mockRequests)
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }
    
    func syncOfflineData() -> AnyPublisher<SyncResult, Error> {
        let result = SyncResult(successCount: 2, failureCount: 0, conflicts: [])
        return Just(result)
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }
    
    func deleteOfflineRequest(id: UUID) -> AnyPublisher<Bool, Error> {
        Just(true)
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }
    
    func clearAllOfflineData() -> AnyPublisher<Bool, Error> {
        Just(true)
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }
}