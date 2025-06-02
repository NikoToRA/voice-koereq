import SwiftUI
import Combine

struct F4View: View {
    @StateObject private var viewModel = F4ViewModel()
    @State private var inputText = ""
    @State private var isRecording = false
    @FocusState private var isTextFieldFocused: Bool
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // ヘッダー
                headerView
                
                // チャット表示エリア
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(viewModel.messages) { message in
                                MessageBubble(message: message)
                                    .id(message.id)
                            }
                            
                            if viewModel.isProcessing {
                                TypingIndicator()
                            }
                        }
                        .padding()
                    }
                    .background(Color(.systemGray6))
                    .onChange(of: viewModel.messages.count) { _ in
                        withAnimation {
                            proxy.scrollTo(viewModel.messages.last?.id, anchor: .bottom)
                        }
                    }
                }
                
                // 入力エリア
                inputView
            }
            .navigationBarHidden(true)
            .alert("エラー", isPresented: $viewModel.showError) {
                Button("OK") { }
            } message: {
                Text(viewModel.errorMessage)
            }
        }
    }
    
    private var headerView: some View {
        VStack(spacing: 8) {
            HStack {
                Image(systemName: "stethoscope")
                    .font(.title2)
                    .foregroundColor(.blue)
                
                Text("AI医療アシスタント")
                    .font(.title2)
                    .fontWeight(.bold)
                
                Spacer()
                
                Button(action: { viewModel.clearConversation() }) {
                    Image(systemName: "trash")
                        .foregroundColor(.gray)
                }
                .padding(.trailing)
            }
            .padding(.horizontal)
            .padding(.top, 50)
            
            Text("医療に関する質問をお聞かせください")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .padding(.bottom, 8)
            
            Divider()
        }
        .background(Color(.systemBackground))
    }
    
    private var inputView: some View {
        VStack(spacing: 0) {
            Divider()
            
            HStack(spacing: 12) {
                // テキスト入力フィールド
                TextField("メッセージを入力...", text: $inputText, axis: .vertical)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .lineLimit(1...4)
                    .focused($isTextFieldFocused)
                    .onSubmit {
                        sendMessage()
                    }
                
                // 音声録音ボタン
                Button(action: toggleRecording) {
                    Image(systemName: isRecording ? "mic.fill" : "mic")
                        .font(.title2)
                        .foregroundColor(isRecording ? .red : .blue)
                        .scaleEffect(isRecording ? 1.2 : 1.0)
                        .animation(.easeInOut(duration: 0.5).repeatForever(autoreverses: true), value: isRecording)
                }
                .disabled(viewModel.isProcessing)
                
                // 送信ボタン
                Button(action: sendMessage) {
                    Image(systemName: "paperplane.fill")
                        .font(.title2)
                        .foregroundColor(inputText.isEmpty ? .gray : .blue)
                }
                .disabled(inputText.isEmpty || viewModel.isProcessing)
            }
            .padding()
            .background(Color(.systemBackground))
        }
    }
    
    private func sendMessage() {
        guard !inputText.isEmpty else { return }
        let messageText = inputText
        inputText = ""
        isTextFieldFocused = false
        
        Task {
            await viewModel.sendMessage(messageText)
        }
    }
    
    private func toggleRecording() {
        if isRecording {
            viewModel.stopRecording()
        } else {
            viewModel.startRecording()
        }
        isRecording.toggle()
    }
}

struct MessageBubble: View {
    let message: Message
    
    var body: some View {
        HStack {
            if message.isUser {
                Spacer()
            }
            
            VStack(alignment: message.isUser ? .trailing : .leading, spacing: 4) {
                Text(message.content)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(message.isUser ? Color.blue : Color(.systemGray5))
                    .foregroundColor(message.isUser ? .white : .primary)
                    .cornerRadius(18)
                    .contextMenu {
                        Button(action: { UIPasteboard.general.string = message.content }) {
                            Label("コピー", systemImage: "doc.on.doc")
                        }
                    }
                
                Text(message.timestamp, style: .time)
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            .frame(maxWidth: UIScreen.main.bounds.width * 0.75, alignment: message.isUser ? .trailing : .leading)
            
            if !message.isUser {
                Spacer()
            }
        }
    }
}

struct TypingIndicator: View {
    @State private var animationAmount = 0.0
    
    var body: some View {
        HStack {
            HStack(spacing: 4) {
                ForEach(0..<3) { index in
                    Circle()
                        .fill(Color.gray)
                        .frame(width: 8, height: 8)
                        .scaleEffect(animationAmount)
                        .animation(
                            .easeInOut(duration: 0.6)
                                .repeatForever()
                                .delay(Double(index) * 0.2),
                            value: animationAmount
                        )
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Color(.systemGray5))
            .cornerRadius(18)
            .onAppear {
                animationAmount = 1.2
            }
            
            Spacer()
        }
    }
}

struct F4View_Previews: PreviewProvider {
    static var previews: some View {
        F4View()
    }
}