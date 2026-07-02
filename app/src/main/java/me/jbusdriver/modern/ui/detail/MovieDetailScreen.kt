package me.jbusdriver.modern.ui.detail

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImagePainter
import me.jbusdriver.R
import me.jbusdriver.modern.ui.components.AppAsyncImage
import me.jbusdriver.modern.core.copy
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.GenreUiModel
import me.jbusdriver.modern.ui.HeaderUiModel
import me.jbusdriver.modern.ui.MovieDetailUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.components.CollectButton
import me.jbusdriver.modern.ui.components.ErrorView
import me.jbusdriver.modern.ui.components.ShareButton
import kotlin.math.abs

/** 封面占位的默认宽高比（横向）。绝大多数封面都聚集在这个比例附近。 */
private const val CoverDefaultAspectRatio = 1.5f

/**
 * 真实宽高比相对占位默认值的相对偏差超过此阈值时（例如横向占位遇到纵向封面）才采用真实比例，
 * 否则保持占位比例，避免图片加载完成后界面跳动。
 */
private const val CoverRatioAdoptionTolerance = 0.25f

private fun shouldAdoptCoverRatio(real: Float): Boolean {
    val default = CoverDefaultAspectRatio
    return abs(real - default) / default > CoverRatioAdoptionTolerance
}

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
    censorType: String? = null,
    onMovieClick: (MovieUiModel, String?) -> Unit = { _, _ -> },
    onActressClick: (ActressUiModel, String?) -> Unit = { _, _ -> },
    onGenreClick: (GenreUiModel, String?) -> Unit = { _, _ -> },
    onImageClick: (List<String>, Int) -> Unit = { _, _ -> },
    onHeaderClick: (HeaderUiModel, String?) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    viewModel: MovieDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMagnetSheet by remember { mutableStateOf(false) }

    LaunchedEffect(movieUrl, censorType) {
        viewModel.loadDetail(movieUrl, censorType)
    }

    val detail = uiState.movieDetail
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        detail?.title ?: stringResource(R.string.loading),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (detail != null) {
                        val code = detail.headers.firstOrNull()?.value ?: ""
                        val shareText = buildString {
                            if (code.isNotBlank()) append(code).append("\n")
                            append(detail.title)
                            append("\n")
                            append(movieUrl)
                        }
                        ShareButton(text = shareText)
                        CollectButton(
                            isCollected = uiState.isCollected,
                            onToggle = { viewModel.toggleCollect() }
                        )
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
                        message = stringResource(uiState.error ?: R.string.load_failed),
                        onRetry = { viewModel.loadDetail(movieUrl, censorType) }
                    )
                }

                detail != null -> {
                    DetailContent(
                        detail = detail,
                        padding = PaddingValues(),
                        onMovieClick = { movie -> onMovieClick(movie, censorType) },
                        onActressClick = { actress -> onActressClick(actress, censorType) },
                        onGenreClick = { genre -> onGenreClick(genre, censorType) },
                        onHeaderClick = { header -> onHeaderClick(header, censorType) },
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
 * @param onMagnetClick 点击“查看磁力連結”按钮时的回调
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
    val coverHeight = remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.copied)
    val copiedTitleMessage = stringResource(R.string.copied_title)
    var selectedHeader by remember { mutableStateOf<HeaderUiModel?>(null) }
    var coverAspectRatio by remember {
        mutableFloatStateOf(CoverDefaultAspectRatio)
    }
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
            AppAsyncImage(
                model = detail.cover,
                contentDescription = detail.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspectRatio)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onImageClick(allImages, 0) }
                    .onSizeChanged { size -> coverHeight.intValue = size.height },
                onState = { state ->
                    if (state is AsyncImagePainter.State.Success) {
                        val drawable = state.result.drawable
                        val width = drawable.intrinsicWidth
                        val height = drawable.intrinsicHeight
                        if (width > 0 && height > 0) {
                            val real = width.toFloat() / height.toFloat()
                            // 仅在真实比例与占位比例差异过大（如纵向封面）时才切换，避免加载后跳动
                            if (shouldAdoptCoverRatio(real)) {
                                coverAspectRatio = real
                            }
                        }
                    }
                }
            )
        }

        // Title
        item(key = "title") {
            Text(
                text = detail.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onLongClick = {

                            context.copy(detail.title)
                            Toast.makeText(context, copiedTitleMessage, Toast.LENGTH_SHORT).show()
                        },
                        onClick = {}
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Headers info
        item(key = "headers") {
            if (detail.headers.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        detail.headers.forEach { header ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (header.link.isNotBlank()) onHeaderClick(header)
                                        },
                                        onLongClick = { selectedHeader = header }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.Top
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
                                        modifier = Modifier.weight(1f)
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
                                            contentDescription = stringResource(R.string.copy),
                                            modifier = Modifier
                                                .size(22.dp)
                                                .padding(start = 4.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable {
                                                    context.copy(header.value)
                                                    Toast.makeText(
                                                        context,
                                                        copiedMessage,
                                                        Toast.LENGTH_SHORT
                                                    ).show()
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
                                        modifier = Modifier.weight(1f)
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
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                    Text(stringResource(R.string.view_magnet))
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
                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    selectedHeader = null
                }) { Text(stringResource(R.string.copy)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedHeader = null
                }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}
