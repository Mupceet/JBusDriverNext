package me.jbusdriver.modern.core

import java.io.File

/**
 * 极简磁盘缓存，仅支持 String 键值对的读写删除。
 *
 * 职责：作为 CacheLoader 的磁盘持久层，将缓存值以文件形式存储在应用缓存目录。
 *
 * 使用场景：CacheLoader.persistentCached 和 getString 的磁盘读写。
 *
 * 线程：非线程安全，调用方需在 IO 调度器上执行（CacheLoader 已通过
 * withContext(Dispatchers.IO) 保证）。
 *
 * @param cacheDir 缓存文件存放目录
 */
class FileCache(private val cacheDir: File) {

    /** 将 [value] 写入以 [key] 的 hashCode 命名的文件。 */
    fun put(key: String, value: String) {
        file(key).writeText(value)
    }

    /** 读取 [key] 对应的文件内容，文件不存在时返回 null。 */
    fun get(key: String): String? {
        val f = file(key)
        return if (f.exists()) f.readText() else null
    }

    /** 删除 [key] 对应的缓存文件。 */
    fun remove(key: String) {
        file(key).let { if (it.exists()) it.delete() }
    }

    private fun file(key: String): File = File(cacheDir, key.hashCode().toString())
}
