import Foundation
import Combine

protocol F7ServiceProtocol {
    func generateSummary(from transcriptionId: String) -> AnyPublisher<F7Summary, F7ServiceError>
    func getSummaries() -> AnyPublisher<[F7Summary], F7ServiceError>
    func getSummary(id: String) -> AnyPublisher<F7Summary, F7ServiceError>
    func deleteSummary(id: String) -> AnyPublisher<Void, F7ServiceError>
}

struct F7Summary: Codable, Identifiable {
    let id: String
    let transcriptionId: String
    let title: String
    let summary: String
    let keyPoints: [String]
    let generatedAt: Date
    let language: String
}

enum F7ServiceError: LocalizedError {
    case invalidTranscriptionId
    case summaryGenerationFailed
    case summaryNotFound
    case networkError(Error)
    case decodingError
    case deletionFailed
    
    var errorDescription: String? {
        switch self {
        case .invalidTranscriptionId:
            return "無効な文字起こしIDです"
        case .summaryGenerationFailed:
            return "サマリー生成に失敗しました"
        case .summaryNotFound:
            return "サマリーが見つかりません"
        case .networkError(let error):
            return "ネットワークエラー: \(error.localizedDescription)"
        case .decodingError:
            return "データの解析に失敗しました"
        case .deletionFailed:
            return "サマリーの削除に失敗しました"
        }
    }
}

class F7Service: F7ServiceProtocol {
    private let baseURL = "https://voicekoereq.azurewebsites.net/api/summaries"
    private let session: URLSession
    
    init(session: URLSession = .shared) {
        self.session = session
    }
    
    func generateSummary(from transcriptionId: String) -> AnyPublisher<F7Summary, F7ServiceError> {
        guard !transcriptionId.isEmpty else {
            return Fail(error: F7ServiceError.invalidTranscriptionId)
                .eraseToAnyPublisher()
        }
        
        let url = URL(string: "\(baseURL)/generate")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body = ["transcriptionId": transcriptionId]
        request.httpBody = try? JSONEncoder().encode(body)
        
        return session.dataTaskPublisher(for: request)
            .map(\.data)
            .decode(type: F7Summary.self, decoder: JSONDecoder())
            .mapError { error in
                if error is DecodingError {
                    return F7ServiceError.decodingError
                } else {
                    return F7ServiceError.networkError(error)
                }
            }
            .eraseToAnyPublisher()
    }
    
    func getSummaries() -> AnyPublisher<[F7Summary], F7ServiceError> {
        let url = URL(string: baseURL)!
        
        return session.dataTaskPublisher(for: url)
            .map(\.data)
            .decode(type: [F7Summary].self, decoder: JSONDecoder())
            .mapError { error in
                if error is DecodingError {
                    return F7ServiceError.decodingError
                } else {
                    return F7ServiceError.networkError(error)
                }
            }
            .eraseToAnyPublisher()
    }
    
    func getSummary(id: String) -> AnyPublisher<F7Summary, F7ServiceError> {
        let url = URL(string: "\(baseURL)/\(id)")!
        
        return session.dataTaskPublisher(for: url)
            .map(\.data)
            .decode(type: F7Summary.self, decoder: JSONDecoder())
            .mapError { error in
                if error is DecodingError {
                    return F7ServiceError.decodingError
                } else if (error as NSError).code == 404 {
                    return F7ServiceError.summaryNotFound
                } else {
                    return F7ServiceError.networkError(error)
                }
            }
            .eraseToAnyPublisher()
    }
    
    func deleteSummary(id: String) -> AnyPublisher<Void, F7ServiceError> {
        let url = URL(string: "\(baseURL)/\(id)")!
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        
        return session.dataTaskPublisher(for: request)
            .tryMap { data, response in
                guard let httpResponse = response as? HTTPURLResponse,
                      (200...299).contains(httpResponse.statusCode) else {
                    throw F7ServiceError.deletionFailed
                }
                return ()
            }
            .mapError { error in
                if let serviceError = error as? F7ServiceError {
                    return serviceError
                } else {
                    return F7ServiceError.networkError(error)
                }
            }
            .eraseToAnyPublisher()
    }
}

#if DEBUG
class MockF7Service: F7ServiceProtocol {
    var shouldFail = false
    var summaries: [F7Summary] = []
    
    init() {
        // Initialize with mock data
        summaries = [
            F7Summary(
                id: "1",
                transcriptionId: "trans-1",
                title: "初診患者の症状相談",
                summary: "患者は頭痛と軽度の発熱を訴えています。症状は3日前から始まり、市販薬で一時的に改善するが再発する状態です。",
                keyPoints: ["頭痛（3日間継続）", "軽度の発熱（37.5度）", "市販薬で一時的改善"],
                generatedAt: Date(),
                language: "ja"
            ),
            F7Summary(
                id: "2",
                transcriptionId: "trans-2",
                title: "経過観察の相談",
                summary: "前回処方された薬の効果について報告。症状は改善傾向にあるが、完全には回復していない。",
                keyPoints: ["薬の効果あり", "症状改善傾向", "完全回復まで継続観察必要"],
                generatedAt: Date().addingTimeInterval(-86400),
                language: "ja"
            )
        ]
    }
    
    func generateSummary(from transcriptionId: String) -> AnyPublisher<F7Summary, F7ServiceError> {
        if shouldFail {
            return Fail(error: F7ServiceError.summaryGenerationFailed)
                .eraseToAnyPublisher()
        }
        
        let newSummary = F7Summary(
            id: UUID().uuidString,
            transcriptionId: transcriptionId,
            title: "新しい相談サマリー",
            summary: "これはテスト用の新しいサマリーです。実際のAIサービスでは、文字起こしデータから自動的にサマリーが生成されます。",
            keyPoints: ["テストポイント1", "テストポイント2", "テストポイント3"],
            generatedAt: Date(),
            language: "ja"
        )
        
        summaries.append(newSummary)
        
        return Just(newSummary)
            .setFailureType(to: F7ServiceError.self)
            .delay(for: .milliseconds(500), scheduler: RunLoop.main)
            .eraseToAnyPublisher()
    }
    
    func getSummaries() -> AnyPublisher<[F7Summary], F7ServiceError> {
        if shouldFail {
            return Fail(error: F7ServiceError.networkError(NSError(domain: "test", code: -1)))
                .eraseToAnyPublisher()
        }
        
        return Just(summaries)
            .setFailureType(to: F7ServiceError.self)
            .delay(for: .milliseconds(300), scheduler: RunLoop.main)
            .eraseToAnyPublisher()
    }
    
    func getSummary(id: String) -> AnyPublisher<F7Summary, F7ServiceError> {
        if shouldFail {
            return Fail(error: F7ServiceError.summaryNotFound)
                .eraseToAnyPublisher()
        }
        
        guard let summary = summaries.first(where: { $0.id == id }) else {
            return Fail(error: F7ServiceError.summaryNotFound)
                .eraseToAnyPublisher()
        }
        
        return Just(summary)
            .setFailureType(to: F7ServiceError.self)
            .delay(for: .milliseconds(300), scheduler: RunLoop.main)
            .eraseToAnyPublisher()
    }
    
    func deleteSummary(id: String) -> AnyPublisher<Void, F7ServiceError> {
        if shouldFail {
            return Fail(error: F7ServiceError.deletionFailed)
                .eraseToAnyPublisher()
        }
        
        summaries.removeAll { $0.id == id }
        
        return Just(())
            .setFailureType(to: F7ServiceError.self)
            .delay(for: .milliseconds(300), scheduler: RunLoop.main)
            .eraseToAnyPublisher()
    }
}
#endif