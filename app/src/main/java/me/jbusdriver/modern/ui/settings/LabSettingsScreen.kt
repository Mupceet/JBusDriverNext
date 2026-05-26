package me.jbusdriver.modern.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.R
import me.jbusdriver.modern.data.MirrorUrl
import me.jbusdriver.modern.data.ScanPhase
import me.jbusdriver.modern.data.ScanState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabSettingsScreen(
    onBack: () -> Unit,
    viewModel: LabSettingsViewModel = hiltViewModel()
) {
    val forumEnabled by viewModel.store.forumEnabled.collectAsStateWithLifecycle()
    val selectedBaseUrl by viewModel.store.selectedBaseUrl.collectAsStateWithLifecycle()
    val cachedMirrorUrls by viewModel.store.cachedMirrorUrls.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    // Determine available URLs for the dialog
    val dialogUrls = if (scanState.phase == ScanPhase.DONE) {
        scanState.discoveredUrls
    } else if (!scanState.isScanning && cachedMirrorUrls.isNotEmpty()) {
        cachedMirrorUrls.map { MirrorUrl(it, true) }
    } else {
        emptyList()
    }
    var showUrlDialog by remember { mutableStateOf(false) }

    if (showUrlDialog && dialogUrls.isNotEmpty()) {
        MirrorUrlDialog(
            urls = dialogUrls,
            selectedUrl = selectedBaseUrl,
            onSelect = { viewModel.selectUrl(it) },
            onDismiss = { showUrlDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("實驗室") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "實驗性功能可能不穩定",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Forum card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.forum_24px),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "論壇功能",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "瀏覽論壇版塊、閱讀和參與帖子討論",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("啟用", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = forumEnabled,
                            onCheckedChange = { viewModel.store.setForumEnabled(it) }
                        )
                    }
                }
            }

            // URL selection card
            UrlSelectionCard(
                selectedBaseUrl = selectedBaseUrl,
                scanState = scanState,
                hasCachedUrls = dialogUrls.isNotEmpty(),
                onScan = { viewModel.startScan() },
                onCancel = { viewModel.cancelScan() },
                onShowUrls = { showUrlDialog = true }
            )
        }
    }
}

@Composable
private fun MirrorUrlDialog(
    urls: List<MirrorUrl>,
    selectedUrl: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "選擇網址",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.5f)
                ) {
                    items(urls, key = { it.url }) { mirror ->
                        val isSelected = mirror.url == selectedUrl
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = mirror.isReachable) {
                                    onSelect(mirror.url)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painterResource(
                                    if (isSelected) R.drawable.radio_button_checked_24px
                                    else R.drawable.radio_button_unchecked_24px
                                ),
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                mirror.url,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (!mirror.isReachable) Modifier.alpha(0.4f) else Modifier),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!mirror.isReachable) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "不可達",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("確認")
                }
            }
        }
    }
}

@Composable
private fun UrlSelectionCard(
    selectedBaseUrl: String,
    scanState: ScanState,
    hasCachedUrls: Boolean,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onShowUrls: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "網址選擇",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "當前：${selectedBaseUrl}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (scanState.isScanning) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("取消掃描")
                    }
                } else {
                    Button(
                        onClick = onScan,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (scanState.phase == ScanPhase.DONE) "重新掃描" else "掃描網址")
                    }
                }

                if (hasCachedUrls) {
                    OutlinedButton(
                        onClick = onShowUrls,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("選擇網址")
                    }
                }
            }

            // Progress
            if (scanState.isScanning) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                val phaseText = when (scanState.phase) {
                    ScanPhase.DISCOVERING -> "正在掃描 ${scanState.scannedCount}/${scanState.totalCount}…"
                    ScanPhase.VERIFYING -> "正在驗證 ${scanState.scannedCount}/${scanState.totalCount}…"
                    else -> ""
                }
                Text(
                    phaseText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (scanState.currentUrl.isNotBlank()) {
                    Text(
                        scanState.currentUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Error
            if (scanState.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    scanState.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
