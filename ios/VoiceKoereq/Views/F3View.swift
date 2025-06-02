import SwiftUI
import Combine
import AVFoundation

struct F3View: View {
    @StateObject private var viewModel = F3ViewModel()
    @State private var isTranscribing = false
    @State private var showError = false
    @State private var errorMessage = ""
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                headerSection
                
                transcriptionSection
                
                controlSection
                
                statusSection
                
                Spacer()
            }
            .padding()
            .navigationTitle("文字起こし")
            .navigationBarTitleDisplayMode(.large)
            .alert("エラー", isPresented: $showError) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(errorMessage)
            }
        }
        .onReceive(viewModel.$error) { error in
            if let error = error {
                errorMessage = error.localizedDescription
                showError = true
            }
        }
    }
    
    private var headerSection: some View {
        VStack(spacing: 8) {
            Image(systemName: "mic.circle.fill")
                .font(.system(size: 60))
                .foregroundColor(.blue)
                .symbolEffect(.bounce, options: .repeating, value: isTranscribing)
            
            Text("音声をテキストに変換します")
                .font(.headline)
                .foregroundColor(.secondary)
        }
        .padding(.top, 20)
    }
    
    private var transcriptionSection: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !viewModel.transcribedText.isEmpty {
                    Text("変換結果")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    
                    Text(viewModel.transcribedText)
                        .font(.body)
                        .padding()
                        .background(Color(.systemGray6))
                        .cornerRadius(12)
                        .textSelection(.enabled)
                } else if viewModel.isTranscribing {
                    HStack {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle())
                            .scaleEffect(0.8)
                        Text("変換中...")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding()
                    .frame(maxWidth: .infinity)
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                } else {
                    Text("録音した音声がここに表示されます")
                        .font(.body)
                        .foregroundColor(.secondary)
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color(.systemGray6))
                        .cornerRadius(12)
                }
            }
        }
        .frame(maxHeight: 300)
    }
    
    private var controlSection: some View {
        VStack(spacing: 16) {
            HStack(spacing: 20) {
                Button(action: {
                    if viewModel.hasAudioData {
                        Task {
                            isTranscribing = true
                            await viewModel.startTranscription()
                            isTranscribing = false
                        }
                    }
                }) {
                    Label("文字起こし開始", systemImage: "text.bubble")
                        .font(.headline)
                        .foregroundColor(.white)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .background(viewModel.hasAudioData ? Color.blue : Color.gray)
                        .cornerRadius(25)
                }
                .disabled(!viewModel.hasAudioData || viewModel.isTranscribing)
                
                Button(action: {
                    viewModel.clearTranscription()
                }) {
                    Image(systemName: "trash")
                        .font(.title2)
                        .foregroundColor(.red)
                        .padding(12)
                        .background(Color(.systemGray5))
                        .clipShape(Circle())
                }
                .disabled(viewModel.transcribedText.isEmpty)
            }
            
            if viewModel.isTranscribing {
                ProgressView(value: viewModel.transcriptionProgress)
                    .progressViewStyle(LinearProgressViewStyle())
                    .scaleEffect(x: 1, y: 2, anchor: .center)
            }
        }
    }
    
    private var statusSection: some View {
        VStack(spacing: 8) {
            if let duration = viewModel.audioDuration {
                HStack {
                    Image(systemName: "waveform")
                        .foregroundColor(.secondary)
                    Text("音声長: \(formatDuration(duration))")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            
            if viewModel.isTranscribing {
                Text("Azure Speech Servicesで処理中...")
                    .font(.caption)
                    .foregroundColor(.blue)
            }
            
            if !viewModel.transcribedText.isEmpty {
                HStack {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.green)
                    Text("変換完了")
                        .font(.caption)
                        .foregroundColor(.green)
                }
            }
        }
        .padding(.vertical, 8)
    }
    
    private func formatDuration(_ duration: TimeInterval) -> String {
        let minutes = Int(duration) / 60
        let seconds = Int(duration) % 60
        return String(format: "%d:%02d", minutes, seconds)
    }
}

struct F3View_Previews: PreviewProvider {
    static var previews: some View {
        F3View()
            .preferredColorScheme(.light)
        
        F3View()
            .preferredColorScheme(.dark)
    }
}