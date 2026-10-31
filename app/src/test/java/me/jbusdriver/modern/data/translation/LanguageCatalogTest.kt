package me.jbusdriver.modern.data.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageCatalogTest {

    @Test
    fun all_containsKomiStoreSet() {
        assertEquals(33, LanguageCatalog.all.size)
        assertEquals("zh-TW", LanguageCatalog.all.first { it.displayName == "Chinese (Traditional)" }.code)
    }

    @Test
    fun findByCode_matchesCaseInsensitive() {
        assertEquals("zh-TW", LanguageCatalog.findByCode("ZH-tw")?.code)
    }

    @Test
    fun findByCode_unknownReturnsNull() {
        assertNull(LanguageCatalog.findByCode("xx"))
        assertNull(LanguageCatalog.findByCode(null))
    }

    @Test
    fun resolveDeviceLanguage_chineseSimplifiedByRegion() {
        assertEquals("zh-CN", LanguageCatalog.resolveDeviceLanguage("zh", "CN"))
        assertEquals("zh-CN", LanguageCatalog.resolveDeviceLanguage("zh", ""))
    }

    @Test
    fun resolveDeviceLanguage_chineseTraditionalByRegion() {
        assertEquals("zh-TW", LanguageCatalog.resolveDeviceLanguage("zh", "TW"))
        assertEquals("zh-TW", LanguageCatalog.resolveDeviceLanguage("zh", "HK"))
        assertEquals("zh-TW", LanguageCatalog.resolveDeviceLanguage("zh", "MO"))
    }

    @Test
    fun resolveDeviceLanguage_matchesByLanguage() {
        assertEquals("ja", LanguageCatalog.resolveDeviceLanguage("ja", "JP"))
        assertEquals("en", LanguageCatalog.resolveDeviceLanguage("en", "US"))
        assertEquals("pt-BR", LanguageCatalog.resolveDeviceLanguage("pt", "BR"))
    }

    @Test
    fun resolveDeviceLanguage_unknownFallsBackToTraditionalChinese() {
        assertEquals("zh-TW", LanguageCatalog.resolveDeviceLanguage("xx", "XX"))
    }
}
