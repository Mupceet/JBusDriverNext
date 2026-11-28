package me.jbusdriver.modern.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 盒式模糊的数学行为。期望值都是手算得出的精确值：
 * 半径 1 的窗口为 3 格，输出 = 窗口内求和后整除 3。
 */
class BoxBlurTransformationTest {

    /** 不透明的灰色像素。 */
    private fun gray(value: Int) = (0xFF shl 24) or (value shl 16) or (value shl 8) or value

    private fun redOf(pixel: Int) = (pixel ushr 16) and 0xFF

    @Test
    fun `uniform image is unchanged`() {
        val src = IntArray(6 * 4) { gray(120) }

        val out = boxBlurPixels(src, width = 6, height = 4, radius = 2, passes = 3)

        assertTrue(out.all { it == gray(120) })
    }

    @Test
    fun `edges keep their own value instead of darkening`() {
        // 左半亮、右半暗的阶跃图：窗口在边缘处夹取，左右两端的像素不应被拉向 0
        val width = 12
        val src = IntArray(width * 2) { index -> if (index % width < 6) gray(200) else gray(40) }

        val out = boxBlurPixels(src, width = width, height = 2, radius = 2, passes = 1)

        val row = (0 until width).map { redOf(out[it]) }
        assertEquals(listOf(200, 200, 200, 200, 168, 136, 104, 72, 40, 40, 40, 40), row)
    }

    @Test
    fun `a lone bright pixel spreads symmetrically and fades into a pyramid`() {
        val width = 9
        val src = IntArray(width * 5) { gray(0) }
        src[2 * width + 4] = gray(90) // 正中

        val out = boxBlurPixels(src, width = width, height = 5, radius = 1, passes = 2)

        val middleRow = (0 until width).map { redOf(out[2 * width + it]) }
        assertEquals(listOf(0, 0, 3, 6, 10, 6, 3, 0, 0), middleRow)
        assertEquals("must be symmetric", middleRow.reversed(), middleRow)
        // 沿竖直方向同样扩散：正上方拿到亮度且低于中心
        assertEquals(6, redOf(out[1 * width + 4]))
        assertEquals(3, redOf(out[0 * width + 4]))
        // 亮度够不到的角落保持全暗
        assertEquals(0, redOf(out[0]))
        assertEquals(0, redOf(out[out.lastIndex]))
    }

    @Test
    fun `more passes flatten the peak`() {
        val width = 9
        val src = IntArray(width * 5) { gray(0) }
        src[2 * width + 4] = gray(90)
        val center = 2 * width + 4

        val twoPasses = boxBlurPixels(src, width, 5, radius = 1, passes = 2)
        val threePasses = boxBlurPixels(src, width, 5, radius = 1, passes = 3)

        assertEquals(10, redOf(twoPasses[center]))
        assertTrue(
            "third pass must spread brightness further: ${redOf(threePasses[center])}",
            redOf(threePasses[center]) < redOf(twoPasses[center])
        )
    }

    @Test
    fun `rejects non-positive radius`() {
        val src = IntArray(4) { gray(100) }

        assertThrows(IllegalArgumentException::class.java) {
            boxBlurPixels(src, 2, 2, radius = 0, passes = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            boxBlurPixels(src, 2, 2, radius = -3, passes = 1)
        }
    }
}
