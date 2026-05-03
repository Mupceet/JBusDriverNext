package me.jbusdriver.modern.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Modifier.STATIC
import java.lang.reflect.Modifier.TRANSIENT
import java.util.Date

/**
 * 全局 Gson 实例
 *
 * 配置：
 * - 排除 transient 和 static 字段（与 Room @Transient 配合）
 * - Int 类型空安全：JSON 中为空或非法时返回 null 而非抛异常
 * - Date 类型：尝试字符串解析，失败时返回当前时间
 * - 序列化时包含 null 值
 *
 * 使用场景：全项目统一的 JSON 序列化/反序列化，包括缓存存取、数据库 Entity 转换
 */
val GSON by lazy {
    GsonBuilder().excludeFieldsWithModifiers(TRANSIENT, STATIC)
        .registerTypeAdapter(Int::class.java, JsonDeserializer<Int> { json, _, _ ->
            if (json.isJsonNull || json.asString.isEmpty()) {
                return@JsonDeserializer null
            }
            try {
                return@JsonDeserializer json.asInt
            } catch (e: NumberFormatException) {
                return@JsonDeserializer null
            }
        }).registerTypeAdapter(Date::class.java, JsonDeserializer { json, _, _ ->
            try {
                return@JsonDeserializer Date(json.asJsonPrimitive.asString)
            } catch (e: Exception) {
                return@JsonDeserializer Date()
            }
        }).serializeNulls().create()
}

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
