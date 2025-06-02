package com.voicekoereq.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicekoereq.repository.F7Summary
import com.voicekoereq.viewmodel.F7ViewModel
import com.voicekoereq.viewmodel.SortOrder
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun F7Screen(
    viewModel: F7ViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredSummaries by viewModel.filteredSummaries.collectAsStateWithLifecycle()
    
    var showSortMenu by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("サマリー生成") },
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "並び替え")
                    }
                    
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("日付（新しい順）") },
                            onClick = {
                                viewModel.changeSortOrder(SortOrder.DATE_DESCENDING)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("日付（古い順）") },
                            onClick = {
                                viewModel.changeSortOrder(SortOrder.DATE_ASCENDING)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("タイトル（昇順）") },
                            onClick = {
                                viewModel.changeSortOrder(SortOrder.TITLE_ASCENDING)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("タイトル（降順）") },
                            onClick = {
                                viewModel.changeSortOrder(SortOrder.TITLE_DESCENDING)
                                showSortMenu = false
                            }
                        )
                    }
                    
                    IconButton(onClick = { viewModel.showGenerateDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "新しいサマリーを生成")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showGenerateDialog() }
            ) {
                Icon(Icons.Default.Add, contentDescription = "新しいサマリーを生成")
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = rememberSnackbarHostState()
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Search Bar
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    onClearSearch = viewModel::clearSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                // Content
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.summaries.isEmpty() -> {
                        EmptyState(onGenerateClick = viewModel::showGenerateDialog)
                    }
                    filteredSummaries.isEmpty() -> {
                        NoResultsState(onClearSearch = viewModel::clearSearch)
                    }
                    else -> {
                        SummaryList(
                            summaries = filteredSummaries,
                            onSummaryClick = viewModel::selectSummary,
                            onDeleteClick = viewModel::showDeleteDialog
                        )
                    }
                }
            }
            
            // Loading overlay
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = false) { },
                    contentAlignment = Alignment.Center
                ) {
                    Card {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }
        }
    }
    
    // Dialogs
    if (uiState.showGenerateDialog) {
        GenerateSummaryDialog(
            transcriptionId = uiState.transcriptionIdInput,
            onTranscriptionIdChange = viewModel::updateTranscriptionIdInput,
            onConfirm = viewModel::generateSummary,
            onDismiss = viewModel::hideGenerateDialog
        )
    }
    
    if (uiState.showDeleteDialog && uiState.summaryToDelete != null) {
        DeleteConfirmationDialog(
            summary = uiState.summaryToDelete!!,
            onConfirm = viewModel::deleteSummary,
            onDismiss = viewModel::hideDeleteDialog
        )
    }
    
    uiState.selectedSummary?.let { summary ->
        SummaryDetailDialog(
            summary = summary,
            onDismiss = viewModel::clearSelectedSummary,
            onExport = { viewModel.exportSummary(summary) }
        )
    }
    
    // Error handling
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            // Show error in snackbar
            viewModel.clearError()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("サマリーを検索") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearSearch) {
                    Icon(Icons.Default.Clear, contentDescription = "検索をクリア")
                }
            }
        },
        singleLine = true
    )
}

@Composable
private fun EmptyState(
    onGenerateClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "サマリーがありません",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "新しいサマリーを生成するには\n右下の＋ボタンをタップしてください",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGenerateClick) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("サマリーを生成")
        }
    }
}

@Composable
private fun NoResultsState(
    onClearSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "検索結果がありません",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "別のキーワードで検索してください",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onClearSearch) {
            Text("検索をクリア")
        }
    }
}

@Composable
private fun SummaryList(
    summaries: List<F7Summary>,
    onSummaryClick: (F7Summary) -> Unit,
    onDeleteClick: (F7Summary) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(summaries) { summary ->
            SummaryCard(
                summary = summary,
                onClick = { onSummaryClick(summary) },
                onDeleteClick = { onDeleteClick(summary) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryCard(
    summary: F7Summary,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPANESE) }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = summary.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "削除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateFormat.format(summary.generatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${summary.keyPoints.size}個の要点",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GenerateSummaryDialog(
    transcriptionId: String,
    onTranscriptionIdChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新しいサマリー生成") },
        text = {
            Column {
                Text("文字起こしIDを入力してください")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = transcriptionId,
                    onValueChange = onTranscriptionIdChange,
                    label = { Text("文字起こしID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = transcriptionId.isNotBlank()
            ) {
                Text("生成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
private fun DeleteConfirmationDialog(
    summary: F7Summary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("サマリーの削除") },
        text = {
            Text("「${summary.title}」を削除してもよろしいですか？\nこの操作は取り消せません。")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("削除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
private fun SummaryDetailDialog(
    summary: F7Summary,
    onDismiss: () -> Unit,
    onExport: () -> String
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.JAPANESE) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(summary.title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Date
                Text(
                    "生成日時",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    dateFormat.format(summary.generatedAt),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Summary
                Text(
                    "サマリー",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    summary.summary,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Key Points
                Text(
                    "要点",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                summary.keyPoints.forEachIndexed { index, point ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            point,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Transcription ID
                Text(
                    "関連する文字起こしID",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    summary.transcriptionId,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Export functionality would be implemented here
                val exportText = onExport()
                // Share intent would be created here
            }) {
                Text("エクスポート")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}