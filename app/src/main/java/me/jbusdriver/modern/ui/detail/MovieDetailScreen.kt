package me.jbusdriver.modern.ui.detail

import me.jbusdriver.R
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import me.jbusdriver.modern.core.copy
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.GenreUiModel
import me.jbusdriver.modern.ui.HeaderUiModel
import me.jbusdriver.modern.ui.ImageSampleUiModel
import me.jbusdriver.modern.ui.MagnetUiModel
import me.jbusdriver.modern.ui.MovieDetailUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.components.ActressAvatar
import me.jbusdriver.modern.ui.components.ErrorView
import androidx.core.net.toUri

/**
 * 影片详情页面的顶层可组合函数。
 *
 * 职责：展示影片的完整详情信息，包括封面、基本信息、截图、演员、类别、推荐影片以及磁力链接，
 * 并提供收藏/取消收藏功能。支持下拉刷新和错误重试。
 *
 * 使用场景：作为 Navigation 图中的一个目标页面，在用户点击影片列表或搜索结果中的影片时导航至此。
 *
 * @param movieUrl 影片详情页的 URL，用于从远程加载详情数据
 * @param onMovieClick 点击推荐影片时的回调
 * @param onActressClick 点击演员头像时的回调
 * @param onGenreClick 点击类别标签时的回调
 * @param onImageClick 点击图片时的回调，参数为图片 URL 列表和当前点击的索引
 * @param onBack 返回上一页的回调
 * @param viewModel 影片详情页的 ViewModel，由 Hilt 自动注入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    movieUrl: String,
    onMovieClick: (MovieUiModel) -> Unit = {},
    onActressClick: (ActressUiModel) -> Unit = {},
    onGenreClick: (GenreUiModel) -> Unit = {},
    onImageClick: (List<String>, Int) -> Unit = { _, _ -> },
    onHeaderClick: (HeaderUiModel) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: MovieDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMagnetSheet by remember { mutableStateOf(false) }

    LaunchedEffect(movieUrl) {
        viewModel.loadDetail(movieUrl)
    }

    val detail = uiState.movieDetail
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        detail?.title ?: "載入中...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = "返回")
                    }
                },
                actions = {
                    if (detail != null) {
                        IconButton(onClick = {
                            viewModel.toggleCollect()
                            val msg = if (!uiState.isCollected) "收藏成功" else "已取消收藏"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                painter = painterResource(if (uiState.isCollected) R.drawable.favorite_fill_24px else R.drawable.favorite_24px),
                                contentDescription = if (uiState.isCollected) "取消收藏" else "收藏",
                                tint = if (uiState.isCollected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.error != null -> {
                    ErrorView(
                        message = "載入失敗，請重試",
                        onRetry = { viewModel.loadDetail(movieUrl) }
                    )
                }

                detail != null -> {
                    DetailContent(
                        detail = detail,
                        padding = PaddingValues(),
                        onMovieClick = onMovieClick,
                        onActressClick = onActressClick,
                        onGenreClick = onGenreClick,
                        onHeaderClick = onHeaderClick,
                        onImageClick = onImageClick,
                        onMagnetClick = {
                            showMagnetSheet = true
                        },
                        isLoadingMagnets = uiState.isLoadingMagnets,
                        hasMagnets = uiState.magnets.isNotEmpty()
                    )
                }
            }
        }
    }

    if (showMagnetSheet) {
        MagnetBottomSheet(
            uiState = uiState,
            onDismiss = { showMagnetSheet = false }
        )
    }
}

/**
 * 影片详情页的内容区域。
 *
 * 职责：以 LazyColumn 形式垂直排列影片的封面、基本信息、截图预览、演员列表、类别标签、推荐影片和磁力链接按钮。
 *
 * 使用场景：由 [MovieDetailScreen] 在数据加载成功后调用，作为详情页面的主体内容。
 *
 * @param detail 影片详情数据模型
 * @param padding 外部传入的内边距，通常来自 Scaffold
 * @param onMovieClick 点击推荐影片时的回调
 * @param onActressClick 点击演员头像时的回调
 * @param onGenreClick 点击类别标签时的回调
 * @param onImageClick 点击图片（封面或截图）时的回调
 * @param onMagnetClick 点击"查看磁力链接"按钮时的回调
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailContent(
    detail: MovieDetailUiModel,
    padding: PaddingValues,
    onMovieClick: (MovieUiModel) -> Unit,
    onActressClick: (ActressUiModel) -> Unit,
    onGenreClick: (GenreUiModel) -> Unit,
    onHeaderClick: (HeaderUiModel) -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    onMagnetClick: () -> Unit,
    isLoadingMagnets: Boolean = false,
    hasMagnets: Boolean = false
) {
    val listState = rememberLazyListState()
    val coverHeight = remember { mutableStateOf(0) }
    val context = LocalContext.current
    var selectedHeader by remember { mutableStateOf<HeaderUiModel?>(null) }
    var coverAspectRatio by remember {
        mutableFloatStateOf(
            if (detail.coverWidth > 0 && detail.coverHeight > 0)
                detail.coverWidth.toFloat() / detail.coverHeight.toFloat()
            else 3f / 2f
        )
    }
    detail.headers.firstOrNull()?.value ?: ""
    val allImages = remember(detail.cover, detail.imageSamples) {
        listOf(detail.cover) + detail.imageSamples.map { it.image }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        // Cover image
        item(key = "cover") {
            AsyncImage(
                model = detail.cover,
                contentDescription = detail.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspectRatio)
                    .clickable { onImageClick(allImages, 0) }
                    .onSizeChanged { size -> coverHeight.value = size.height },
                onSuccess = { result ->
                    val drawable = result.result.drawable
                    val width = drawable.intrinsicWidth
                    val height = drawable.intrinsicHeight
                    if (width > 0 && height > 0) {
                        coverAspectRatio = width.toFloat() / height.toFloat()
                    }
                }
            )
        }

        // Headers info
        item(key = "headers") {
            if (detail.headers.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        detail.headers.forEach { header ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = header.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(80.dp)
                                )
                                if (header.name == "識別碼") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f).combinedClickable(
                                            onClick = {
                                                if (header.link.isNotBlank()) onHeaderClick(header)
                                            },
                                            onLongClick = { selectedHeader = header }
                                        )
                                    ) {
                                        Text(
                                            text = header.value,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (header.link.isNotBlank())
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                        val context = LocalContext.current
                                        Icon(
                                            painter = painterResource(R.drawable.content_copy_24px),
                                            contentDescription = "複製",
                                            modifier = Modifier
                                                .size(22.dp)
                                                .padding(start = 4.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable {
                                                    context.copy(header.value)
                                                    Toast.makeText(context, "已複製", Toast.LENGTH_SHORT).show()
                                                },
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Text(
                                        text = header.value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (header.link.isNotBlank())
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f).combinedClickable(
                                            onClick = {
                                                if (header.link.isNotBlank()) onHeaderClick(header)
                                            },
                                            onLongClick = { selectedHeader = header }
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Image Samples
        if (detail.imageSamples.isNotEmpty()) {
            item(key = "samples") {
                ImageSampleSection(
                    samples = detail.imageSamples,
                    allImages = allImages,
                    onImageClick = onImageClick
                )
            }
        }

        // Actresses
        if (detail.actresses.isNotEmpty()) {
            item(key = "actresses") {
                ActressSection(actresses = detail.actresses, onActressClick = onActressClick)
            }
        }

        // Genres
        if (detail.genres.isNotEmpty()) {
            item(key = "genres") {
                GenreSection(genres = detail.genres, onGenreClick = onGenreClick)
            }
        }

        // Related Movies
        if (detail.relatedMovies.isNotEmpty()) {
            item(key = "related") {
                RelatedMovieSection(movies = detail.relatedMovies, onMovieClick = onMovieClick)
            }
        }

        // Magnet button or bottom spacing
        if (isLoadingMagnets || hasMagnets) {
            item(key = "magnet") {
                Button(
                    onClick = onMagnetClick,
                    enabled = hasMagnets,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    if (isLoadingMagnets) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("查看磁力連結")
                }
            }
        } else {
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    selectedHeader?.let { header ->
        AlertDialog(
            onDismissRequest = { selectedHeader = null },
            title = { Text(header.name) },
            text = { SelectionContainer { Text(header.value) } },
            confirmButton = {
                TextButton(onClick = {
                    context.copy(header.value)
                    Toast.makeText(context, "已複製", Toast.LENGTH_SHORT).show()
                    selectedHeader = null
                }) { Text("複製") }
            },
            dismissButton = {
                TextButton(onClick = { selectedHeader = null }) { Text("關閉") }
            }
        )
    }
}

/**
 * 影片类别标签区域。
 *
 * 职责：以 FlowRow 形式展示影片所属的所有类别标签，每个标签可点击跳转。
 *
 * 使用场景：作为 [DetailContent] 中的一个 section，当影片有关联类别时显示。
 *
 * @param genres 类别列表
 * @param onGenreClick 点击单个类别标签时的回调
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreSection(genres: List<GenreUiModel>, onGenreClick: (GenreUiModel) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("類別", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            genres.forEach { genre ->
                AssistChip(
                    onClick = { onGenreClick(genre) },
                    label = { Text(genre.name, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

/**
 * 影片截图预览区域。
 *
 * 职责：以水平滚动列表展示影片的截图缩略图，点击任意截图可进入全屏图片查看器。
 *
 * 使用场景：作为 [DetailContent] 中的一个 section，当影片有关联截图时显示。
 *
 * @param samples 截图列表，包含缩略图和大图的 URL
 * @param onImageClick 点击截图时的回调，参数为所有大图 URL 列表和当前点击的索引
 */
@Composable
private fun ImageSampleSection(
    samples: List<ImageSampleUiModel>,
    allImages: List<String>,
    onImageClick: (List<String>, Int) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            "截圖",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(samples.size) { index ->
                val sample = samples[index]
                AsyncImage(
                    model = sample.thumb,
                    contentDescription = sample.title,
                    modifier = Modifier
                        .width(100.dp)
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            onImageClick(allImages, index + 1)
                        },
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

/**
 * 演员头像列表区域。
 *
 * 职责：以水平滚动列表展示影片关联的演员头像和名称，点击可跳转到演员详情。
 *
 * 使用场景：作为 [DetailContent] 中的一个 section，当影片有关联演员时显示。
 *
 * @param actresses 演员列表
 * @param onActressClick 点击演员时的回调
 */
@Composable
private fun ActressSection(
    actresses: List<ActressUiModel>,
    onActressClick: (ActressUiModel) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            "演員",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(actresses.size) { index ->
                val actress = actresses[index]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onActressClick(actress) }
                ) {
                    ActressAvatar(
                        avatarUrl = actress.avatar,
                        contentDescription = actress.name,
                        size = 64.dp,
                        onClick = { onActressClick(actress) }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = actress.name,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(64.dp)
                    )
                }
            }
        }
    }
}

/**
 * 推荐影片区域。
 *
 * 职责：以水平滚动列表展示与当前影片相关的推荐影片，包含封面和标题。
 *
 * 使用场景：作为 [DetailContent] 中的一个 section，当影片有关联推荐影片时显示。
 *
 * @param movies 推荐影片列表
 * @param onMovieClick 点击推荐影片时的回调
 */
@Composable
private fun RelatedMovieSection(movies: List<MovieUiModel>, onMovieClick: (MovieUiModel) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            "推薦",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(movies.size) { index ->
                val movie = movies[index]
                Column(
                    modifier = Modifier
                        .width(100.dp)
                        .clickable { onMovieClick(movie) }
                ) {
                    AsyncImage(
                        model = movie.imageUrl,
                        contentDescription = movie.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = movie.code + " " + movie.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 磁力链接底部弹窗。
 *
 * 职责：以 ModalBottomSheet 展示影片的磁力链接列表，支持分页加载更多。
 * 点击磁力链接项会将链接复制到剪贴板。
 *
 * 使用场景：在 [MovieDetailScreen] 中点击"查看磁力链接"按钮后弹出。
 *
 * @param uiState 影片详情页的 UI 状态，包含磁力链接数据和加载状态
 * @param onDismiss 关闭弹窗的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MagnetBottomSheet(
    uiState: MovieDetailUiState,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.615f)
        ) {
            Text(
                "磁力連結",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    uiState.isLoadingMagnets && uiState.magnets.isEmpty() -> {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    uiState.magnetsError != null && uiState.magnets.isEmpty() -> {
                        item {
                            Text(uiState.magnetsError, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    uiState.magnets.isEmpty() -> {
                        item {
                            Text(
                                "暫無磁力連結",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    else -> {
                        items(uiState.magnets, key = { it.link }) { magnet ->
                            MagnetItem(magnet = magnet, context = context)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单条磁力链接的可组合项。
 *
 * 职责：展示磁力链接的名称、大小和日期信息，提供「复制」和「打开」两个独立操作。
 *
 * 使用场景：作为 [MagnetBottomSheet] 中磁力链接列表的单个条目。
 *
 * @param magnet 磁力链接数据模型
 * @param context Android Context，用于获取 ClipboardManager 和启动 Intent
 */
@Composable
private fun MagnetItem(magnet: MagnetUiModel, context: Context) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                if (magnet.link.isNotBlank()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, magnet.link.toUri())
                        context.startActivity(Intent.createChooser(intent, "選擇下載方式"))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "未找到可處理的應用", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = magnet.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (magnet.size.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                magnet.size,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    if (magnet.date.isNotBlank()) {
                        Text(
                            magnet.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            IconButton(
                onClick = {
                    if (magnet.link.isNotBlank()) {
                        context.copy(magnet.link)
                        Toast.makeText(context, "已複製磁力連結", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(
                    painterResource(
                        R.drawable.content_copy_24px),
                    contentDescription = "複製連結",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
