package me.jbusdriver.modern.data.localvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalVideoScannerTest {

    @Test
    fun scan_mapsFilesToEntitiesByExtractedCode() {
        val now = 1_700_000_000_000L
        val files = listOf(
            ScannedFile("ABC-001.mp4", "u1", "video/mp4", 10L),
            ScannedFile("ABC-001_4K.mkv", "u2", "video/x-matroska", 20L),
            ScannedFile("DEF-002.mp4", "u3", null, 30L),
            ScannedFile("clip.mp4", "u4", "video/mp4", 40L), // 无番号，丢弃
        )

        val entities = scanVideoFiles(files, now)

        assertEquals(3, entities.size)
        val abc = entities.filter { it.code == "ABC-001" }
        assertEquals(2, abc.size)
        assertTrue(abc.all { it.scannedAt == now })
        assertTrue(entities.any { it.code == "DEF-002" && it.uri == "u3" && it.size == 30L })
        assertTrue(entities.none { it.uri == "u4" })
    }

    @Test
    fun scan_uppercasesCodes() {
        val entities = scanVideoFiles(
            listOf(ScannedFile("abc-003.mp4", "u", null, 1L)),
            0L,
        )
        assertEquals("ABC-003", entities.single().code)
    }
}
