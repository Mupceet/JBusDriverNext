package me.jbusdriver.modern.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageInfo
import android.text.format.Formatter
import androidx.collection.ArrayMap
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
fun Long.formatFileSize(): String =
    Formatter.formatFileSize(JBusManager.manager.first().get(), this)
// endregion

// region ArrayMap 工具

/** 创建带初始元素的 ArrayMap */
fun <K, V> arrayMapof(vararg pairs: Pair<K, V>): ArrayMap<K, V> =
    ArrayMap<K, V>(pairs.size).apply { putAll(pairs) }

/** 创建空 ArrayMap */
fun <K, V> arrayMapof(): ArrayMap<K, V> = ArrayMap()
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
inline fun <reified T> Gson.fromJson(json: String): T? =
    this.fromJson<T>(json, object : TypeToken<T>() {}.type)

/**
 * 将任意对象序列化为 JSON 字符串
 *
 * 使用场景：缓存写入、数据库 Entity 的 jsonStr 字段
 */
fun Any?.toJsonString(): String = GSON.toJson(this)
// endregion

// region 屏幕尺寸

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
