package me.jbusdriver.modern.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewScreen(
    images: List<String>,
    startIndex: Int = 0,
    onBack: () -> Unit = {}
) {
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
        pageCount = { images.size }
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomableImage(imageUrl = images[page])
        }

        TopAppBar(
            title = {
                Text(
                    "${pagerState.currentPage + 1} / ${images.size}",
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * Custom pinch-to-zoom that only intercepts 2-finger gestures.
 * Single-finger events pass through to HorizontalPager for swiping.
 */
@Composable
private fun ZoomableImage(imageUrl: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var layoutSize by remember { mutableFloatStateOf(1f) }

    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { layoutSize = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Wait for first finger but do NOT consume — let pager have it
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    var previousDistance = 0f
                    var previousCentroid = Offset.Zero
                    var isPinching = false

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val activeChanges = event.changes.filter { it.pressed }

                        if (activeChanges.size >= 2) {
                            // Pinch gesture detected — consume all events to take over
                            isPinching = true
                            activeChanges.forEach { it.consume() }

                            val p0 = activeChanges[0].position
                            val p1 = activeChanges[1].position
                            val currentDistance = hypot(p1.x - p0.x, p1.y - p0.y)
                            val centroid = Offset(
                                (p0.x + p1.x) / 2f,
                                (p0.y + p1.y) / 2f
                            )

                            if (previousDistance > 0f) {
                                val zoom = currentDistance / previousDistance
                                val pan = centroid - previousCentroid

                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                scale = newScale
                                if (newScale > 1.01f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                    val maxX = (layoutSize * (scale - 1f)) / 2f
                                    val maxY = (layoutSize * (scale - 1f)) * 1.33f / 2f
                                    offsetX = offsetX.coerceIn(-maxX, maxX)
                                    offsetY = offsetY.coerceIn(-maxY, maxY)
                                } else {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                            previousDistance = currentDistance
                            previousCentroid = centroid
                        } else {
                            if (isPinching) {
                                // Pinch ended, reset tracking
                                isPinching = false
                                previousDistance = 0f
                                if (scale <= 1.01f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                            // Single finger or no fingers — do NOT consume, let pager handle
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            }
    )
}
