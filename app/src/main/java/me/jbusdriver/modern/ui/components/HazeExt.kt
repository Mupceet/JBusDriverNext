package me.jbusdriver.modern.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 全局毛玻璃效果的唯一入口（Haze 2.1.0）。
 * 所有屏幕只通过 [hazeTopBar]/[hazeBottomBar]/[hazeSourceContent] 使用，
 * 不直接接触 haze DSL；将来替换实现（如自研 graphicsLayer 捕获）只改本文件。
 *
 * 布局要求：被模糊内容（列表）与效果栏须同屏叠放，内容从栏下穿过
 * （内容不吃 Scaffold 顶/底 padding，顶/底高改由滚动容器 contentPadding 提供）。
 * 栏自身容器色需设为 Color.Transparent，否则会遮挡模糊层。
 * API 31+ 为真模糊，Android 11 及以下由 Haze 自动降级为半透明 scrim。
 */

/** 创建屏幕级 Haze 状态（每个毛玻璃屏持有一次，栏与内容共享）。 */
@Composable
fun rememberAppHazeState(): HazeState = rememberHazeState()

/** 标记被模糊的内容（列表/网格等滚动容器）。 */
fun Modifier.hazeSourceContent(state: HazeState): Modifier = hazeSource(state)

/** 顶部标题栏效果（thin 样式，轻透）。[dark] 为深色底玻璃（图片查看器等黑底场景）。 */
@Composable
fun Modifier.hazeTopBar(state: HazeState, dark: Boolean = false): Modifier =
    hazeBlur(
        input = HazeInput.Sources(state),
        style = if (dark) HazeMaterials.thin(containerColor = Color.Black) else HazeMaterials.thin()
    )

/** 底部导航栏效果（regular 样式，略厚以保证文字可读）。 */
@Composable
fun Modifier.hazeBottomBar(state: HazeState): Modifier =
    hazeBlur(input = HazeInput.Sources(state), style = HazeMaterials.regular())
