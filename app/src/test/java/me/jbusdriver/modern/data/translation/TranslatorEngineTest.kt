package me.jbusdriver.modern.data.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslatorEngineTest {

    // ---- parseGtxBody ----

    @Test
    fun parseGtxBody_joinsSegments() {
        val body = """[[["譯文A","原文A",null,null,1],["譯文B","原文B",null,null,1]],null,"ja"]"""
        assertEquals("譯文A譯文B", parseGtxBody(body))
    }

    @Test
    fun parseGtxBody_blankResultReturnsNull() {
        assertNull(parseGtxBody("""[[["","原文"]]]"""))
    }

    @Test
    fun parseGtxBody_malformedReturnsNull() {
        assertNull(parseGtxBody("not json"))
    }
}
