package me.jbusdriver.modern.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.database.Cursor
import android.net.Uri
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.Nullable
import androidx.collection.ArrayMap
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// region 文件大小常量与格式化

/** 文件大小单位常量 */
const val KB = 1024.0
const val MB = KB * 1024
const val GB = MB * 1024
const val TB = GB * 1024

/**
 * 将字节数格式化为人类可读的文件大小字符串
 *
 * 使用场景：CacheLoader 初始化时显示可用内存大小
 */
fun Long.formatFileSize(): String = Formatter.formatFileSize(JBusManager.manager.first().get(), this)
// endregion

// region ArrayMap 工具

/** 创建带初始元素的 ArrayMap */
fun <K, V> arrayMapof(vararg pairs: Pair<K, V>): ArrayMap<K, V> = ArrayMap<K, V>(pairs.size).apply { putAll(pairs) }

/** 创建空 ArrayMap */
fun <K, V> arrayMapof(): ArrayMap<K, V> = ArrayMap()
// endregion

// region Context 扩展

/**
 * 主线程 Handler，用于 postMain() 投递任务
 *
 * 注意：Coroutines 项目中优先使用 withContext(Dispatchers.Main)，此 Handler 仅为 toast 复用
 */
val Main_Worker by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

/**
 * 在主线程 Handler 上投递任务
 *
 * @param block 待执行的操作
 */
fun postMain(block: () -> Unit) = Main_Worker.post(block)

/** 获取 LayoutInflater 实例 */
val Context.inflater: LayoutInflater
    get() = LayoutInflater.from(this)

/** 获取屏幕显示参数 */
val Context.displayMetrics: android.util.DisplayMetrics
    get() = resources.displayMetrics

/** dp 转 px */
fun Context.dpToPx(dp: Float) = (dp * this.displayMetrics.density + 0.5).toInt()

/** px 转 dp */
fun Context.pxToDp(px: Float) = (px / this.displayMetrics.density + 0.5).toInt()

/** 便捷加载布局 */
fun Context.inflate(layoutResId: Int, parent: ViewGroup? = null, attachToRoot: Boolean = false): View =
    LayoutInflater.from(this).inflate(layoutResId, parent, attachToRoot)
// endregion

// region Gson 扩展

/**
 * 泛型 JSON 反序列化
 *
 * 通过 reified 类型参数避免传入 TypeToken
 * 使用场景：CacheLoader 读取缓存时反序列化为具体类型
 *
 * @param T 目标类型
 * @param json JSON 字符串
 * @return 反序列化结果，JSON 无效时返回 null
 */
@Nullable
inline fun <reified T> Gson.fromJson(json: String) = this.fromJson<T>(json, object : TypeToken<T>() {}.type)

/**
 * 将任意对象序列化为 JSON 字符串
 *
 * 使用场景：缓存写入、数据库 Entity 的 jsonStr 字段
 */
fun Any?.toJsonString() = GSON.toJson(this)
// endregion

// region 屏幕尺寸

/** 获取屏幕宽度（像素） */
val Context.screenWidth: Int
    inline get() {
        val wm = this.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getMetrics(this.displayMetrics)
        return displayMetrics.widthPixels
    }

/** 根据屏幕宽度计算网格列数：≤1080px → 3列，≤1440px → 4列，更大 → 5列 */
val Context.spanCount: Int
    inline get() = with(this.screenWidth) {
        when {
            this <= 1080 -> 3
            this <= 1440 -> 4
            else -> 5
        }
    }
// endregion

// region 剪贴板

/** 复制文本到系统剪贴板 */
fun Context.copy(content: String) {
    val cmb = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cmb.setPrimaryClip(ClipData.newPlainText(null, content))
}

/** 从系统剪贴板粘贴文本，无内容时返回 null */
fun Context.paste(): String? {
    val cmb = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cmb.primaryClip ?: return null
    return if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(this)?.toString() else null
}
// endregion

// region 包信息

/** 获取当前应用的 PackageInfo，失败时返回 null */
val Context.packageInfo: PackageInfo?
    get() = try {
        this.packageManager.getPackageInfo(this.packageName, 0)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
// endregion

// region Cursor 扩展

/** 按列名安全读取 String，列不存在时返回空字符串 */
fun Cursor.getStringByColumn(colName: String): String? =
    try {
        this.getString(this.getColumnIndexOrThrow(colName))
    } catch (ex: Exception) {
        ""
    }

/** 按列名安全读取 Int，列不存在时返回 -1 */
fun Cursor.getIntByColumn(colName: String): Int = try {
    this.getInt(this.getColumnIndexOrThrow(colName))
} catch (ex: Exception) {
    -1
}

/** 按列名安全读取 Long，列不存在时返回 -1 */
fun Cursor.getLongByColumn(colName: String): Long = try {
    this.getLong(this.getColumnIndexOrThrow(colName))
} catch (ex: Exception) {
    -1
}
// endregion

/**
 * 使用外部浏览器打开 URL
 *
 * @param url 目标 URL
 * @param errorHandler 无可用浏览器时的回调
 */
fun Context.browse(url: String, errorHandler: (Throwable) -> Unit = {}) {
    try {
        startActivity(Intent().apply {
            this.action = "android.intent.action.VIEW"
            this.data = Uri.parse(url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (e: Exception) {
        errorHandler(e)
    }
}

// region SharedPreferences

/** 从全局配置 SharedPreferences 读取字符串 */
fun getSp(key: String): String? =
    JBusManager.context.applicationContext.getSharedPreferences("config", Context.MODE_PRIVATE).getString(key, null)

/**
 * 写入全局配置 SharedPreferences
 *
 * 使用协程 IO 调度器避免阻塞主线程
 */
suspend fun saveSp(key: String, value: String) = withContext(Dispatchers.IO) {
    JBusManager.context.applicationContext.getSharedPreferences(
        "config",
        Context.MODE_PRIVATE
    ).edit().putString(key, value).apply()
}
// endregion
