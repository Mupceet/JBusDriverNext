package me.jbusdriver.modern.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
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
import me.jbusdriver.modern.data.settings.MovieLoadMode
import me.jbusdriver.modern.data.settings.MovieListStyle
import me.jbusdriver.modern.data.settings.ThemeMode
import me.jbusdriver.modern.domain.model.LocalVideoSummary
import me.jbusdriver.modern.ui.components.SelectableDropdownItem
import java.text.DateFormat
import java.util.Date
import java.util.Locale

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
    val movieListStyle by store.movieListStyle.collectAsStateWithLifecycle()
    val movieLoadMode by store.movieLoadMode.collectAsStateWithLifecycle()

    // Network state
    val selectedBaseUrl by store.selectedBaseUrl.collectAsStateWithLifecycle()
    val cachedMirrorUrls by store.cachedMirrorUrls.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    val localVideoSummary by viewModel.localVideoSummary.collectAsStateWithLifecycle()
    val isScanningVideos by viewModel.isScanningVideos.collectAsStateWithLifecycle()

    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let { viewModel.setLocalVideoFolder(it) } }

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
                movieListStyle = movieListStyle,
                movieLoadMode = movieLoadMode,
                onThemeModeChange = { scope.launch { store.setThemeMode(it) } },
                onDynamicColorChange = { scope.launch { store.setDynamicColor(it) } },
                onShowMovieTabChange = { scope.launch { store.setShowMovieTab(it) } },
                onShowActressTabChange = { scope.launch { store.setShowActressTab(it) } },
                onShowForumTabChange = { scope.launch { store.setShowForumTab(it) } },
                onAutoLoadGifsChange = { scope.launch { store.setAutoLoadGifs(it) } },
                onForumFloorOrderChange = { scope.launch { store.setForumFloorOrder(it) } },
                onMovieListStyleChange = { scope.launch { store.setMovieListStyle(it) } },
                onMovieLoadModeChange = { scope.launch { store.setMovieLoadMode(it) } }
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

            // === Local Video Card ===
            val showUncollectedLocal by viewModel.showUncollectedLocal.collectAsStateWithLifecycle()
            LocalVideoCard(
                summary = localVideoSummary,
                isScanning = isScanningVideos,
                showUncollectedLocal = showUncollectedLocal,
                onToggleShowUncollectedLocal = { viewModel.setShowUncollectedLocal(it) },
                onPickFolder = { pickFolderLauncher.launch(null) },
                onClearFolder = { viewModel.clearLocalVideoFolder() },
                onRescan = { viewModel.rescanLocalVideos() },
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
    movieListStyle: MovieListStyle,
    movieLoadMode: MovieLoadMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onShowMovieTabChange: (Boolean) -> Unit,
    onShowActressTabChange: (Boolean) -> Unit,
    onShowForumTabChange: (Boolean) -> Unit,
    onAutoLoadGifsChange: (Boolean) -> Unit,
    onForumFloorOrderChange: (ForumFloorOrder) -> Unit,
    onMovieListStyleChange: (MovieListStyle) -> Unit,
    onMovieLoadModeChange: (MovieLoadMode) -> Unit
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
                    style = MaterialTheme.typography.bodyLarge,
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
                Text("主題模式", style = MaterialTheme.typography.bodyLarge)
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
                            SelectableDropdownItem(
                                label = themeModeLabel(mode),
                                selected = mode == themeMode,
                                onClick = {
                                    onThemeModeChange(mode)
                                    themeExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Movie list layout
            MovieListStyleRow(movieListStyle, onMovieListStyleChange)

            // Movie default loading mode
            MovieLoadModeRow(movieLoadMode, onMovieLoadModeChange)

            // Dynamic color
            DynamicColorRow(dynamicColor, onDynamicColorChange)

            // Movie tab
            SwitchRow("顯示影片", showMovieTab, onShowMovieTabChange)

            // Actress tab
            SwitchRow("顯示演員", showActressTab, onShowActressTabChange)

            // Forum tab
            SwitchRow("顯示論壇", showForumTab, onShowForumTabChange)

            // Forum sub-options — lightly indented under the forum toggle when enabled
            AnimatedVisibility(visible = showForumTab) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp)) {
                    SwitchRow("自動載入動圖", autoLoadGifs, onAutoLoadGifsChange)
                    FloorOrderRow(forumFloorOrder, onForumFloorOrderChange)
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
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.scale(SwitchScale)
            )
        }
    }
}

@Composable
private fun DynamicColorRow(
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit
) {
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
                    style = MaterialTheme.typography.bodyLarge,
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
                enabled = supportsDynamicColor,
                modifier = Modifier.scale(SwitchScale)
            )
        }
    }
}

@Composable
private fun FloorOrderRow(
    forumFloorOrder: ForumFloorOrder,
    onForumFloorOrderChange: (ForumFloorOrder) -> Unit
) {
    var floorExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { floorExpanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("樓層瀏覽順序", style = MaterialTheme.typography.bodyLarge)
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
                    SelectableDropdownItem(
                        label = floorOrderLabel(order),
                        selected = order == forumFloorOrder,
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

@Composable
private fun MovieListStyleRow(
    movieListStyle: MovieListStyle,
    onMovieListStyleChange: (MovieListStyle) -> Unit
) {
    var styleExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { styleExpanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("影片列表樣式", style = MaterialTheme.typography.bodyLarge)
        Box {
            Text(
                movieListStyleLabel(movieListStyle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            DropdownMenu(
                expanded = styleExpanded,
                onDismissRequest = { styleExpanded = false }
            ) {
                MovieListStyle.entries.forEach { style ->
                    SelectableDropdownItem(
                        label = movieListStyleLabel(style),
                        selected = style == movieListStyle,
                        onClick = {
                            onMovieListStyleChange(style)
                            styleExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieLoadModeRow(
    movieLoadMode: MovieLoadMode,
    onMovieLoadModeChange: (MovieLoadMode) -> Unit
) {
    var modeExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { modeExpanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.movie_load_mode), style = MaterialTheme.typography.bodyLarge)
        Box {
            Text(
                movieLoadModeLabel(movieLoadMode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            DropdownMenu(
                expanded = modeExpanded,
                onDismissRequest = { modeExpanded = false }
            ) {
                MovieLoadMode.entries.forEach { mode ->
                    SelectableDropdownItem(
                        label = movieLoadModeLabel(mode),
                        selected = mode == movieLoadMode,
                        onClick = {
                            onMovieLoadModeChange(mode)
                            modeExpanded = false
                        }
                    )
                }
            }
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

/** Settings switches render slightly smaller than the default Material3 size. */
private const val SwitchScale = 0.85f

@Composable
private fun movieListStyleLabel(style: MovieListStyle) = when (style) {
    MovieListStyle.GRID -> stringResource(R.string.grid)
    MovieListStyle.LIST -> stringResource(R.string.list_view)
}

@Composable
private fun movieLoadModeLabel(mode: MovieLoadMode) = when (mode) {
    MovieLoadMode.WITH_MAGNET -> stringResource(R.string.movie_load_mode_with_magnet)
    MovieLoadMode.ALL -> stringResource(R.string.movie_load_mode_all)
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
                    style = MaterialTheme.typography.bodyLarge,
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
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
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
                                            style = MaterialTheme.typography.bodyLarge,
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

//region Local Video Card

@Composable
private fun LocalVideoCard(
    summary: LocalVideoSummary,
    isScanning: Boolean,
    showUncollectedLocal: Boolean,
    onToggleShowUncollectedLocal: (Boolean) -> Unit,
    onPickFolder: () -> Unit,
    onClearFolder: () -> Unit,
    onRescan: () -> Unit,
) {
    val context = LocalContext.current
    val linkedCountText = context.resources.getQuantityString(
        R.plurals.local_video_linked_count,
        summary.linkedCount,
        summary.linkedCount,
    )
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.public_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.local_video),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(16.dp))

            // 当前文件夹（点击选择/更换）— 整行可点，水波纹横向填满
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPickFolder)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.local_video_folder),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        summary.folderDisplayName
                            ?: stringResource(R.string.local_video_folder_not_set),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (summary.folderDisplayName != null) {
                    OutlinedButton(onClick = onClearFolder) {
                        Text(stringResource(R.string.local_video_clear_folder))
                    }
                }
            }

            // 上次扫描时间 + 关联数
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                summary.lastScannedAt?.let { ts ->
                    Text(
                        stringResource(R.string.local_video_last_scan, formatTime(ts)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    linkedCountText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 显示未收藏的本地视频（整行点击切换，Switch 缩放）
            SwitchRow(
                stringResource(R.string.local_video_show_uncollected),
                showUncollectedLocal,
                onToggleShowUncollectedLocal,
            )

            // 重新扫描
            Button(
                onClick = onRescan,
                enabled = !isScanning && summary.folderDisplayName != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                if (isScanning) {
                    Text(stringResource(R.string.local_video_scanning))
                } else {
                    Text(stringResource(R.string.local_video_rescan))
                }
            }
        }
    }
}

private fun formatTime(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
        .format(Date(epochMs))

//endregion
