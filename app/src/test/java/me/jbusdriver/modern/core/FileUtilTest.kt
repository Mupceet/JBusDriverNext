package me.jbusdriver.modern.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileUtilTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `create dir creates missing directory`() {
        val target = File(temporaryFolder.root, "new-dir")

        assertEquals(target.path, createDir(target.path))
        assertTrue(target.isDirectory)
    }

    @Test
    fun `create dir returns existing directory`() {
        val target = temporaryFolder.newFolder("existing")

        assertEquals(target.path, createDir(target.path))
        assertTrue(target.isDirectory)
    }

    @Test
    fun `create dir replaces same-name file with directory`() {
        val target = temporaryFolder.newFile("conflict")

        assertEquals(target.path, createDir(target.path))
        assertTrue(target.isDirectory)
    }

    @Test
    fun `create dir returns null when path is blank`() {
        assertNull(createDir("   "))
    }
}
