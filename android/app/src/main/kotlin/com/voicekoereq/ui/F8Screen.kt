package com.voicekoereq.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicekoereq.repository.MedicalRequest
import com.voicekoereq.viewmodel.F8ViewModel
import com.voicekoereq.viewmodel.F8UiEvent
import com.voicekoereq.viewmodel.F8UiState
import com.voicekoereq.viewmodel.SyncStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun F8Screen(
    viewModel: F8ViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var patientName by remember { mutableStateOf("") }
    var symptoms by remember { mutableStateOf("") }
    var transcriptionText by remember { mutableStateOf("") }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E88E5),
                        Color(0xFF1565C0)
                    )
                )
            )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("オフラインデータ") },
                    actions = {
                        IconButton(
                            onClick = { viewModel.handleEvent(F8UiEvent.ShowAddRequest) }
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "データ追加",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status Bar
                StatusBar(uiState)
                
                // Main Content
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White
                            )
                        }
                    }
                    uiState.offlineRequests.isEmpty() -> {
                        EmptyState()
                    }
                    else -> {
                        RequestsList(
                            requests = uiState.offlineRequests,
                            onRequestClick = { request ->
                                viewModel.handleEvent(F8UiEvent.SelectRequest(request))
                            },
                            onDeleteClick = { request ->
                                viewModel.handleEvent(F8UiEvent.DeleteRequest(request))
                            },
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Action Buttons
                ActionButtons(uiState, viewModel)
            }
        }
        
        // Add Request Dialog
        if (uiState.showAddRequest) {
            AlertDialog(
                onDismissRequest = { viewModel.handleEvent(F8UiEvent.HideAddRequest) },
                title = { Text("新規データ追加") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = patientName,
                            onValueChange = { patientName = it },
                            label = { Text("患者名") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = symptoms,
                            onValueChange = { symptoms = it },
                            label = { Text("症状") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = transcriptionText,
                            onValueChange = { transcriptionText = it },
                            label = { Text("詳細") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.handleEvent(
                                F8UiEvent.SaveRequest(
                                    patientName = patientName,
                                    symptoms = symptoms,
                                    transcriptionText = transcriptionText.ifEmpty { null },
                                    audioFilePath = null
                                )
                            )
                            patientName = ""
                            symptoms = ""
                            transcriptionText = ""
                        },
                        enabled = patientName.isNotEmpty() && symptoms.isNotEmpty()
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.handleEvent(F8UiEvent.HideAddRequest)
                            patientName = ""
                            symptoms = ""
                            transcriptionText = ""
                        }
                    ) {
                        Text("キャンセル")
                    }
                }
            )
        }
        
        // Delete Confirmation Dialog
        if (uiState.showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { viewModel.handleEvent(F8UiEvent.CancelDelete) },
                title = { Text("削除の確認") },
                text = { Text("このデータを削除してもよろしいですか？") },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.handleEvent(F8UiEvent.ConfirmDelete) }
                    ) {
                        Text("削除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.handleEvent(F8UiEvent.CancelDelete) }
                    ) {
                        Text("キャンセル")
                    }
                }
            )
        }
        
        // Sync Result Dialog
        if (uiState.showSyncResult) {
            uiState.syncResult?.let { result ->
                AlertDialog(
                    onDismissRequest = { viewModel.handleEvent(F8UiEvent.DismissSyncResult) },
                    title = { Text("同期結果") },
                    text = {
                        Text("成功: ${result.successCount}件\n失敗: ${result.failureCount}件")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { viewModel.handleEvent(F8UiEvent.DismissSyncResult) }
                        ) {
                            Text("OK")
                        }
                    }
                )
            }
        }
        
        // Error Dialog
        uiState.errorMessage?.let { error ->
            AlertDialog(
                onDismissRequest = { viewModel.handleEvent(F8UiEvent.DismissError) },
                title = { Text("エラー") },
                text = { Text(error) },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.handleEvent(F8UiEvent.DismissError) }
                    ) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
private fun StatusBar(uiState: F8UiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Connection Status
        Row(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(15.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (uiState.isOnline) Color.Green else Color.Red)
            )
            Text(
                text = if (uiState.isOnline) "オンライン" else "オフライン",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
        
        // Sync Status
        AnimatedVisibility(
            visible = uiState.syncStatus is SyncStatus.Syncing
        ) {
            Row(
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(15.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Text(
                    text = "同期中...",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
        
        // Data Count
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "${uiState.totalCount}件",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
            if (uiState.unsyncedCount > 0) {
                Text(
                    text = "未同期: ${uiState.unsyncedCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Yellow
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = "オフラインデータがありません",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Text(
                text = "インターネット接続がない場合、\nデータは自動的に保存されます",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun RequestsList(
    requests: List<MedicalRequest>,
    onRequestClick: (MedicalRequest) -> Unit,
    onDeleteClick: (MedicalRequest) -> Unit,
    viewModel: F8ViewModel,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(requests) { request ->
            RequestCard(
                request = request,
                onTap = { onRequestClick(request) },
                onDelete = { onDeleteClick(request) },
                viewModel = viewModel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestCard(
    request: MedicalRequest,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    viewModel: F8ViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() },
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = request.patientName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = request.symptoms,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (request.isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = if (request.isSynced) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                    Text(
                        text = viewModel.formatDate(request.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            request.transcriptionText?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "削除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(
    uiState: F8UiState,
    viewModel: F8ViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sync Button
        Button(
            onClick = { viewModel.handleEvent(F8UiEvent.SyncData) },
            enabled = uiState.isOnline && uiState.unsyncedCount > 0 && !uiState.isLoading,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isOnline && uiState.unsyncedCount > 0) Color(0xFF4CAF50) else Color.Gray
            )
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("データを同期")
        }
        
        // Clear Button
        AnimatedVisibility(
            visible = uiState.totalCount > 0
        ) {
            IconButton(
                onClick = { viewModel.handleEvent(F8UiEvent.ClearAllData) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF44336))
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = "すべて削除",
                    tint = Color.White
                )
            }
        }
    }
}