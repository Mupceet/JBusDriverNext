package me.jbusdriver.modern.ui.sitemanager

import android.widget.Toast
import me.jbusdriver.modern.core.copy
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.R
import me.jbusdriver.modern.data.mirror.MirrorUrl
import me.jbusdriver.modern.data.mirror.ScanPhase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteManagerScreen(
    onBack: () -> Unit,
    viewModel: SiteManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    // 分组各自的管理模式（点击「管理」后行尾才显示删除）；纯 UI 局部状态
    var customManageMode by remember { mutableStateOf(false) }
    var scannedManageMode by remember { mutableStateOf(false) }

    val customEntries = uiState.entries.filter { it.group == SiteGroup.CUSTOM }
    val scannedEntries = uiState.entries.filter { it.group == SiteGroup.SCANNED }
    // 管理中删空后自动退出管理模式（完成恢复为管理）
    LaunchedEffect(customEntries.isEmpty()) { if (customEntries.isEmpty()) customManageMode = false }
    LaunchedEffect(scannedEntries.isEmpty()) { if (scannedEntries.isEmpty()) scannedManageMode = false }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message.format(context), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.site_manager_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (uiState.isScanning && uiState.runningOp == ScanOp.VERIFY) {
                        // 測速進行中：取消入口在測速按鈕上
                        TextButton(
                            onClick = viewModel::cancelScan,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    } else {
                        TextButton(
                            onClick = viewModel::startVerify,
                            enabled = !uiState.isScanning
                        ) {
                            Text(stringResource(R.string.site_test_all))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SiteSection(
                title = stringResource(R.string.site_group_preset),
                entries = uiState.entries.filter { it.group == SiteGroup.PRESET },
                selectedUrl = uiState.selectedUrl,
                measurements = uiState.measurements,
                onSelect = viewModel::selectSite
            )

            // 自定義卡常顯在第二位；卡頭右上角為「添加 / 管理」
            SiteSection(
                title = stringResource(R.string.site_group_custom),
                entries = customEntries,
                selectedUrl = uiState.selectedUrl,
                measurements = uiState.measurements,
                onSelect = viewModel::selectSite,
                onDelete = viewModel::deleteEntry,
                showDelete = customManageMode,
                action = {
                    TextButton(onClick = { showAddDialog = true }) {
                        Text(stringResource(R.string.site_add))
                    }
                    TextButton(
                        onClick = { customManageMode = !customManageMode },
                        enabled = customEntries.isNotEmpty()
                    ) {
                        Text(
                            if (customManageMode) stringResource(R.string.site_manage_done)
                            else stringResource(R.string.site_manage)
                        )
                    }
                },
                emptyHint = stringResource(R.string.site_custom_empty)
            )

            SiteSection(
                title = stringResource(R.string.site_group_scanned),
                entries = scannedEntries,
                selectedUrl = uiState.selectedUrl,
                measurements = uiState.measurements,
                onSelect = viewModel::selectSite,
                onDelete = viewModel::deleteEntry,
                showDelete = scannedManageMode,
                action = {
                    if (uiState.isScanning && uiState.runningOp == ScanOp.SCAN) {
                        // 僅掃描進行中顯示取消；測速時的取消在頂欄測速按鈕上
                        TextButton(
                            onClick = viewModel::cancelScan,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    } else {
                        TextButton(
                            onClick = viewModel::startScan,
                            enabled = !uiState.isScanning
                        ) {
                            Text(
                                if (uiState.scanPhase == ScanPhase.DONE) stringResource(R.string.rescan)
                                else stringResource(R.string.site_scan)
                            )
                        }
                    }
                    // 掃描進行中禁用管理，避免行刪除與掃描持久化互相覆蓋；空列表置灰
                    TextButton(
                        onClick = { scannedManageMode = !scannedManageMode },
                        enabled = !uiState.isScanning && scannedEntries.isNotEmpty()
                    ) {
                        Text(
                            if (scannedManageMode) stringResource(R.string.site_manage_done)
                            else stringResource(R.string.site_manage)
                        )
                    }
                },
                emptyHint = stringResource(R.string.site_scan_empty)
            )

            if (uiState.isScanning) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                val phaseText = when (uiState.scanPhase) {
                    ScanPhase.DISCOVERING -> stringResource(
                        R.string.scanning, uiState.scannedCount, uiState.totalCount
                    )
                    ScanPhase.VERIFYING -> stringResource(
                        R.string.verifying, uiState.scannedCount, uiState.totalCount
                    )
                    else -> ""
                }
                if (phaseText.isNotEmpty()) {
                    Text(
                        phaseText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (uiState.currentUrl.isNotBlank()) {
                    Text(
                        uiState.currentUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showAddDialog) {
        AddCustomUrlDialog(
            onDismiss = { showAddDialog = false },
            onSave = { url ->
                showAddDialog = false
                viewModel.addCustomUrl(url)
            }
        )
    }
}

@Composable
private fun SiteSection(
    title: String,
    entries: List<SiteEntry>,
    selectedUrl: String,
    measurements: Map<String, MirrorUrl>,
    onSelect: (String) -> Unit,
    onDelete: ((SiteEntry) -> Unit)? = null,
    showDelete: Boolean = false,
    action: (@Composable () -> Unit)? = null,
    emptyHint: String? = null
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                action?.invoke()
            }
            if (entries.isEmpty()) {
                if (emptyHint != null) {
                    Text(
                        emptyHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            } else {
                entries.forEach { entry ->
                    SiteEntryRow(
                        entry = entry,
                        selected = entry.url == selectedUrl,
                        measured = measurements[entry.url],
                        onClick = { onSelect(entry.url) },
                        // 刪除按鈕僅在管理模式下顯示，常態下讓網址與測速徽標有完整寬度
                        onDelete = onDelete?.takeIf { showDelete }?.let { cb -> { cb(entry) } }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteEntryRow(
    entry: SiteEntry,
    selected: Boolean,
    measured: MirrorUrl?,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val context = LocalContext.current
    val copiedText = stringResource(R.string.copied)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                // 长按复制网址（与设置页网址行一致）
                onLongClick = {
                    context.copy(entry.url)
                    Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                }
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.size(40.dp))
        Text(
            entry.url,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (measured != null) {
            if (!measured.isReachable) {
                Text(
                    stringResource(R.string.unreachable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    "${measured.latencyMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        if (onDelete != null) {
            // 40dp 可点击图标与 RadioButton 同高，避免管理模式切换时行高跳动
            Icon(
                painterResource(R.drawable.delete_24px),
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onDelete)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun AddCustomUrlDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.site_add_title)) },
        text = {
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.site_add_hint)) },
                placeholder = { Text("https://…") }
            )
        },
        confirmButton = {
            TextButton(
                enabled = urlText.isNotBlank(),
                onClick = { onSave(urlText) }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
