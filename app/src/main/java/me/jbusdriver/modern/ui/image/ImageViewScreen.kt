package me.jbusdriver.modern.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import kotlin.math.abs
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

@Composable
private fun ZoomableImage(imageUrl: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }

    fun maxX() = (layoutSize.width * (scale - 1f)) / 2f
    fun maxY() = (layoutSize.height * (scale - 1f)) / 2f

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
                    var isPinching = false
                    var isDragging = false

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pointers = event.changes.filter { it.pressed }

                        when {
                            pointers.size >= 2 -> {
                                // --- Pinch to zoom ---
                                isPinching = true
                                isDragging = false
                                pointers.forEach { it.consume() }

                                val p0 = pointers[0].position
                                val p1 = pointers[1].position
                                val dist = hypot(p1.x - p0.x, p1.y - p0.y)
                                val centroid = Offset((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)

                                if (prevPinchDist > 1f) {
                                    val zoom = dist / prevPinchDist
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    val ratio = newScale / scale

                                    // Keep centroid fixed: offset from center adjusted by scale ratio
                                    val cx = centroid.x - layoutSize.width / 2f
                                    val cy = centroid.y - layoutSize.height / 2f
                                    offsetX = cx * (1f - ratio) + offsetX * ratio
                                    offsetY = cy * (1f - ratio) + offsetY * ratio

                                    scale = newScale
                                    offsetX = offsetX.coerceIn(-maxX(), maxX())
                                    offsetY = offsetY.coerceIn(-maxY(), maxY())

                                    if (scale <= 1.001f) {
                                        scale = 1f; offsetX = 0f; offsetY = 0f
                                    }
                                }
                                prevPinchDist = dist
                            }

                            pointers.size == 1 && !isPinching -> {
                                val change = pointers[0]

                                if (scale > 1.01f) {
                                    // --- Zoomed in: pan image, hand off to pager at edges ---
                                    if (!isDragging) {
                                        isDragging = true
                                        prevDragPos = change.position
                                    } else {
                                        val dx = change.position.x - prevDragPos.x
                                        val dy = change.position.y - prevDragPos.y
                                        prevDragPos = change.position

                                        // Always pan vertically when zoomed in
                                        offsetY = (offsetY + dy).coerceIn(-maxY(), maxY())

                                        // Pan horizontally; check if at edge BEFORE this drag
                                        val mx = maxX()
                                        val atRightEdge = offsetX >= mx - 0.5f && dx > 0
                                        val atLeftEdge = offsetX <= -mx + 0.5f && dx < 0

                                        if (!atRightEdge && !atLeftEdge) {
                                            offsetX = (offsetX + dx).coerceIn(-mx, mx)
                                            change.consume()
                                        } else {
                                            // At edge: don't consume horizontal → pager handles swipe
                                            offsetX = (offsetX + dx).coerceIn(-mx, mx)
                                        }
                                    }
                                }
                                // scale == 1: don't consume, pager handles everything
                            }

                            else -> {
                                isPinching = false
                                isDragging = false
                                prevPinchDist = 0f
                                if (scale <= 1.01f) { scale = 1f; offsetX = 0f; offsetY = 0f }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // Gesture ended: snap to 1x if barely zoomed
                    if (scale <= 1.01f) { scale = 1f; offsetX = 0f; offsetY = 0f }
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
