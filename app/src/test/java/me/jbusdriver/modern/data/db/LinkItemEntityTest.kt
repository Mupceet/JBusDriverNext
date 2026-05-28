package me.jbusdriver.modern.data.db

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates that the Room schema for [CollectDatabase] defines the correct unique index
 * on `t_link`: (dbType, key) instead of just (key).
 *
 * Room annotations have CLASS retention, so runtime reflection cannot read them.
 * Instead we verify the compile-time exported schema JSON, which is the canonical
 * representation of the database structure.
 */
class LinkItemEntityTest {

    @Test
    fun linkItem_uniqueIndexUsesDbTypeAndKey() {
        val tLink = loadTLinkEntity(2)
        val indices = tLink["indices"].asJsonArray

        assertEquals("t_link should have exactly one index", 1, indices.size())

        val index = indices[0].asJsonObject
        assertTrue("Index should be unique", index["unique"].asBoolean)

        val columns = index["columnNames"].asJsonArray
        assertEquals("Unique index should have 2 columns", 2, columns.size())
        assertEquals("dbType", columns[0].asString)
        assertEquals("key", columns[1].asString)
    }

    @Test
    fun collectDatabase_versionIs2() {
        val db = loadSchema(2)
        assertEquals(2, db["version"].asInt)
    }

    @Test
    fun collectDatabase_v1IndexWasOnlyKey() {
        val tLink = loadTLinkEntity(1)
        val index = tLink["indices"].asJsonArray[0].asJsonObject

        assertTrue("v1 index should be unique", index["unique"].asBoolean)
        val columns = index["columnNames"].asJsonArray
        assertEquals(1, columns.size())
        assertEquals("key", columns[0].asString)
    }

    private fun loadTLinkEntity(version: Int) =
        loadSchema(version)["entities"].asJsonArray
            .first { it.asJsonObject["tableName"].asString == "t_link" }
            .asJsonObject

    private fun loadSchema(version: Int) =
        JsonParser.parseString(
            File("schemas/me.jbusdriver.modern.data.db.CollectDatabase/$version.json")
                .readText()
        ).asJsonObject["database"].asJsonObject
}
