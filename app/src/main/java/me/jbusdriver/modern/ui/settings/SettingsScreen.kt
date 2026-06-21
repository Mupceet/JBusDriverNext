package me.jbusdriver.modern.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.data.mirror.MirrorUrl
import me.jbusdriver.modern.data.mirror.ScanPhase
import me.jbusdriver.modern.data.mirror.ScanState
import me.jbusdriver.modern.data.settings.ForumFloorOrder
import me.jbusdriver.modern.data.settings.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val store = viewModel.store
    val scope = rememberCoroutineScope()

    // Appearance state
    val themeMode by store.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by store.dynamicColor.collectAsStateWithLifecycle()
    val showMovieTab by store.showMovieTab.collectAsStateWithLifecycle()
    val showActressTab by store.showActressTab.collectAsStateWithLifecycle()
    val showForumTab by store.showForumTab.collectAsStateWithLifecycle()
    val autoLoadGifs by store.autoLoadGifs.collectAsStateWithLifecycle()
    val forumFloorOrder by store.forumFloorOrder.collectAsStateWithLifecycle()

    // Network state
    val selectedBaseUrl by store.selectedBaseUrl.collectAsStateWithLifecycle()
    val cachedMirrorUrls by store.cachedMirrorUrls.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    // Mirror URL list for network card
    val mirrorUrls = if (scanState.phase == ScanPhase.DONE) {
        scanState.discoveredUrls
    } else if (!scanState.isScanning && cachedMirrorUrls.isNotEmpty()) {
        cachedMirrorUrls.map { MirrorUrl(it, true) }.sortedWith(
            compareBy<MirrorUrl> { it.url.contains("www.javbus.com", ignoreCase = true).not() }
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
                title = { Text("設置") },
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
            // === Appearance Card ===
            AppearanceCard(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                showMovieTab = showMovieTab,
                showActressTab = showActressTab,
                showForumTab = showForumTab,
                autoLoadGifs = autoLoadGifs,
                forumFloorOrder = forumFloorOrder,
                onThemeModeChange = { scope.launch { store.setThemeMode(it) } },
                onDynamicColorChange = { scope.launch { store.setDynamicColor(it) } },
                onShowMovieTabChange = { scope.launch { store.setShowMovieTab(it) } },
                onShowActressTabChange = { scope.launch { store.setShowActressTab(it) } },
                onShowForumTabChange = { scope.launch { store.setShowForumTab(it) } },
                onAutoLoadGifsChange = { scope.launch { store.setAutoLoadGifs(it) } },
                onForumFloorOrderChange = { scope.launch { store.setForumFloorOrder(it) } }
            )

            // === Network Card ===
            NetworkCard(
                selectedBaseUrl = selectedBaseUrl,
                mirrorUrls = mirrorUrls,
                scanState = scanState,
                hasCachedUrls = hasCachedUrls,
                onScan = { viewModel.startScan() },
                onCancel = { viewModel.cancelScan() },
                onVerify = { viewModel.startVerify() },
                onSelect = { viewModel.selectUrl(it) }
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

//region Appearance Card

@Composable
private fun AppearanceCard(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    showMovieTab: Boolean,
    showActressTab: Boolean,
    showForumTab: Boolean,
    autoLoadGifs: Boolean,
    forumFloorOrder: ForumFloorOrder,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onShowMovieTabChange: (Boolean) -> Unit,
    onShowActressTabChange: (Boolean) -> Unit,
    onShowForumTabChange: (Boolean) -> Unit,
    onAutoLoadGifsChange: (Boolean) -> Unit,
    onForumFloorOrderChange: (ForumFloorOrder) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            // Header
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.settings_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "外觀",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))

            // Theme mode — full-width clickable, content padded
            var themeExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { themeExpanded = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("主題模式", style = MaterialTheme.typography.bodyMedium)
                Box {
                    Text(
                        themeModeLabel(themeMode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    DropdownMenu(
                        expanded = themeExpanded,
                        onDismissRequest = { themeExpanded = false }
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(themeModeLabel(mode)) },
                                onClick = {
                                    onThemeModeChange(mode)
                                    themeExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Dynamic color
            val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (supportsDynamicColor)
                            Modifier.clickable { onDynamicColorChange(!dynamicColor) }
                        else Modifier
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "動態顏色",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = if (supportsDynamicColor) Modifier else Modifier.alpha(0.38f)
                        )
                        if (!supportsDynamicColor) {
                            Text(
                                "需要 Android 12+",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.alpha(0.6f)
                            )
                        }
                    }
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = null,
                        enabled = supportsDynamicColor
                    )
                }
            }

            // Movie tab
            SwitchRow("顯示影片", showMovieTab, onShowMovieTabChange)

            // Actress tab
            SwitchRow("顯示演員", showActressTab, onShowActressTabChange)

            // Forum tab
            SwitchRow("顯示論壇", showForumTab, onShowForumTabChange)

            // Forum sub-panel
            AnimatedVisibility(visible = showForumTab) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SwitchRow("自動載入動圖", autoLoadGifs, onAutoLoadGifsChange)

                    Spacer(Modifier.height(8.dp))

                    // Floor order — full-width clickable, content padded
                    var floorExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { floorExpanded = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("樓層瀏覽順序", style = MaterialTheme.typography.bodyMedium)
                        Box {
                            Text(
                                floorOrderLabel(forumFloorOrder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            DropdownMenu(
                                expanded = floorExpanded,
                                onDismissRequest = { floorExpanded = false }
                            ) {
                                ForumFloorOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(floorOrderLabel(order)) },
                                        onClick = {
                                            onForumFloorOrderChange(order)
                                            floorExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

private fun themeModeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> "遵循系統"
    ThemeMode.LIGHT -> "亮色主題"
    ThemeMode.DARK -> "暗色主題"
}

private fun floorOrderLabel(order: ForumFloorOrder) = when (order) {
    ForumFloorOrder.REGULAR -> "正序"
    ForumFloorOrder.REVERSE -> "倒序"
}

//endregion

//region Network Card

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkCard(
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
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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
                            if (scanState.phase == ScanPhase.DONE) stringResource(R.string.rescan)
                            else stringResource(R.string.scan_url)
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
                    stringResource(scanState.error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

//endregion
