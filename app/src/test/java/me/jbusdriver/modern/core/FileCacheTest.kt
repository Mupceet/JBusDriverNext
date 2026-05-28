package me.jbusdriver.modern.core

import org.junit.Assert.assertEquals
import org.junit.Test
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
}
