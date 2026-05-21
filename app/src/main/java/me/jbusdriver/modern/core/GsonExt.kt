package me.jbusdriver.modern.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.lang.reflect.Modifier.STATIC
import me.jbusdriver.modern.domain.model.ContentBlockAdapterFactory
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
 * - [NullSafeFactory]：反序列化后对 Kotlin 非空集合字段自动填充空实例，防止 Gson 绕过构造函数导致 NPE
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
        })
        .registerTypeAdapterFactory(NullSafeFactory)
        .registerTypeAdapterFactory(ContentBlockAdapterFactory)
        .serializeNulls().create()
}

/**
 * Gson TypeAdapterFactory，在反序列化后对 Kotlin 的非空集合/Map 字段做 null → 空实例替换。
 *
 * Gson 通过 Unsafe.allocateInstance() 绕过构造函数，导致 Kotlin 非空集合字段可能为 null。
 * 此 Factory 在 read() 完成后检查所有 List/Map/Set 字段，将 null 替换为空集合。
 */
private object NullSafeFactory : TypeAdapterFactory {
    override fun <T : Any> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        val raw = type.rawType
        // Only process project data classes or @Keep-annotated classes
        if (!raw.name.startsWith("me.jbusdriver.") && raw.getAnnotation(androidx.annotation.Keep::class.java) == null) return null
        val delegate = gson.getDelegateAdapter(this, type)
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T?) = delegate.write(out, value)
            override fun read(`in`: JsonReader): T? {
                val result = delegate.read(`in`) ?: return null
                fillNullCollections(result)
                return result
            }
        }
    }

    private fun fillNullCollections(obj: Any) {
        obj.javaClass.declaredFields.forEach { field ->
            if (java.util.Collection::class.java.isAssignableFrom(field.type)
                || java.util.Map::class.java.isAssignableFrom(field.type)
            ) {
                field.isAccessible = true
                if (field.get(obj) == null) {
                    val empty = when {
                        java.util.List::class.java.isAssignableFrom(field.type) -> emptyList<Any>()
                        java.util.Set::class.java.isAssignableFrom(field.type) -> emptySet<Any>()
                        java.util.Map::class.java.isAssignableFrom(field.type) -> emptyMap<Any, Any>()
                        else -> return@forEach
                    }
                    field.set(obj, empty)
                }
            }
        }
    }
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
