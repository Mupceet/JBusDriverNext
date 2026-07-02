package me.jbusdriver.modern.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import me.jbusdriver.modern.ui.theme.LocalIsDarkTheme

/**
 * 夜间模式下统一压低图片亮度的颜色矩阵。
 *
 * 将 RGB 三通道各缩放至 [DIM_SCALE]（alpha 保持不变），相当于把照片本身压暗，
 * 而不是在图片上盖一层半透明黑纱，因此不会影响占位背景色，观感更自然。
 *
 * 通过 [ColorFilter.colorMatrix] 在绘制阶段应用，完全不触碰 Coil 的磁盘缓存
 * （日/夜间共用同一份缓存），切换主题即时生效、无额外内存开销。
 */
private const val DIM_SCALE = 0.7f
private val DarkDimMatrix = ColorMatrix(
    floatArrayOf(
        DIM_SCALE, 0f, 0f, 0f, 0f,
        0f, DIM_SCALE, 0f, 0f, 0f,
        0f, 0f, DIM_SCALE, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
)

/**
 * 夜间模式压暗滤镜的统一入口。
 *
 * - [AppAsyncImage] 内部使用它；
 * - 个别无法走包装器的调用点（例如 [coil.compose.SubcomposeAsyncImage] 带 loading 插槽的场景）
 *   也可直接 `colorFilter = dimColorFilter()`，保证整站压暗逻辑只有这一处。
 *
 * 注意：是否压暗以**应用当前主题**为准（[LocalIsDarkTheme]），而不是系统夜间模式——
 * 用户可以在应用内显式选择日间/夜间，二者不一定一致。
 *
 * @param enabled 是否启用压暗，默认跟随应用夜间主题（[LocalIsDarkTheme]）。
 */
@Composable
fun dimColorFilter(enabled: Boolean = LocalIsDarkTheme.current): ColorFilter? =
    remember(enabled) { if (enabled) ColorFilter.colorMatrix(DarkDimMatrix) else null }

/**
 * 全站统一的图片组件。
 *
 * 相比直接使用 [AsyncImage]，唯一区别是**夜间主题下**自动压低亮度（通过 [dimColorFilter]），
 * 避免大量封面/截图把深色界面"打亮"；日间主题不做任何调整。其余行为与 [AsyncImage] 完全一致。
 *
 * 全屏图片查看器（`ImageViewScreen`）需要保留完整细节，因此不使用本组件，
 * 直接使用 [AsyncImage]。
 *
 * @param model Coil 加载模型（URL、URI、File 等）。
 * @param contentDescription 无障碍描述。
 * @param modifier 应用于图片的 Modifier。
 * @param contentScale 内容缩放方式，默认 [ContentScale.Crop]。
 * @param dim 是否参与夜间压暗，默认 true；需要原样展示细节的场景传 false（日间主题下始终无影响）。
 * @param onState 图片状态回调，供需要读取 drawable（例如动态宽高比）的场景使用。
 */
@Composable
fun AppAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    dim: Boolean = true,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
) {
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        colorFilter = if (dim) dimColorFilter() else null,
        onState = onState,
    )
}
