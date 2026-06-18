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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.R
import me.jbusdriver.modern.data.ForumFloorOrder
import me.jbusdriver.modern.data.MirrorUrl
import me.jbusdriver.modern.data.ScanPhase
import me.jbusdriver.modern.data.ScanState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabSettingsScreen(
    onBack: () -> Unit,
    viewModel: LabSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lab_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.back)
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
                stringResource(R.string.lab_experimental),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Forum card
            ForumCard(
                forumEnabled = uiState.forumEnabled,
                autoLoadGifs = uiState.autoLoadGifs,
                forumFloorOrder = uiState.forumFloorOrder,
                onForumEnabledChange = viewModel::setForumEnabled,
                onAutoLoadGifsChange = viewModel::setAutoLoadGifs,
                onForumFloorOrderChange = viewModel::setForumFloorOrder
            )

            // URL selection card
            UrlSelectionCard(
                selectedBaseUrl = uiState.selectedBaseUrl,
                mirrorUrls = uiState.mirrorUrls,
                scanState = uiState.scanState,
                hasCachedUrls = uiState.hasCachedUrls,
                onScan = { viewModel.startScan() },
                onCancel = { viewModel.cancelScan() },
                onVerify = { viewModel.startVerify() },
                onSelect = { viewModel.selectUrl(it) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForumCard(
    forumEnabled: Boolean,
    autoLoadGifs: Boolean,
    forumFloorOrder: ForumFloorOrder,
    onForumEnabledChange: (Boolean) -> Unit,
    onAutoLoadGifsChange: (Boolean) -> Unit,
    onForumFloorOrderChange: (ForumFloorOrder) -> Unit
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
                    stringResource(R.string.forum_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.forum_settings_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.forum_enable),
                    style = MaterialTheme.typography.bodyMedium
                )
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
                    Text(
                        stringResource(R.string.auto_load_gif),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.auto_load_gif_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoLoadGifs,
                    onCheckedChange = onAutoLoadGifsChange
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.floor_order),
                    style = MaterialTheme.typography.bodyMedium
                )
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = forumFloorOrder == ForumFloorOrder.REGULAR,
                        onClick = { onForumFloorOrderChange(ForumFloorOrder.REGULAR) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {}
                    ) {
                        Text(stringResource(R.string.floor_order_regular))
                    }
                    SegmentedButton(
                        selected = forumFloorOrder == ForumFloorOrder.REVERSE,
                        onClick = { onForumFloorOrderChange(ForumFloorOrder.REVERSE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {}
                    ) {
                        Text(stringResource(R.string.floor_order_reverse))
                    }
                }
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
                    stringResource(R.string.select_url),
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
                                                stringResource(R.string.unreachable),
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
                        Text(stringResource(R.string.cancel))
                    }
                } else {
                    Button(
                        onClick = onScan,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (scanState.phase == ScanPhase.DONE) stringResource(R.string.rescan) else stringResource(
                                R.string.scan_url
                            )
                        )
                    }
                }

                if (hasCachedUrls) {
                    OutlinedButton(
                        onClick = onVerify,
                        modifier = Modifier.weight(1f),
                        enabled = !scanState.isScanning
                    ) {
                        Text(stringResource(R.string.check_connectivity))
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
                    ScanPhase.DISCOVERING -> stringResource(
                        R.string.scanning,
                        scanState.scannedCount,
                        scanState.totalCount
                    )

                    ScanPhase.VERIFYING -> stringResource(
                        R.string.verifying,
                        scanState.scannedCount,
                        scanState.totalCount
                    )

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
                    scanState.error?.let { stringResource(it) } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
