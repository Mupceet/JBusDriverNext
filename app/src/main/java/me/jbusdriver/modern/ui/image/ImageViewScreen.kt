package me.jbusdriver.modern.ui.image

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewScreen(
    images: List<String>,
    startIndex: Int = 0,
    onBack: () -> Unit = {}
) {
    val view = LocalView.current
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    DisposableEffect(isDarkTheme) {
        val window = (view.context as Activity).window
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.isAppearanceLightStatusBars = false
        onDispose {
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
        }
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
        pageCount = { images.size }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = images[page],
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .zoomable(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 3f))),
                    onState = { imageState = it }
                )
                if (imageState is AsyncImagePainter.State.Loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        // Top bar
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
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
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            saveToGallery(context, images[pagerState.currentPage])
                        }
                    }) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = "保存",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            shareImage(context, images[pagerState.currentPage])
                        }
                    }) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "分享",
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

        // Bottom thumbnail strip
        if (images.size > 1) {
            ThumbnailStrip(
                images = images,
                currentPage = pagerState.currentPage,
                onPageClick = { page ->
                    scope.launch { pagerState.scrollToPage(page) }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(vertical = 8.dp)
                    .navigationBarsPadding()
            )
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
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 100.dp, height = 75.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .then(
                        if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp))
                        else Modifier
                    )
                    .clickable { onPageClick(index) }
            )
        }
    }
}

private suspend fun saveToGallery(context: Context, imageUrl: String) {
    try {
        val bytes = withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder().url(imageUrl).build()
            client.newCall(request).execute().body?.bytes()
        } ?: throw IllegalStateException("Empty response")

        withContext(Dispatchers.IO) {
            val filename = "JBus_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/JBus")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
            ) ?: throw IllegalStateException("Failed to create media store entry")

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(bytes)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "已保存到相冊", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "保存失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

private suspend fun shareImage(context: Context, imageUrl: String) {
    try {
        val file = withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder().url(imageUrl).build()
            val response = client.newCall(request).execute()
            val bytes = response.body?.bytes() ?: throw IllegalStateException("Empty response")

            val shareDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val file = File(shareDir, "share_${System.currentTimeMillis()}.jpg")
            file.writeBytes(bytes)
            file
        }

        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享圖片"))
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "分享失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
