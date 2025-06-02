import Foundation
import Combine
import LocalAuthentication

class F1Service {
    private let baseURL = "https://api.voicekoereq.com/v1"
    private let session: URLSession
    
    init(session: URLSession = .shared) {
        self.session = session
    }
    
    func authenticate(username: String, password: String) -> AnyPublisher<AuthResponse, AuthError> {
        guard let url = URL(string: "\(baseURL)/auth/login") else {
            return Fail(error: AuthError.unknown)
                .eraseToAnyPublisher()
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body = [
            "username": username,
            "password": password
        ]
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        } catch {
            return Fail(error: AuthError.unknown)
                .eraseToAnyPublisher()
        }
        
        return session.dataTaskPublisher(for: request)
            .tryMap { data, response in
                guard let httpResponse = response as? HTTPURLResponse else {
                    throw AuthError.unknown
                }
                
                switch httpResponse.statusCode {
                case 200:
                    return data
                case 401:
                    throw AuthError.invalidCredentials
                case 500...599:
                    throw AuthError.serverError
                default:
                    throw AuthError.unknown
                }
            }
            .decode(type: AuthResponseDTO.self, decoder: JSONDecoder())
            .map { dto in
                AuthResponse(
                    token: dto.token,
                    userId: dto.userId,
                    userName: dto.userName,
                    expiresIn: dto.expiresIn
                )
            }
            .mapError { error in
                if let authError = error as? AuthError {
                    return authError
                } else if error is URLError {
                    return .networkError
                } else {
                    return .unknown
                }
            }
            .eraseToAnyPublisher()
    }
    
    func authenticateWithBiometrics() -> AnyPublisher<AuthResponse, AuthError> {
        let context = LAContext()
        context.localizedReason = "Voice KoeReqにログインします"
        
        return Future<AuthResponse, AuthError> { promise in
            context.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: "生体認証でログイン"
            ) { [weak self] success, error in
                if success {
                    // Retrieve stored credentials and authenticate
                    if let credentials = self?.retrieveStoredCredentials() {
                        self?.authenticate(
                            username: credentials.username,
                            password: credentials.password
                        )
                        .sink(
                            receiveCompletion: { completion in
                                if case .failure(let error) = completion {
                                    promise(.failure(error))
                                }
                            },
                            receiveValue: { response in
                                promise(.success(response))
                            }
                        )
                        .store(in: &self!.cancellables)
                    } else {
                        promise(.failure(.biometricFailed))
                    }
                } else {
                    if let laError = error as? LAError {
                        switch laError.code {
                        case .biometryNotAvailable, .biometryNotEnrolled:
                            promise(.failure(.biometricNotAvailable))
                        default:
                            promise(.failure(.biometricFailed))
                        }
                    } else {
                        promise(.failure(.biometricFailed))
                    }
                }
            }
        }
        .eraseToAnyPublisher()
    }
    
    func refreshToken(token: String) -> AnyPublisher<AuthResponse, AuthError> {
        guard let url = URL(string: "\(baseURL)/auth/refresh") else {
            return Fail(error: AuthError.unknown)
                .eraseToAnyPublisher()
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        
        return session.dataTaskPublisher(for: request)
            .tryMap { data, response in
                guard let httpResponse = response as? HTTPURLResponse else {
                    throw AuthError.unknown
                }
                
                switch httpResponse.statusCode {
                case 200:
                    return data
                case 401:
                    throw AuthError.invalidCredentials
                case 500...599:
                    throw AuthError.serverError
                default:
                    throw AuthError.unknown
                }
            }
            .decode(type: AuthResponseDTO.self, decoder: JSONDecoder())
            .map { dto in
                AuthResponse(
                    token: dto.token,
                    userId: dto.userId,
                    userName: dto.userName,
                    expiresIn: dto.expiresIn
                )
            }
            .mapError { error in
                if let authError = error as? AuthError {
                    return authError
                } else if error is URLError {
                    return .networkError
                } else {
                    return .unknown
                }
            }
            .eraseToAnyPublisher()
    }
    
    private var cancellables = Set<AnyCancellable>()
    
    private func retrieveStoredCredentials() -> (username: String, password: String)? {
        let keychain = KeychainService()
        guard let username = keychain.load(forKey: "username"),
              let password = keychain.load(forKey: "password") else {
            return nil
        }
        return (username, password)
    }
}

// MARK: - DTOs

private struct AuthResponseDTO: Codable {
    let token: String
    let userId: String
    let userName: String
    let expiresIn: TimeInterval
}

// MARK: - Mock Service for Development

#if DEBUG
class MockF1Service: F1Service {
    var shouldSucceed = true
    var delay: TimeInterval = 1.0
    
    override func authenticate(username: String, password: String) -> AnyPublisher<AuthResponse, AuthError> {
        return Future<AuthResponse, AuthError> { promise in
            DispatchQueue.global().asyncAfter(deadline: .now() + self.delay) {
                if self.shouldSucceed && username == "demo" && password == "demo123" {
                    let response = AuthResponse(
                        token: "mock-jwt-token-12345",
                        userId: "user-12345",
                        userName: "デモユーザー",
                        expiresIn: 3600
                    )
                    promise(.success(response))
                } else {
                    promise(.failure(.invalidCredentials))
                }
            }
        }
        .eraseToAnyPublisher()
    }
    
    override func authenticateWithBiometrics() -> AnyPublisher<AuthResponse, AuthError> {
        return authenticate(username: "demo", password: "demo123")
    }
}
#endif