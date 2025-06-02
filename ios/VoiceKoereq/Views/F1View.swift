import SwiftUI
import Combine

struct F1View: View {
    @StateObject private var viewModel = F1ViewModel()
    @State private var isShowingAuthError = false
    @FocusState private var isUsernameFocused: Bool
    @FocusState private var isPasswordFocused: Bool
    
    var body: some View {
        ZStack {
            LinearGradient(
                gradient: Gradient(colors: [Color(hex: "1E88E5"), Color(hex: "1565C0")]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 30) {
                VStack(spacing: 20) {
                    Image(systemName: "waveform.circle.fill")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 120, height: 120)
                        .foregroundColor(.white)
                        .shadow(radius: 10)
                    
                    VStack(spacing: 8) {
                        Text("Voice KoeReq")
                            .font(.system(size: 36, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                        
                        Text("音声医療相談アプリ")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundColor(.white.opacity(0.9))
                    }
                }
                .padding(.top, 60)
                
                VStack(spacing: 20) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("ユーザー名")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.8))
                        
                        TextField("", text: $viewModel.username)
                            .placeholder(when: viewModel.username.isEmpty) {
                                Text("ユーザー名を入力")
                                    .foregroundColor(.white.opacity(0.5))
                            }
                            .textFieldStyle(RoundedTextFieldStyle())
                            .autocapitalization(.none)
                            .disableAutocorrection(true)
                            .focused($isUsernameFocused)
                            .accessibilityLabel("ユーザー名入力フィールド")
                    }
                    
                    VStack(alignment: .leading, spacing: 8) {
                        Text("パスワード")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.8))
                        
                        SecureField("", text: $viewModel.password)
                            .placeholder(when: viewModel.password.isEmpty) {
                                Text("パスワードを入力")
                                    .foregroundColor(.white.opacity(0.5))
                            }
                            .textFieldStyle(RoundedTextFieldStyle())
                            .focused($isPasswordFocused)
                            .accessibilityLabel("パスワード入力フィールド")
                    }
                    
                    Toggle(isOn: $viewModel.rememberMe) {
                        Text("ログイン情報を保存")
                            .font(.system(size: 14))
                            .foregroundColor(.white)
                    }
                    .toggleStyle(CheckboxToggleStyle())
                    .padding(.horizontal, 4)
                }
                .padding(.horizontal, 40)
                
                VStack(spacing: 16) {
                    Button(action: {
                        hideKeyboard()
                        viewModel.login()
                    }) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Color.white)
                                .shadow(radius: 5)
                            
                            if viewModel.isLoading {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .blue))
                            } else {
                                Text("ログイン")
                                    .font(.system(size: 18, weight: .semibold))
                                    .foregroundColor(Color(hex: "1E88E5"))
                            }
                        }
                        .frame(height: 50)
                        .frame(maxWidth: .infinity)
                        .disabled(viewModel.isLoading || !viewModel.isFormValid)
                        .opacity(viewModel.isFormValid ? 1.0 : 0.7)
                    }
                    .accessibilityLabel("ログインボタン")
                    .accessibilityHint("タップしてログインします")
                    
                    Button(action: {
                        viewModel.loginWithBiometrics()
                    }) {
                        HStack {
                            Image(systemName: viewModel.biometricType == .faceID ? "faceid" : "touchid")
                                .font(.system(size: 20))
                            Text(viewModel.biometricType == .faceID ? "Face IDでログイン" : "Touch IDでログイン")
                                .font(.system(size: 16, weight: .medium))
                        }
                        .foregroundColor(.white)
                    }
                    .opacity(viewModel.isBiometricAvailable ? 1.0 : 0.5)
                    .disabled(!viewModel.isBiometricAvailable)
                    .accessibilityLabel("生体認証でログイン")
                }
                .padding(.horizontal, 40)
                
                Spacer()
                
                VStack(spacing: 8) {
                    Text("初めてご利用の方")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.8))
                    
                    Button(action: {
                        viewModel.navigateToRegistration()
                    }) {
                        Text("アカウント作成")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(.white)
                            .underline()
                    }
                    .accessibilityLabel("アカウント作成ボタン")
                }
                .padding(.bottom, 40)
            }
        }
        .alert("認証エラー", isPresented: $viewModel.showError) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(viewModel.errorMessage)
        }
        .onTapGesture {
            hideKeyboard()
        }
    }
    
    private func hideKeyboard() {
        isUsernameFocused = false
        isPasswordFocused = false
    }
}

struct RoundedTextFieldStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration
            .padding()
            .background(Color.white.opacity(0.2))
            .foregroundColor(.white)
            .cornerRadius(10)
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(Color.white.opacity(0.3), lineWidth: 1)
            )
    }
}

struct CheckboxToggleStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack {
            Image(systemName: configuration.isOn ? "checkmark.square.fill" : "square")
                .foregroundColor(.white)
                .font(.system(size: 20))
                .onTapGesture {
                    configuration.isOn.toggle()
                }
            
            configuration.label
        }
    }
}

extension View {
    func placeholder<Content: View>(
        when shouldShow: Bool,
        alignment: Alignment = .leading,
        @ViewBuilder placeholder: () -> Content) -> some View {
        
        ZStack(alignment: alignment) {
            placeholder().opacity(shouldShow ? 1 : 0)
            self
        }
    }
}

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3:
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6:
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue:  Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

#if DEBUG
struct F1View_Previews: PreviewProvider {
    static var previews: some View {
        F1View()
    }
}
#endif