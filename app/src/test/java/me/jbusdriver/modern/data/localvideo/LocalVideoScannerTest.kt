package me.jbusdriver.modern.data.localvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalVideoScannerTest {

    private val now = 1_700_000_000_000L

    @Test
    fun scan_keepsAllFilesAndUsesBlankCodeForUnclassified() {
        val files = listOf(
            ScannedFile("ABC-001.mp4", "u1", "video/mp4", 10L),
            ScannedFile("clip.mp4", "u4", "video/mp4", 40L), // 无法匹配 -> 保留，code=""
        )
        val entities = scanVideoFiles(files, collectedCodes = emptySet(), scannedAt = now)
        assertEquals(2, entities.size)
        val unclassified = entities.first { it.uri == "u4" }
        assertEquals("", unclassified.code)
        assertEquals("clip", unclassified.name)
    }

    @Test
    fun scan_firstLevelFolderNameUsedAsName() {
        val entities = scanVideoFiles(
            listOf(ScannedFile("x.mp4", "u", "video/mp4", 1L, parentFolderNames = listOf("ABC-123"))),
            emptySet(), now,
        )
        val e = entities.single()
        assertEquals("ABC-123", e.name)
        assertEquals("ABC-123", e.code)
    }

    @Test
    fun scan_outermostFirstLevelFolderWinsAsName() {
        val entities = scanVideoFiles(
            listOf(ScannedFile("x.mp4", "u", null, 1L, parentFolderNames = listOf("DEF-456", "ABC-123"))),
            emptySet(), now,
        )
        assertEquals("ABC-123", entities.single().name)
        assertEquals("ABC-123", entities.single().code)
    }

    @Test
    fun scan_rootFileUsesFileNameWithoutExtension() {
        val entities = scanVideoFiles(
            listOf(ScannedFile("ABC-123.mp4", "u", null, 1L, parentFolderNames = emptyList())),
            emptySet(), now,
        )
        assertEquals("ABC-123", entities.single().name)
        assertEquals("ABC-123", entities.single().code)
    }

    @Test
    fun scan_favoriteMatchTreatsDashAndUnderscoreAsSame() {
        val entities = scanVideoFiles(
            listOf(
                ScannedFile("ABC_123.mp4", "u1", null, 1L, parentFolderNames = emptyList()),
                ScannedFile("movie.mp4", "u2", null, 2L, parentFolderNames = listOf("ABC_123")),
            ),
            collectedCodes = setOf("ABC-123"), now,
        )
        assertEquals(2, entities.size)
        assertTrue(entities.all { it.code == "ABC-123" })
    }

    @Test
    fun scan_favoriteMatchBeatsParserFallback() {
        // ABC-123 在收藏中，即使文件名带后缀也直接归属收藏番号
        val entities = scanVideoFiles(
            listOf(ScannedFile("ABC-123 (1080p).mp4", "u", null, 1L, parentFolderNames = emptyList())),
            collectedCodes = setOf("ABC-123"), now,
        )
        assertEquals("ABC-123", entities.single().code)
    }

    @Test
    fun scan_parserPipelineConfirmsCodeForUncollected() {
        val entities = scanVideoFiles(
            listOf(
                ScannedFile("DEF-456.mp4", "u1", null, 1L),
                ScannedFile("FC2-PPV-1234567.mp4", "u2", null, 2L),
                ScannedFile("SSVR-001.mp4", "u3", null, 3L),
            ),
            emptySet(), now,
        )
        val byUri = entities.associateBy { it.uri }
        assertEquals("DEF-456", byUri.getValue("u1").code)
        assertEquals("FC2-PPV-1234567", byUri.getValue("u2").code)
        assertEquals("SSVR-001", byUri.getValue("u3").code)
    }

    @Test
    fun scan_uppercasesCodes() {
        val entities = scanVideoFiles(
            listOf(ScannedFile("abc-003.mp4", "u", null, 1L)),
            emptySet(), now,
        )
        assertEquals("ABC-003", entities.single().code)
    }

    @Test
    fun scan_folderAndRootFileAggregateUnderSameCode() {
        val entities = scanVideoFiles(
            listOf(
                ScannedFile("ABC-123.mp4", "u1", null, 1L, parentFolderNames = emptyList()),
                ScannedFile("movie.mp4", "u2", null, 2L, parentFolderNames = listOf("ABC-123")),
            ),
            emptySet(), now,
        )
        assertEquals(2, entities.size)
        assertTrue(entities.all { it.code == "ABC-123" })
    }
}
