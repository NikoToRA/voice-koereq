package com.voicekoereq.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicekoereq.viewmodel.F2ViewModel
import com.voicekoereq.viewmodel.RecordingState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun F2Screen(
    viewModel: F2ViewModel = hiltViewModel(),
    onNavigateToHistory: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val audioLevel by viewModel.audioLevel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        viewModel.requestMicrophonePermission(context)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "音声録音",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 録音状態表示
                RecordingStatusSection(
                    state = uiState.recordingState,
                    recordingTime = uiState.recordingTimeText
                )
                
                // 音声レベルビジュアライザー
                if (uiState.recordingState == RecordingState.RECORDING) {
                    AudioVisualizer(
                        audioLevel = audioLevel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(horizontal = 16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // コントロールボタン
                RecordingControls(
                    state = uiState.recordingState,
                    hasRecordings = uiState.hasRecordings,
                    isLoading = uiState.isLoading,
                    onStartRecording = {
                        coroutineScope.launch {
                            viewModel.startRecording()
                        }
                    },
                    onPauseRecording = { viewModel.pauseRecording() },
                    onResumeRecording = { viewModel.resumeRecording() },
                    onStopRecording = {
                        coroutineScope.launch {
                            viewModel.stopRecording()
                        }
                    },
                    onNavigateToHistory = onNavigateToHistory
                )
            }
            
            // ローディングオーバーレイ
            if (uiState.isLoading) {
                LoadingOverlay()
            }
            
            // エラーダイアログ
            uiState.errorMessage?.let { error ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearError() },
                    title = { Text("エラー") },
                    text = { Text(error) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RecordingStatusSection(
    state: RecordingState,
    recordingTime: String
) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 40.dp)
    ) {
        Icon(
            imageVector = if (state == RecordingState.IDLE) Icons.Default.Mic else Icons.Default.MicExternalOn,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer(
                    scaleX = if (state == RecordingState.RECORDING) scale else 1f,
                    scaleY = if (state == RecordingState.RECORDING) scale else 1f
                ),
            tint = when (state) {
                RecordingState.RECORDING -> MaterialTheme.colorScheme.error
                RecordingState.PAUSED -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = when (state) {
                RecordingState.IDLE -> "準備完了"
                RecordingState.RECORDING -> "録音中"
                RecordingState.PAUSED -> "一時停止中"
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        if (state != RecordingState.IDLE) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = recordingTime,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Light
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AudioVisualizer(
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val bars = remember { mutableStateListOf(*Array(20) { 0f }) }
    
    LaunchedEffect(audioLevel) {
        bars.removeAt(0)
        bars.add(audioLevel)
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEach { level ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(level)
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
private fun RecordingControls(
    state: RecordingState,
    hasRecordings: Boolean,
    isLoading: Boolean,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        when (state) {
            RecordingState.IDLE -> {
                Button(
                    onClick = onStartRecording,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "録音開始",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            RecordingState.RECORDING, RecordingState.PAUSED -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 一時停止/再開ボタン
                    FloatingActionButton(
                        onClick = if (state == RecordingState.PAUSED) onResumeRecording else onPauseRecording,
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(70.dp)
                    ) {
                        Icon(
                            imageVector = if (state == RecordingState.PAUSED) 
                                Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    // 停止ボタン
                    FloatingActionButton(
                        onClick = onStopRecording,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(70.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // 録音履歴へのリンク
        if (hasRecordings) {
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(
                onClick = onNavigateToHistory
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "録音履歴",
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "処理中...",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}