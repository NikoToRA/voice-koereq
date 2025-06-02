import Foundation
import Combine
import LocalAuthentication

class F1ViewModel: ObservableObject {
    @Published var username: String = ""
    @Published var password: String = ""
    @Published var rememberMe: Bool = false
    @Published var isLoading: Bool = false
    @Published var showError: Bool = false
    @Published var errorMessage: String = ""
    @Published var isAuthenticated: Bool = false
    @Published var isBiometricAvailable: Bool = false
    @Published var biometricType: LABiometryType = .none
    
    private let authService: F1Service
    private var cancellables = Set<AnyCancellable>()
    
    var isFormValid: Bool {
        !username.isEmpty && !password.isEmpty
    }
    
    init(authService: F1Service = F1Service()) {
        self.authService = authService
        checkBiometricAvailability()
        loadSavedCredentials()
    }
    
    func login() {
        guard isFormValid else { return }
        
        isLoading = true
        errorMessage = ""
        
        authService.authenticate(username: username, password: password)
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { [weak self] completion in
                    self?.isLoading = false
                    if case .failure(let error) = completion {
                        self?.handleError(error)
                    }
                },
                receiveValue: { [weak self] response in
                    self?.handleLoginSuccess(response)
                }
            )
            .store(in: &cancellables)
    }
    
    func loginWithBiometrics() {
        guard isBiometricAvailable else { return }
        
        isLoading = true
        errorMessage = ""
        
        authService.authenticateWithBiometrics()
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { [weak self] completion in
                    self?.isLoading = false
                    if case .failure(let error) = completion {
                        self?.handleError(error)
                    }
                },
                receiveValue: { [weak self] response in
                    self?.handleLoginSuccess(response)
                }
            )
            .store(in: &cancellables)
    }
    
    func navigateToRegistration() {
        // Navigation logic will be handled by the parent view or coordinator
        print("Navigate to registration")
    }
    
    private func handleLoginSuccess(_ response: AuthResponse) {
        isAuthenticated = true
        
        if rememberMe {
            saveCredentials()
        }
        
        // Store auth token
        UserDefaults.standard.set(response.token, forKey: "authToken")
        UserDefaults.standard.set(response.userId, forKey: "userId")
        
        // Navigation to main screen will be handled by parent view
    }
    
    private func handleError(_ error: AuthError) {
        switch error {
        case .invalidCredentials:
            errorMessage = "ユーザー名またはパスワードが正しくありません"
        case .networkError:
            errorMessage = "ネットワークエラーが発生しました。接続を確認してください"
        case .serverError:
            errorMessage = "サーバーエラーが発生しました。しばらくしてから再試行してください"
        case .biometricFailed:
            errorMessage = "生体認証に失敗しました"
        case .biometricNotAvailable:
            errorMessage = "生体認証が利用できません"
        case .unknown:
            errorMessage = "予期しないエラーが発生しました"
        }
        showError = true
    }
    
    private func checkBiometricAvailability() {
        let context = LAContext()
        var error: NSError?
        
        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            isBiometricAvailable = true
            biometricType = context.biometryType
        } else {
            isBiometricAvailable = false
            biometricType = .none
        }
    }
    
    private func saveCredentials() {
        let keychain = KeychainService()
        _ = keychain.save(username, forKey: "username")
        _ = keychain.save(password, forKey: "password")
        UserDefaults.standard.set(true, forKey: "rememberMe")
    }
    
    private func loadSavedCredentials() {
        let keychain = KeychainService()
        if UserDefaults.standard.bool(forKey: "rememberMe"),
           let savedUsername = keychain.load(forKey: "username"),
           let savedPassword = keychain.load(forKey: "password") {
            username = savedUsername
            password = savedPassword
            rememberMe = true
        }
    }
}

// MARK: - Supporting Types

struct AuthResponse {
    let token: String
    let userId: String
    let userName: String
    let expiresIn: TimeInterval
}

enum AuthError: Error {
    case invalidCredentials
    case networkError
    case serverError
    case biometricFailed
    case biometricNotAvailable
    case unknown
}

// MARK: - Keychain Service

class KeychainService {
    func save(_ value: String, forKey key: String) -> Bool {
        if let data = value.data(using: .utf8) {
            let query: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrAccount as String: key,
                kSecValueData as String: data
            ]
            
            SecItemDelete(query as CFDictionary)
            
            let status = SecItemAdd(query as CFDictionary, nil)
            return status == errSecSuccess
        }
        return false
    }
    
    func load(forKey key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        
        var dataTypeRef: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &dataTypeRef)
        
        if status == errSecSuccess,
           let data = dataTypeRef as? Data,
           let value = String(data: data, encoding: .utf8) {
            return value
        }
        
        return nil
    }
    
    func delete(forKey key: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key
        ]
        
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess
    }
}