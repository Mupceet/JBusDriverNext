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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
 * Zoom behavior matching MVP's PhotoView:
 * - Pinch zooms from display center (offset stays 0,0 during zoom)
 * - When zoomed in (scale > 1): single-finger drag pans image, all events consumed
 * - When scale == 1: all events pass to pager for page swiping
 * - Zoom out below 1f snaps back to 1f
 */
@Composable
private fun ZoomableImage(imageUrl: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }

    fun calcMaxX() = (layoutSize.width * (scale - 1f)) / 2f
    fun calcMaxY() = (layoutSize.height * (scale - 1f)) / 2f

    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { layoutSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)

                    var prevPinchDist = 0f
                    var prevDragPos = Offset.Zero
                    var wasZoomed = scale > 1.01f

                    do {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.filter { it.pressed }

                        when {
                            pointers.size >= 2 -> {
                                // Pinch to zoom — consume all, zoom from center
                                pointers.forEach { it.consume() }
                                prevPinchDist // keep drag state reset

                                val p0 = pointers[0].position
                                val p1 = pointers[1].position
                                val dist = hypot(p1.x - p0.x, p1.y - p0.y)

                                if (prevPinchDist > 1f) {
                                    val newScale = (scale * (dist / prevPinchDist)).coerceIn(1f, 5f)
                                    // Keep center fixed: adjust offset so center stays at center
                                    val ratio = newScale / scale
                                    offsetX *= ratio
                                    offsetY *= ratio
                                    scale = newScale
                                    // Clamp after scaling
                                    offsetX = offsetX.coerceIn(-calcMaxX(), calcMaxX())
                                    offsetY = offsetY.coerceIn(-calcMaxY(), calcMaxY())
                                }
                                prevPinchDist = dist
                                wasZoomed = true
                            }

                            pointers.size == 1 -> {
                                val change = pointers[0]
                                if (scale > 1.01f) {
                                    // Zoomed in: pan image, consume ALL events
                                    if (!wasZoomed && prevDragPos == Offset.Zero) {
                                        prevDragPos = change.position
                                        wasZoomed = true
                                    }
                                    val dx = change.position.x - prevDragPos.x
                                    val dy = change.position.y - prevDragPos.y
                                    prevDragPos = change.position
                                    offsetX = (offsetX + dx).coerceIn(-calcMaxX(), calcMaxX())
                                    offsetY = (offsetY + dy).coerceIn(-calcMaxY(), calcMaxY())
                                    change.consume()
                                } else {
                                    // Not zoomed: pass to pager, reset state
                                    wasZoomed = false
                                    prevDragPos = Offset.Zero
                                }
                            }
                            // else: no active pointers, loop will end
                        }
                    } while (event.changes.any { it.pressed })

                    // Snap to 1x if close
                    if (scale <= 1.01f) {
                        scale = 1f; offsetX = 0f; offsetY = 0f
                    }
                    prevPinchDist = 0f
                    prevDragPos = Offset.Zero
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
