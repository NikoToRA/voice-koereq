import SwiftUI

struct F7View: View {
    @StateObject private var viewModel = F7ViewModel()
    @State private var showingGenerateSheet = false
    @State private var transcriptionId = ""
    @State private var showingExportSheet = false
    @State private var exportedText = ""
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                searchBar
                
                if viewModel.hasNoResults {
                    emptyStateView
                } else if viewModel.hasNoFilteredResults {
                    noResultsView
                } else {
                    summaryList
                }
            }
            .navigationTitle("サマリー生成")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        showingGenerateSheet = true
                    }) {
                        Image(systemName: "plus.circle.fill")
                            .font(.title2)
                    }
                    .accessibilityLabel("新しいサマリーを生成")
                }
                
                ToolbarItem(placement: .navigationBarLeading) {
                    Menu {
                        Section("並び替え") {
                            Button("日付（新しい順）") {
                                viewModel.changeSortOrder(to: .dateDescending)
                            }
                            Button("日付（古い順）") {
                                viewModel.changeSortOrder(to: .dateAscending)
                            }
                            Button("タイトル（昇順）") {
                                viewModel.changeSortOrder(to: .titleAscending)
                            }
                            Button("タイトル（降順）") {
                                viewModel.changeSortOrder(to: .titleDescending)
                            }
                        }
                    } label: {
                        Image(systemName: "arrow.up.arrow.down.circle")
                            .font(.title3)
                    }
                    .accessibilityLabel("並び替えオプション")
                }
            }
        }
        .onAppear {
            viewModel.loadSummaries()
        }
        .sheet(isPresented: $showingGenerateSheet) {
            generateSummarySheet
        }
        .sheet(isPresented: $showingExportSheet) {
            ShareSheet(activityItems: [exportedText])
        }
        .alert("エラー", isPresented: $viewModel.showError) {
            Button("OK") {
                viewModel.showError = false
            }
        } message: {
            Text(viewModel.errorMessage ?? "不明なエラーが発生しました")
        }
        .overlay {
            if viewModel.isLoading {
                ProgressView("処理中...")
                    .padding()
                    .background(Color(.systemBackground))
                    .cornerRadius(10)
                    .shadow(radius: 5)
            }
        }
    }
    
    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.secondary)
            
            TextField("サマリーを検索", text: $viewModel.searchText)
                .textFieldStyle(RoundedBorderTextFieldStyle())
            
            if !viewModel.searchText.isEmpty {
                Button(action: viewModel.clearSearch) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                }
                .accessibilityLabel("検索をクリア")
            }
        }
        .padding()
        .background(Color(.systemGray6))
    }
    
    private var emptyStateView: some View {
        VStack(spacing: 20) {
            Image(systemName: "doc.text.magnifyingglass")
                .font(.system(size: 60))
                .foregroundColor(.secondary)
            
            Text("サマリーがありません")
                .font(.title2)
                .fontWeight(.semibold)
            
            Text("新しいサマリーを生成するには\n右上の＋ボタンをタップしてください")
                .multilineTextAlignment(.center)
                .foregroundColor(.secondary)
            
            Button(action: {
                showingGenerateSheet = true
            }) {
                Label("サマリーを生成", systemImage: "plus.circle")
                    .padding()
                    .background(Color.accentColor)
                    .foregroundColor(.white)
                    .cornerRadius(10)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    private var noResultsView: some View {
        VStack(spacing: 20) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 60))
                .foregroundColor(.secondary)
            
            Text("検索結果がありません")
                .font(.title2)
                .fontWeight(.semibold)
            
            Text("別のキーワードで検索してください")
                .foregroundColor(.secondary)
            
            Button(action: viewModel.clearSearch) {
                Text("検索をクリア")
                    .padding()
                    .background(Color.accentColor)
                    .foregroundColor(.white)
                    .cornerRadius(10)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    private var summaryList: some View {
        List {
            ForEach(viewModel.filteredSummaries) { summary in
                SummaryRow(
                    summary: summary,
                    onTap: {
                        viewModel.selectSummary(summary)
                    },
                    onExport: {
                        exportedText = viewModel.exportSummary(summary)
                        showingExportSheet = true
                    },
                    onDelete: {
                        viewModel.deleteSummary(summary)
                    }
                )
            }
        }
        .listStyle(PlainListStyle())
        .sheet(item: $viewModel.selectedSummary) { summary in
            SummaryDetailView(summary: summary) {
                viewModel.selectedSummary = nil
            }
        }
    }
    
    private var generateSummarySheet: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text("文字起こしIDを入力してください")
                    .font(.headline)
                    .padding(.top)
                
                TextField("文字起こしID", text: $transcriptionId)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .padding(.horizontal)
                
                Spacer()
                
                Button(action: {
                    viewModel.generateSummary(from: transcriptionId)
                    showingGenerateSheet = false
                    transcriptionId = ""
                }) {
                    Text("サマリーを生成")
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(transcriptionId.isEmpty ? Color.gray : Color.accentColor)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                }
                .disabled(transcriptionId.isEmpty)
                .padding(.horizontal)
                .padding(.bottom)
            }
            .navigationTitle("新しいサマリー")
            .navigationBarItems(
                leading: Button("キャンセル") {
                    showingGenerateSheet = false
                    transcriptionId = ""
                }
            )
        }
    }
}

struct SummaryRow: View {
    let summary: F7Summary
    let onTap: () -> Void
    let onExport: () -> Void
    let onDelete: () -> Void
    
    private let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        formatter.locale = Locale(identifier: "ja_JP")
        return formatter
    }()
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(summary.title)
                    .font(.headline)
                    .lineLimit(1)
                
                Spacer()
                
                Menu {
                    Button(action: onExport) {
                        Label("エクスポート", systemImage: "square.and.arrow.up")
                    }
                    
                    Button(role: .destructive, action: onDelete) {
                        Label("削除", systemImage: "trash")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .font(.title3)
                        .foregroundColor(.secondary)
                }
            }
            
            Text(summary.summary)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .lineLimit(2)
            
            HStack {
                Label(dateFormatter.string(from: summary.generatedAt), systemImage: "calendar")
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                Spacer()
                
                Text("\(summary.keyPoints.count)個の要点")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 8)
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
    }
}

struct SummaryDetailView: View {
    let summary: F7Summary
    let onDismiss: () -> Void
    
    private let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.timeStyle = .short
        formatter.locale = Locale(identifier: "ja_JP")
        return formatter
    }()
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("生成日時")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text(dateFormatter.string(from: summary.generatedAt))
                            .font(.body)
                    }
                    
                    VStack(alignment: .leading, spacing: 8) {
                        Text("サマリー")
                            .font(.headline)
                        Text(summary.summary)
                            .font(.body)
                    }
                    
                    VStack(alignment: .leading, spacing: 12) {
                        Text("要点")
                            .font(.headline)
                        
                        ForEach(Array(summary.keyPoints.enumerated()), id: \.offset) { index, point in
                            HStack(alignment: .top, spacing: 12) {
                                Text("\(index + 1).")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .frame(width: 20, alignment: .trailing)
                                
                                Text(point)
                                    .font(.body)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                    }
                    
                    VStack(alignment: .leading, spacing: 8) {
                        Text("関連する文字起こしID")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text(summary.transcriptionId)
                            .font(.body)
                            .fontDesign(.monospaced)
                    }
                }
                .padding()
            }
            .navigationTitle(summary.title)
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("閉じる", action: onDismiss)
                }
            }
        }
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let activityItems: [Any]
    
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }
    
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

struct F7View_Previews: PreviewProvider {
    static var previews: some View {
        F7View()
    }
}