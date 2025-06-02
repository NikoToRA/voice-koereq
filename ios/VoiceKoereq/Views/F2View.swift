import SwiftUI
import AVFoundation

struct F2View: View {
    @StateObject private var viewModel = F2ViewModel()
    @State private var showingAlert = false
    @State private var alertMessage = ""
    
    var body: some View {
        NavigationView {
            VStack(spacing: 30) {
                // 録音状態表示
                VStack(spacing: 16) {
                    Image(systemName: viewModel.isRecording ? "mic.fill" : "mic")
                        .font(.system(size: 80))
                        .foregroundColor(viewModel.isRecording ? .red : .blue)
                        .scaleEffect(viewModel.isRecording ? 1.2 : 1.0)
                        .animation(.easeInOut(duration: 0.5).repeatForever(autoreverses: true), value: viewModel.isRecording)
                    
                    Text(viewModel.statusText)
                        .font(.title2)
                        .foregroundColor(.secondary)
                    
                    if viewModel.isRecording || viewModel.isPaused {
                        Text(viewModel.recordingTimeText)
                            .font(.system(size: 48, weight: .light, design: .monospaced))
                            .foregroundColor(.primary)
                    }
                }
                .padding(.vertical, 40)
                
                // 音声波形ビジュアライザー
                if viewModel.isRecording && !viewModel.isPaused {
                    AudioVisualizerView(audioLevel: $viewModel.audioLevel)
                        .frame(height: 100)
                        .padding(.horizontal)
                }
                
                Spacer()
                
                // コントロールボタン
                VStack(spacing: 20) {
                    if !viewModel.isRecording && !viewModel.isPaused {
                        // 録音開始ボタン
                        Button(action: {
                            Task {
                                await startRecording()
                            }
                        }) {
                            Label("録音開始", systemImage: "mic.fill")
                                .font(.title2)
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 20)
                                .background(Color.red)
                                .cornerRadius(15)
                        }
                        .disabled(viewModel.isLoading)
                    } else {
                        HStack(spacing: 20) {
                            // 一時停止/再開ボタン
                            Button(action: {
                                if viewModel.isPaused {
                                    viewModel.resumeRecording()
                                } else {
                                    viewModel.pauseRecording()
                                }
                            }) {
                                Image(systemName: viewModel.isPaused ? "play.fill" : "pause.fill")
                                    .font(.title)
                                    .foregroundColor(.white)
                                    .frame(width: 70, height: 70)
                                    .background(Color.orange)
                                    .clipShape(Circle())
                            }
                            
                            // 停止ボタン
                            Button(action: {
                                Task {
                                    await stopRecording()
                                }
                            }) {
                                Image(systemName: "stop.fill")
                                    .font(.title)
                                    .foregroundColor(.white)
                                    .frame(width: 70, height: 70)
                                    .background(Color.gray)
                                    .clipShape(Circle())
                            }
                        }
                    }
                    
                    // 録音履歴へのリンク
                    if viewModel.hasRecordings {
                        NavigationLink(destination: RecordingListView()) {
                            Label("録音履歴", systemImage: "list.bullet")
                                .font(.headline)
                                .foregroundColor(.blue)
                        }
                    }
                }
                .padding(.horizontal, 40)
                .padding(.bottom, 40)
            }
            .navigationTitle("音声録音")
            .navigationBarTitleDisplayMode(.large)
            .alert("エラー", isPresented: $showingAlert) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(alertMessage)
            }
            .overlay {
                if viewModel.isLoading {
                    LoadingView()
                }
            }
        }
        .onAppear {
            Task {
                await viewModel.requestMicrophonePermission()
            }
        }
    }
    
    private func startRecording() async {
        do {
            try await viewModel.startRecording()
        } catch {
            alertMessage = error.localizedDescription
            showingAlert = true
        }
    }
    
    private func stopRecording() async {
        do {
            try await viewModel.stopRecording()
        } catch {
            alertMessage = error.localizedDescription
            showingAlert = true
        }
    }
}

// 音声波形ビジュアライザー
struct AudioVisualizerView: View {
    @Binding var audioLevel: Float
    @State private var levels: [Float] = Array(repeating: 0.0, count: 50)
    
    var body: some View {
        GeometryReader { geometry in
            HStack(alignment: .center, spacing: 2) {
                ForEach(0..<levels.count, id: \.self) { index in
                    Rectangle()
                        .fill(Color.red.opacity(0.8))
                        .frame(width: (geometry.size.width / CGFloat(levels.count)) - 2,
                               height: CGFloat(levels[index]) * geometry.size.height)
                }
            }
        }
        .onReceive(Timer.publish(every: 0.1, on: .main, in: .common).autoconnect()) { _ in
            levels.removeFirst()
            levels.append(audioLevel)
        }
    }
}

// ローディングビュー
struct LoadingView: View {
    var body: some View {
        ZStack {
            Color.black.opacity(0.4)
                .ignoresSafeArea()
            
            VStack(spacing: 20) {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    .scaleEffect(1.5)
                
                Text("処理中...")
                    .foregroundColor(.white)
                    .font(.headline)
            }
            .padding(40)
            .background(Color.black.opacity(0.8))
            .cornerRadius(20)
        }
    }
}

// 録音履歴ビュー（プレースホルダー）
struct RecordingListView: View {
    var body: some View {
        List {
            Text("録音履歴機能は今後実装予定です")
                .foregroundColor(.secondary)
        }
        .navigationTitle("録音履歴")
        .navigationBarTitleDisplayMode(.large)
    }
}

struct F2View_Previews: PreviewProvider {
    static var previews: some View {
        F2View()
    }
}