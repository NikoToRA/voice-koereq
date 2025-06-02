import SwiftUI

struct F8View: View {
    @StateObject private var viewModel = F8ViewModel()
    @State private var showAddRequest = false
    @State private var patientName = ""
    @State private var symptoms = ""
    @State private var transcriptionText = ""
    
    var body: some View {
        NavigationView {
            ZStack {
                LinearGradient(
                    gradient: Gradient(colors: [Color(hex: "1E88E5"), Color(hex: "1565C0")]),
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                VStack(spacing: 20) {
                    // Status Bar
                    statusBar
                    
                    // Main Content
                    if viewModel.isLoading {
                        ProgressView("読み込み中...")
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .foregroundColor(.white)
                            .padding()
                    } else if viewModel.offlineRequests.isEmpty {
                        emptyState
                    } else {
                        requestsList
                    }
                    
                    // Action Buttons
                    actionButtons
                }
                .padding()
            }
            .navigationTitle("オフラインデータ")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showAddRequest = true }) {
                        Image(systemName: "plus.circle.fill")
                            .foregroundColor(.white)
                    }
                }
            }
        }
        .sheet(isPresented: $showAddRequest) {
            addRequestSheet
        }
        .alert("エラー", isPresented: $viewModel.showError) {
            Button("OK") {
                viewModel.showError = false
            }
        } message: {
            Text(viewModel.errorMessage ?? "不明なエラーが発生しました")
        }
        .alert("削除の確認", isPresented: $viewModel.showDeleteConfirmation) {
            Button("キャンセル", role: .cancel) {
                viewModel.showDeleteConfirmation = false
            }
            Button("削除", role: .destructive) {
                viewModel.confirmDelete()
            }
        } message: {
            Text("このデータを削除してもよろしいですか？")
        }
        .alert("同期結果", isPresented: $viewModel.showSyncResult) {
            Button("OK") {
                viewModel.showSyncResult = false
            }
        } message: {
            if let result = viewModel.syncResult {
                Text("成功: \(result.successCount)件\n失敗: \(result.failureCount)件")
            }
        }
    }
    
    private var statusBar: some View {
        HStack {
            // Connection Status
            HStack(spacing: 8) {
                Circle()
                    .fill(viewModel.isOnline ? Color.green : Color.red)
                    .frame(width: 10, height: 10)
                Text(viewModel.isOnline ? "オンライン" : "オフライン")
                    .font(.caption)
                    .foregroundColor(.white)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Color.black.opacity(0.3))
            .cornerRadius(15)
            
            Spacer()
            
            // Sync Status
            if case .syncing = viewModel.syncStatus {
                HStack(spacing: 8) {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        .scaleEffect(0.8)
                    Text("同期中...")
                        .font(.caption)
                        .foregroundColor(.white)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.black.opacity(0.3))
                .cornerRadius(15)
            }
            
            // Data Count
            VStack(alignment: .trailing, spacing: 2) {
                Text("\(viewModel.totalCount)件")
                    .font(.caption2)
                    .foregroundColor(.white)
                if viewModel.unsyncedCount > 0 {
                    Text("未同期: \(viewModel.unsyncedCount)")
                        .font(.caption2)
                        .foregroundColor(.yellow)
                }
            }
        }
    }
    
    private var emptyState: some View {
        VStack(spacing: 20) {
            Image(systemName: "icloud.slash")
                .font(.system(size: 60))
                .foregroundColor(.white.opacity(0.6))
            
            Text("オフラインデータがありません")
                .font(.headline)
                .foregroundColor(.white)
            
            Text("インターネット接続がない場合、データは自動的に保存されます")
                .font(.caption)
                .foregroundColor(.white.opacity(0.8))
                .multilineTextAlignment(.center)
        }
        .padding()
    }
    
    private var requestsList: some View {
        ScrollView {
            VStack(spacing: 12) {
                ForEach(viewModel.offlineRequests) { request in
                    RequestCard(
                        request: request,
                        onTap: { viewModel.selectRequest(request) },
                        onDelete: { viewModel.deleteRequest(request) }
                    )
                }
            }
        }
    }
    
    private var actionButtons: some View {
        HStack(spacing: 16) {
            // Sync Button
            Button(action: viewModel.syncData) {
                HStack {
                    Image(systemName: "arrow.triangle.2.circlepath")
                    Text("データを同期")
                }
                .foregroundColor(.white)
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .background(
                    viewModel.isOnline && viewModel.unsyncedCount > 0
                        ? Color.green
                        : Color.gray
                )
                .cornerRadius(25)
            }
            .disabled(!viewModel.isOnline || viewModel.unsyncedCount == 0 || viewModel.isLoading)
            
            // Clear Button
            if viewModel.totalCount > 0 {
                Button(action: viewModel.clearAllData) {
                    Image(systemName: "trash")
                        .foregroundColor(.white)
                        .padding(12)
                        .background(Color.red)
                        .clipShape(Circle())
                }
            }
        }
    }
    
    private var addRequestSheet: some View {
        NavigationView {
            Form {
                Section(header: Text("患者情報")) {
                    TextField("患者名", text: $patientName)
                    TextField("症状", text: $symptoms)
                    TextField("詳細", text: $transcriptionText, axis: .vertical)
                        .lineLimit(3...6)
                }
            }
            .navigationTitle("新規データ追加")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("キャンセル") {
                        showAddRequest = false
                        clearForm()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("保存") {
                        saveRequest()
                    }
                    .disabled(patientName.isEmpty || symptoms.isEmpty)
                }
            }
        }
    }
    
    private func saveRequest() {
        viewModel.saveRequest(
            patientName: patientName,
            symptoms: symptoms,
            transcriptionText: transcriptionText.isEmpty ? nil : transcriptionText,
            audioFilePath: nil
        )
        showAddRequest = false
        clearForm()
    }
    
    private func clearForm() {
        patientName = ""
        symptoms = ""
        transcriptionText = ""
    }
}

struct RequestCard: View {
    let request: MedicalRequest
    let onTap: () -> Void
    let onDelete: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(request.patientName)
                        .font(.headline)
                        .foregroundColor(.primary)
                    
                    Text(request.symptoms)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
                
                Spacer()
                
                VStack(alignment: .trailing, spacing: 4) {
                    if request.isSynced {
                        Image(systemName: "checkmark.icloud.fill")
                            .foregroundColor(.green)
                    } else {
                        Image(systemName: "icloud.slash")
                            .foregroundColor(.orange)
                    }
                    
                    Text(formatDate(request.timestamp))
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            }
            
            if let transcription = request.transcriptionText {
                Text(transcription)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .lineLimit(2)
                    .padding(.top, 4)
            }
        }
        .padding()
        .background(Color.white.opacity(0.95))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.1), radius: 2, x: 0, y: 1)
        .onTapGesture(perform: onTap)
        .contextMenu {
            Button(role: .destructive) {
                onDelete()
            } label: {
                Label("削除", systemImage: "trash")
            }
        }
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ja_JP")
        formatter.dateFormat = "MM/dd HH:mm"
        return formatter.string(from: date)
    }
}

struct F8View_Previews: PreviewProvider {
    static var previews: some View {
        F8View()
    }
}