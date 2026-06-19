package me.jbusdriver.modern.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileCacheTest {
    @Test
    fun cacheKeysWithSameJavaHashCodeDoNotOverwriteEachOther() {
        val dir = Files.createTempDirectory("jbus-file-cache").toFile()
        val cache = FileCache(dir, maxSizeBytes = 1024 * 1024)

        cache.put("FB", "first")
        cache.put("Ea", "second")

        assertEquals("first", cache.get("FB"))
        assertEquals("second", cache.get("Ea"))
    }

    @Test
    fun putThenGetReturnsStoredValue() {
        val dir = Files.createTempDirectory("jbus-file-cache").toFile()
        val cache = FileCache(dir, maxSizeBytes = 1024 * 1024)

        cache.put("key", "value")

        assertEquals("value", cache.get("key"))
    }

    @Test
    fun getReturnsNullForMissingKey() {
        val dir = Files.createTempDirectory("jbus-file-cache").toFile()
        val cache = FileCache(dir, maxSizeBytes = 1024 * 1024)

        assertNull(cache.get("missing"))
    }

    @Test
    fun getReadsValueFromLegacyHashCodeFile() {
        val dir = Files.createTempDirectory("jbus-file-cache").toFile()
        // 手动放置旧版以 hashCode 命名的遗留文件，验证向后兼容读取
        File(dir, "key".hashCode().toString()).writeText("legacy")
        val cache = FileCache(dir, maxSizeBytes = 1024 * 1024)

        assertEquals("legacy", cache.get("key"))
    }

    @Test
    fun removeDeletesEntry() {
        val dir = Files.createTempDirectory("jbus-file-cache").toFile()
        val cache = FileCache(dir, maxSizeBytes = 1024 * 1024)

        cache.put("key", "value")
        cache.remove("key")

        assertNull(cache.get("key"))
    }

    @Test
    fun putOverwritesExistingValue() {
        val dir = Files.createTempDirectory("jbus-file-cache").toFile()
        val cache = FileCache(dir, maxSizeBytes = 1024 * 1024)

        cache.put("key", "first")
        cache.put("key", "second")

        assertEquals("second", cache.get("key"))
    }

    @Test
    fun trimRemovesOldestFilesWhenOverSizeLimit() {
        val dir = Files.createTempDirectory("jbus-trim").toFile()
        val cache = FileCache(dir, maxSizeBytes = 200)
        val base = 1_700_000_000_000L
        // 三个各 100 字节的文件，总大小 300 > 上限 200
        val oldest = File(dir, "a").apply {
            writeText("x".repeat(100))
            setLastModified(base)
        }
        val middle = File(dir, "b").apply {
            writeText("x".repeat(100))
            setLastModified(base + 1_000)
        }
        val newest = File(dir, "c").apply {
            writeText("x".repeat(100))
            setLastModified(base + 2_000)
        }

        // put 触发 trim：目标 75%（150），需删到 <= 150
        cache.put("trigger", "y")

        assertFalse(oldest.exists())
        assertFalse(middle.exists())
        assertTrue(newest.exists())
    }
}
