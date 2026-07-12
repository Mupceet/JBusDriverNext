package me.jbusdriver.modern.ui.image

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import me.jbusdriver.modern.ui.theme.LocalIsDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.jbusdriver.modern.ui.components.AppAsyncImage
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.ui.components.dimColorFilter
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewScreen(
    images: List<String>,
    startIndex: Int = 0,
    onBack: () -> Unit = {},
    viewModel: ImageActionsViewModel = hiltViewModel()
) {
    val view = LocalView.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val isDarkTheme = LocalIsDarkTheme.current
    val scope = rememberCoroutineScope()
    var isFullscreen by remember { mutableStateOf(false) }

    val window = (view.context as Activity).window
    val insetsController = WindowCompat.getInsetsController(window, view)

    DisposableEffect(isDarkTheme) {
        insetsController.isAppearanceLightStatusBars = false
        onDispose {
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
            // Always restore system bars when leaving the viewer.
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
        pageCount = { images.size }
    )

    LaunchedEffect(viewModel, context, resources) {
        viewModel.messages.collect { message ->
            val text = message.formatArg?.let {
                resources.getString(message.messageRes, it)
            } ?: resources.getString(message.messageRes)
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val zoomableImageState = rememberZoomableImageState(
                rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 3f))
            )
            Box(modifier = Modifier.fillMaxSize()) {
                ZoomableAsyncImage(
                    model = images[page],
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = dimColorFilter(),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    state = zoomableImageState,
                    // Telephoto consumes all gestures (incl. double-tap-zoom), so the toggle
                    // must ride on its own single-tap callback; a parent clickable won't fire.
                    onClick = { isFullscreen = !isFullscreen },
                )
                if (!zoomableImageState.isImageDisplayed) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        // Top bar (single-tap the image to toggle)
        AnimatedVisibility(
            visible = !isFullscreen,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f))
                    .statusBarsPadding()
            ) {
                TopAppBar(
                    title = {
                        Text(
                            "${pagerState.currentPage + 1} / ${images.size}",
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(R.drawable.arrow_back_24px),
                                contentDescription = stringResource(R.string.back),
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.saveImage(images[pagerState.currentPage])
                        }) {
                            Icon(
                                painterResource(R.drawable.download_24px),
                                contentDescription = stringResource(R.string.save),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = {
                            viewModel.shareImage(images[pagerState.currentPage])
                        }) {
                            Icon(
                                painterResource(R.drawable.share_24px),
                                contentDescription = stringResource(R.string.share),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = Color.White,
                        titleContentColor = Color.White
                    )
                )
            }
        }

        // Bottom thumbnail strip
        if (images.size > 1) {
            AnimatedVisibility(
                visible = !isFullscreen,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ThumbnailStrip(
                    images = images,
                    currentPage = pagerState.currentPage,
                    onPageClick = { page ->
                        scope.launch { pagerState.scrollToPage(page) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(top = 16.dp, bottom = 32.dp)
                        .navigationBarsPadding()
                )
            }
        }
    }
}

@Composable
private fun ThumbnailStrip(
    images: List<String>,
    currentPage: Int,
    onPageClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentPage) {
        listState.animateScrollToItem(currentPage)
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(images) { index, imageUrl ->
            val isSelected = index == currentPage
            AppAsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 60.dp, height = 60.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.DarkGray)
                    .then(
                        if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp))
                        else Modifier
                    )
                    .clickable { onPageClick(index) }
            )
        }
    }
}
