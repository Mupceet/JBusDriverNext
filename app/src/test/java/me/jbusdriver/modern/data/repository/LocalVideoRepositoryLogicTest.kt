package me.jbusdriver.modern.data.repository

import me.jbusdriver.modern.data.db.entity.LocalVideoEntity
import me.jbusdriver.modern.data.localvideo.DeleteFileResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalVideoRepositoryLogicTest {

    private fun e(id: Int, code: String, title: String? = null, image: String? = null) =
        LocalVideoEntity(id = id, code = code, name = "$code.mp4", uri = "u$id", mime = "video/mp4", size = 1L, scannedAt = 0L, title = title, imageUrl = image)

    @Test
    fun groupLocalVideoEntities_groupsByCode_picksFirstNonNullRepresentative() {
        val groups = groupLocalVideoEntities(listOf(
            e(1, "ABC", title = "T", image = "img"),
            e(2, "ABC"),
            e(3, "DEF"),
        ))
        assertEquals(2, groups.size)
        val abc = groups.first { it.code == "ABC" }
        assertEquals("T", abc.title)
        assertEquals("img", abc.imageUrl)
        assertEquals(2, abc.files.size)
    }

    @Test
    fun planDeletion_successAndNotFoundRemoved_failedKept() {
        val entities = listOf(e(1, "A"), e(2, "B"), e(3, "C"))
        val results = listOf(DeleteFileResult.SUCCESS, DeleteFileResult.FAILED, DeleteFileResult.NOT_FOUND)
        val plan = planDeletion(entities, results)
        assertEquals(listOf(1, 3), plan.removedIds)
        assertEquals(1, plan.failed)
    }

    @Test
    fun planDeletion_empty() {
        val plan = planDeletion(emptyList(), emptyList())
        assertTrue(plan.removedIds.isEmpty())
        assertEquals(0, plan.failed)
    }

    @Test
    fun preserveSnapshots_carriesForwardSnapshotForSameCode() {
        val existing = listOf(
            e(1, "ABC", title = "T", image = "img").copy(date = "2024-01-01"),
        )
        val scanned = listOf(
            LocalVideoEntity(id = 0, code = "ABC", name = "a.mp4", uri = "u1", mime = null, size = 1, scannedAt = 10),
            LocalVideoEntity(id = 0, code = "DEF", name = "d.mp4", uri = "u2", mime = null, size = 1, scannedAt = 10),
        )
        val result = preserveSnapshots(scanned, existing)
        assertEquals(2, result.size)
        val abc = result.first { it.code == "ABC" }
        assertEquals("T", abc.title)
        assertEquals("img", abc.imageUrl)
        assertEquals("2024-01-01", abc.date)
        val def = result.first { it.code == "DEF" }
        assertNull(def.title)
    }

    @Test
    fun preserveSnapshots_dropsSnapshotWhenCodeGone() {
        val existing = listOf(e(1, "ABC", title = "T"))
        assertTrue(preserveSnapshots(emptyList(), existing).isEmpty())
    }
}
