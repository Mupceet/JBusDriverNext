package me.jbusdriver.modern.core

import java.io.File
import java.security.MessageDigest

/**
 * 极简磁盘缓存，仅支持 String 键值对的读写删除。
 *
 * 职责：作为 CacheStore 的磁盘持久层，将缓存值以文件形式存储在应用缓存目录。
 * 写入时自动淘汰旧文件，保证总大小不超过 [maxSizeBytes]。
 *
 * 使用场景：DefaultCacheStore 和 CacheStore.persistentCached 的磁盘读写。
 *
 * 线程：非线程安全，调用方需在 IO 调度器上执行（DefaultCacheStore 已通过
 * withContext(Dispatchers.IO) 保证）。
 *
 * @param cacheDir 缓存文件存放目录
 * @param maxSizeBytes 磁盘缓存上限，默认 50MB
 */
class FileCache(
    private val cacheDir: File,
    private val maxSizeBytes: Long = 50 * 1024 * 1024
) {

    /** 将 [value] 写入以 [key] 的 SHA-256 哈希命名的文件。 */
    fun put(key: String, value: String) {
        cacheDir.mkdirs()
        file(key).writeText(value)
        trim()
    }

    /** 读取 [key] 对应的文件内容，文件不存在时返回 null。 */
    fun get(key: String): String? {
        val f = file(key)
        if (f.exists()) return f.readText()
        val legacy = legacyFile(key)
        return if (legacy.exists()) legacy.readText() else null
    }

    /** 删除 [key] 对应的缓存文件。 */
    fun remove(key: String) {
        file(key).let { if (it.exists()) it.delete() }
        legacyFile(key).let { if (it.exists()) it.delete() }
    }

    private fun file(key: String): File = File(cacheDir, key.sha256())

    private fun legacyFile(key: String): File = File(cacheDir, key.hashCode().toString())

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }

    /** 超过上限时按最后修改时间删除最旧的文件，回退到 75% 容量。 */
    private fun trim() {
        val files = cacheDir.listFiles()?.asList() ?: return
        val totalSize = files.sumOf { it.length() }
        if (totalSize <= maxSizeBytes) return

        val targetSize = (maxSizeBytes * 0.75).toLong()
        var currentSize = totalSize
        for (f in files.sortedBy { it.lastModified() }) {
            if (currentSize <= targetSize) break
            currentSize -= f.length()
            f.delete()
        }
    }
}
