package me.jbusdriver.modern.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.data.LabSettingsStore
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
    val autoLoadGifs by viewModel.store.autoLoadGifs.collectAsStateWithLifecycle()
    val selectedBaseUrl by viewModel.store.selectedBaseUrl.collectAsStateWithLifecycle()
    val cachedMirrorUrls by viewModel.store.cachedMirrorUrls.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val mirrorUrls = if (scanState.phase == ScanPhase.DONE) {
        scanState.discoveredUrls
    } else if (!scanState.isScanning && cachedMirrorUrls.isNotEmpty()) {
        val defaultHost = "www.javbus.com"
        cachedMirrorUrls.map { MirrorUrl(it, true) }.sortedWith(
            compareBy<MirrorUrl> { it.url.contains(defaultHost, ignoreCase = true).not() }
                .thenBy { if (it.isReachable) it.latencyMs else Long.MAX_VALUE }
                .thenBy { it.url }
        )
    } else {
        emptyList()
    }
    val hasCachedUrls = mirrorUrls.isNotEmpty() || cachedMirrorUrls.isNotEmpty()

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
            ForumCard(
                forumEnabled = forumEnabled,
                autoLoadGifs = autoLoadGifs,
                onForumEnabledChange = { scope.launch { viewModel.store.setForumEnabled(it) } },
                onAutoLoadGifsChange = { scope.launch { viewModel.store.setAutoLoadGifs(it) } }
            )

            // URL selection card
            UrlSelectionCard(
                selectedBaseUrl = selectedBaseUrl,
                mirrorUrls = mirrorUrls,
                scanState = scanState,
                hasCachedUrls = hasCachedUrls,
                onScan = { viewModel.startScan() },
                onCancel = { viewModel.cancelScan() },
                onVerify = { viewModel.startVerify() },
                onSelect = { viewModel.selectUrl(it) }
            )
        }
    }
}

@Composable
private fun ForumCard(
    forumEnabled: Boolean,
    autoLoadGifs: Boolean,
    onForumEnabledChange: (Boolean) -> Unit,
    onAutoLoadGifsChange: (Boolean) -> Unit
) {
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
                    onCheckedChange = onForumEnabledChange
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("自動載入動圖", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "論壇帖中的 GIF 圖片無需點擊直接播放",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoLoadGifs,
                    onCheckedChange = onAutoLoadGifsChange
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UrlSelectionCard(
    selectedBaseUrl: String,
    mirrorUrls: List<MirrorUrl>,
    scanState: ScanState,
    hasCachedUrls: Boolean,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onVerify: () -> Unit,
    onSelect: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.public_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "選擇網址",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(12.dp))

            // Dropdown spinner for URL selection
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded && mirrorUrls.isNotEmpty(),
                onExpandedChange = { if (mirrorUrls.isNotEmpty()) expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedBaseUrl,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = {
                        if (mirrorUrls.isNotEmpty()) {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        }
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                if (mirrorUrls.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        mirrorUrls.forEach { mirror ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
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
                                        } else if (mirror.latencyMs >= 0) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "${mirror.latencyMs} ms",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    if (mirror.isReachable) {
                                        onSelect(mirror.url)
                                    }
                                    expanded = false
                                },
                                enabled = mirror.isReachable
                            )
                        }
                    }
                }
            }

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
                        Text("取消")
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
                        onClick = onVerify,
                        modifier = Modifier.weight(1f),
                        enabled = !scanState.isScanning
                    ) {
                        Text("檢測連通")
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
