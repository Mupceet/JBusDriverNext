package me.jbusdriver.modern.ui.components

import android.graphics.Bitmap
import androidx.core.graphics.scale
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 把图片抹成柔和色块的盒式模糊（三次盒式逼近高斯），用于小图放大铺成的背景。
 *
 * 直接把几十像素的小图放大，每个源像素的细节会留成一格一格的拼布感；先在源图上
 * 真正模糊一次再放大，就只剩平缓的色块。半径按短边比例取，与图片尺寸无关。
 * 在解码线程上做，纯 Kotlin 像素运算，不依赖 RenderEffect（Android 11 及以下同样生效）。
 */
object BoxBlurTransformation : Transformation {

    override val cacheKey: String = javaClass.name

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val shortSide = minOf(input.width, input.height)
        // 过大的源图先缩小：模糊后的色块用不上分辨率，也避免逐像素运算量失控
        val source = if (shortSide > MAX_SOURCE_SIZE) {
            val scale = MAX_SOURCE_SIZE.toFloat() / shortSide
            input.scale(
                width = max(1, (input.width * scale).roundToInt()),
                height = max(1, (input.height * scale).roundToInt()),
                filter = true
            )
        } else {
            input
        }
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val radius = max(1, (minOf(width, height) * RADIUS_FRACTION).roundToInt())
        val blurred = boxBlurPixels(pixels, width, height, radius, PASSES)
        return Bitmap.createBitmap(blurred, width, height, Bitmap.Config.ARGB_8888)
    }

    private const val MAX_SOURCE_SIZE = 128
    private const val RADIUS_FRACTION = 0.11f
    private const val PASSES = 3
}

/**
 * 对不透明的 ARGB 像素做可分离的盒式模糊：横、竖各滑动一次为「一遍」，重复 [passes] 遍逼近高斯。
 * 边缘按夹取处理（窗口里越界的采样点取边缘像素），窗口大小恒定，不会出现边缘变暗。
 *
 * 纯函数，不依赖 Android 类，便于单元测试。
 */
internal fun boxBlurPixels(
    src: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    passes: Int
): IntArray {
    require(width > 0 && height > 0) { "size must be positive: ${width}x$height" }
    require(radius >= 1) { "radius must be positive: $radius" }
    var current = src
    repeat(passes) {
        current = blurAxis(
            blurAxis(current, width, height, radius, horizontal = true),
            width, height, radius, horizontal = false
        )
    }
    return current
}

/** 沿一个方向做一遍滑动窗口盒式平均。 */
private fun blurAxis(
    src: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean
): IntArray {
    val dst = IntArray(src.size)
    // 一「行」：横向时是图片的一行（沿 x），纵向时是图片的一列（沿 y）
    val lineLength = if (horizontal) width else height
    val lineCount = if (horizontal) height else width
    val step = if (horizontal) 1 else width      // 行内相邻像素的下标间隔
    val lineStride = if (horizontal) width else 1 // 行首之间的下标间隔
    val last = lineLength - 1
    val window = 2 * radius + 1
    for (line in 0 until lineCount) {
        val base = line * lineStride
        var sumA = 0
        var sumR = 0
        var sumG = 0
        var sumB = 0
        for (offset in -radius..radius) {
            val p = src[base + offset.coerceIn(0, last) * step]
            sumA += (p ushr 24) and 0xFF
            sumR += (p ushr 16) and 0xFF
            sumG += (p ushr 8) and 0xFF
            sumB += p and 0xFF
        }
        for (x in 0..last) {
            dst[base + x * step] =
                ((sumA / window) shl 24) or ((sumR / window) shl 16) or
                    ((sumG / window) shl 8) or (sumB / window)
            if (x < last) {
                // 窗口右移一格：移出最左的采样，移入最右的下一格（越界处夹取到边缘）
                val leaving = src[base + (x - radius).coerceIn(0, last) * step]
                val entering = src[base + (x + radius + 1).coerceIn(0, last) * step]
                sumA += ((entering ushr 24) and 0xFF) - ((leaving ushr 24) and 0xFF)
                sumR += ((entering ushr 16) and 0xFF) - ((leaving ushr 16) and 0xFF)
                sumG += ((entering ushr 8) and 0xFF) - ((leaving ushr 8) and 0xFF)
                sumB += (entering and 0xFF) - (leaving and 0xFF)
            }
        }
    }
    return dst
}
