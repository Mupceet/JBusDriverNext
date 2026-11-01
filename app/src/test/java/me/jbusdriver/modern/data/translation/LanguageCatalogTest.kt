package me.jbusdriver.modern.data.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageCatalogTest {

    @Test
    fun all_containsExactlyThreeSupportedLanguages() {
        assertEquals(listOf("zh-CN", "zh-TW", "en"), LanguageCatalog.all.map { it.code })
    }

    @Test
    fun findByCode_matchesCaseInsensitive() {
        assertEquals("zh-TW", LanguageCatalog.findByCode("ZH-tw")?.code)
        assertEquals("zh-CN", LanguageCatalog.findByCode("zh-cn")?.code)
    }

    @Test
    fun findByCode_unknownOrBlankReturnsNull() {
        assertNull(LanguageCatalog.findByCode("xx"))
        assertNull(LanguageCatalog.findByCode(null))
        assertNull(LanguageCatalog.findByCode(""))
    }
}
