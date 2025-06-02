import XCTest
import Combine
@testable import VoiceKoereq

class F1Tests: XCTestCase {
    
    var viewModel: F1ViewModel!
    var mockService: MockF1Service!
    var cancellables: Set<AnyCancellable>!
    
    override func setUp() {
        super.setUp()
        mockService = MockF1Service()
        viewModel = F1ViewModel(authService: mockService)
        cancellables = Set<AnyCancellable>()
    }
    
    override func tearDown() {
        viewModel = nil
        mockService = nil
        cancellables = nil
        super.tearDown()
    }
    
    // MARK: - Form Validation Tests
    
    func testFormValidation_EmptyFields_IsInvalid() {
        viewModel.username = ""
        viewModel.password = ""
        
        XCTAssertFalse(viewModel.isFormValid)
    }
    
    func testFormValidation_OnlyUsername_IsInvalid() {
        viewModel.username = "testuser"
        viewModel.password = ""
        
        XCTAssertFalse(viewModel.isFormValid)
    }
    
    func testFormValidation_OnlyPassword_IsInvalid() {
        viewModel.username = ""
        viewModel.password = "password123"
        
        XCTAssertFalse(viewModel.isFormValid)
    }
    
    func testFormValidation_BothFields_IsValid() {
        viewModel.username = "testuser"
        viewModel.password = "password123"
        
        XCTAssertTrue(viewModel.isFormValid)
    }
    
    // MARK: - Login Tests
    
    func testLogin_Success() {
        let expectation = XCTestExpectation(description: "Login succeeds")
        
        mockService.shouldSucceed = true
        mockService.delay = 0.1
        
        viewModel.username = "demo"
        viewModel.password = "demo123"
        
        viewModel.$isAuthenticated
            .dropFirst()
            .sink { isAuthenticated in
                if isAuthenticated {
                    expectation.fulfill()
                }
            }
            .store(in: &cancellables)
        
        viewModel.login()
        
        wait(for: [expectation], timeout: 1.0)
        
        XCTAssertTrue(viewModel.isAuthenticated)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertFalse(viewModel.showError)
    }
    
    func testLogin_InvalidCredentials() {
        let expectation = XCTestExpectation(description: "Login fails with invalid credentials")
        
        mockService.shouldSucceed = false
        mockService.delay = 0.1
        
        viewModel.username = "wrong"
        viewModel.password = "wrong"
        
        viewModel.$showError
            .dropFirst()
            .sink { showError in
                if showError {
                    expectation.fulfill()
                }
            }
            .store(in: &cancellables)
        
        viewModel.login()
        
        wait(for: [expectation], timeout: 1.0)
        
        XCTAssertFalse(viewModel.isAuthenticated)
        XCTAssertTrue(viewModel.showError)
        XCTAssertEqual(viewModel.errorMessage, "ユーザー名またはパスワードが正しくありません")
    }
    
    func testLogin_LoadingState() {
        mockService.delay = 0.5
        
        viewModel.username = "demo"
        viewModel.password = "demo123"
        
        viewModel.login()
        
        XCTAssertTrue(viewModel.isLoading)
    }
    
    // MARK: - Remember Me Tests
    
    func testRememberMe_SavesCredentials() {
        let keychain = KeychainService()
        
        // Clean up any existing credentials
        _ = keychain.delete(forKey: "username")
        _ = keychain.delete(forKey: "password")
        
        viewModel.username = "testuser"
        viewModel.password = "testpass"
        viewModel.rememberMe = true
        
        mockService.shouldSucceed = true
        mockService.delay = 0.1
        
        let expectation = XCTestExpectation(description: "Credentials are saved")
        
        viewModel.$isAuthenticated
            .dropFirst()
            .sink { isAuthenticated in
                if isAuthenticated {
                    expectation.fulfill()
                }
            }
            .store(in: &cancellables)
        
        viewModel.login()
        
        wait(for: [expectation], timeout: 1.0)
        
        // Verify credentials were saved
        XCTAssertNotNil(keychain.load(forKey: "username"))
        XCTAssertNotNil(keychain.load(forKey: "password"))
        XCTAssertTrue(UserDefaults.standard.bool(forKey: "rememberMe"))
        
        // Clean up
        _ = keychain.delete(forKey: "username")
        _ = keychain.delete(forKey: "password")
        UserDefaults.standard.removeObject(forKey: "rememberMe")
    }
    
    // MARK: - Biometric Authentication Tests
    
    func testBiometricLogin_Success() {
        let expectation = XCTestExpectation(description: "Biometric login succeeds")
        
        mockService.shouldSucceed = true
        mockService.delay = 0.1
        
        // Save test credentials for biometric auth
        let keychain = KeychainService()
        _ = keychain.save("demo", forKey: "username")
        _ = keychain.save("demo123", forKey: "password")
        
        viewModel.$isAuthenticated
            .dropFirst()
            .sink { isAuthenticated in
                if isAuthenticated {
                    expectation.fulfill()
                }
            }
            .store(in: &cancellables)
        
        viewModel.loginWithBiometrics()
        
        wait(for: [expectation], timeout: 1.0)
        
        XCTAssertTrue(viewModel.isAuthenticated)
        
        // Clean up
        _ = keychain.delete(forKey: "username")
        _ = keychain.delete(forKey: "password")
    }
    
    // MARK: - Token Storage Tests
    
    func testTokenStorage_AfterSuccessfulLogin() {
        let expectation = XCTestExpectation(description: "Token is stored after login")
        
        mockService.shouldSucceed = true
        mockService.delay = 0.1
        
        viewModel.username = "demo"
        viewModel.password = "demo123"
        
        viewModel.$isAuthenticated
            .dropFirst()
            .sink { isAuthenticated in
                if isAuthenticated {
                    expectation.fulfill()
                }
            }
            .store(in: &cancellables)
        
        viewModel.login()
        
        wait(for: [expectation], timeout: 1.0)
        
        XCTAssertNotNil(UserDefaults.standard.string(forKey: "authToken"))
        XCTAssertNotNil(UserDefaults.standard.string(forKey: "userId"))
        
        // Clean up
        UserDefaults.standard.removeObject(forKey: "authToken")
        UserDefaults.standard.removeObject(forKey: "userId")
    }
}

// MARK: - F1Service Tests

class F1ServiceTests: XCTestCase {
    
    var service: F1Service!
    var mockSession: URLSession!
    var cancellables: Set<AnyCancellable>!
    
    override func setUp() {
        super.setUp()
        
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        mockSession = URLSession(configuration: configuration)
        
        service = F1Service(session: mockSession)
        cancellables = Set<AnyCancellable>()
    }
    
    override func tearDown() {
        service = nil
        mockSession = nil
        cancellables = nil
        MockURLProtocol.requestHandler = nil
        super.tearDown()
    }
    
    func testAuthenticate_SuccessfulResponse() {
        let expectation = XCTestExpectation(description: "Authentication succeeds")
        
        MockURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: nil
            )!
            
            let responseData = """
            {
                "token": "test-token",
                "userId": "test-user-id",
                "userName": "テストユーザー",
                "expiresIn": 3600
            }
            """.data(using: .utf8)!
            
            return (response, responseData)
        }
        
        service.authenticate(username: "test", password: "test123")
            .sink(
                receiveCompletion: { completion in
                    if case .finished = completion {
                        expectation.fulfill()
                    }
                },
                receiveValue: { response in
                    XCTAssertEqual(response.token, "test-token")
                    XCTAssertEqual(response.userId, "test-user-id")
                    XCTAssertEqual(response.userName, "テストユーザー")
                    XCTAssertEqual(response.expiresIn, 3600)
                }
            )
            .store(in: &cancellables)
        
        wait(for: [expectation], timeout: 1.0)
    }
    
    func testAuthenticate_InvalidCredentials() {
        let expectation = XCTestExpectation(description: "Authentication fails with 401")
        
        MockURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 401,
                httpVersion: nil,
                headerFields: nil
            )!
            
            return (response, Data())
        }
        
        service.authenticate(username: "wrong", password: "wrong")
            .sink(
                receiveCompletion: { completion in
                    if case .failure(let error) = completion {
                        XCTAssertEqual(error, .invalidCredentials)
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
    
    func testAuthenticate_ServerError() {
        let expectation = XCTestExpectation(description: "Authentication fails with server error")
        
        MockURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 500,
                httpVersion: nil,
                headerFields: nil
            )!
            
            return (response, Data())
        }
        
        service.authenticate(username: "test", password: "test123")
            .sink(
                receiveCompletion: { completion in
                    if case .failure(let error) = completion {
                        XCTAssertEqual(error, .serverError)
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

// MARK: - Mock URL Protocol

class MockURLProtocol: URLProtocol {
    static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?
    
    override class func canInit(with request: URLRequest) -> Bool {
        return true
    }
    
    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        return request
    }
    
    override func startLoading() {
        guard let handler = MockURLProtocol.requestHandler else {
            XCTFail("Handler is not set.")
            return
        }
        
        do {
            let (response, data) = try handler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }
    
    override func stopLoading() {}
}