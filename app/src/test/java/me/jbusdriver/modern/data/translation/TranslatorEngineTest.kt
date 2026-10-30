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

    // ---- buildCustomRequestUrl ----

    @Test
    fun buildCustomRequestUrl_encodesTextAndReplacesPlaceholders() {
        assertEquals(
            "https://x/api/ja/zh-TW/%E3%83%86%E3%82%B9%E3%83%88",
            buildCustomRequestUrl("https://x/api/{source}/{target}/{text}", "テスト", "ja", "zh-TW")
        )
    }

    @Test
    fun buildCustomRequestUrl_missingTextPlaceholderReturnsNull() {
        assertNull(buildCustomRequestUrl("https://x/api?q=1", "テスト", "ja", "zh-TW"))
    }

    @Test
    fun buildCustomRequestUrl_blankTemplateReturnsNull() {
        assertNull(buildCustomRequestUrl("   ", "テスト", "ja", "zh-TW"))
    }

    // ---- extractCustomResponse ----

    @Test
    fun extractCustomResponse_blankPathReturnsTrimmedBody() {
        assertEquals("譯文", extractCustomResponse("  譯文  ", ""))
    }

    @Test
    fun extractCustomResponse_blankPathBlankBodyReturnsNull() {
        assertNull(extractCustomResponse("   ", ""))
    }

    @Test
    fun extractCustomResponse_objectPath() {
        assertEquals("譯文", extractCustomResponse("""{"translation":"譯文"}""", "translation"))
    }

    @Test
    fun extractCustomResponse_nestedObjectPath() {
        assertEquals("譯文", extractCustomResponse("""{"responseData":{"translatedText":"譯文"}}""", "responseData.translatedText"))
    }

    @Test
    fun extractCustomResponse_arrayIndexPath() {
        assertEquals("譯文", extractCustomResponse("""{"data":[["譯文","原文"]]}""", "data.0.0"))
    }

    @Test
    fun extractCustomResponse_missingPathReturnsNull() {
        assertNull(extractCustomResponse("""{"other":1}""", "translation"))
    }

    @Test
    fun extractCustomResponse_blankLeafReturnsNull() {
        assertNull(extractCustomResponse("""{"translation":"   "}""", "translation"))
    }
}
