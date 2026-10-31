package me.jbusdriver.modern.data.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewTranslatorsTest {

    // ---- Youdao ----

    @Test
    fun buildSignInput_shortTextUnchanged() {
        assertEquals("テスト", buildSignInput("テスト"))
    }

    @Test
    fun buildSignInput_longTextHeadLenTail() {
        val text = (1..40).joinToString("") { "a" } // 40 chars
        assertEquals(text.take(10) + "40" + text.takeLast(10), buildSignInput(text))
    }

    @Test
    fun sha256Hex_knownVector() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex("")
        )
    }

    @Test
    fun youdaoLangCode_mapsChineseVariants() {
        assertEquals("auto", youdaoLangCode("auto"))
        assertEquals("zh-CHS", youdaoLangCode("zh-CN"))
        assertEquals("zh-CHT", youdaoLangCode("zh-TW"))
        assertEquals("ja", youdaoLangCode("ja"))
    }

    @Test
    fun parseYoudaoBody_joinsTranslationArray() {
        assertEquals("第一行\n第二行", parseYoudaoBody("""{"errorCode":"0","translation":["第一行","第二行"]}"""))
    }

    @Test
    fun parseYoudaoBody_errorCodeReturnsNull() {
        assertNull(parseYoudaoBody("""{"errorCode":"108","msg":"param error"}"""))
    }

    // ---- LibreTranslate ----

    @Test
    fun libreCodes_mapVariantsAndAuto() {
        assertEquals("auto", libreSourceCode("auto"))
        assertEquals("zh", libreSourceCode("zh-CN"))
        assertEquals("zt", libreTargetCode("zh-TW"))
        assertNull(libreTargetCode("auto"))
        assertEquals("ja", libreTargetCode("ja-JP"))
    }

    @Test
    fun parseLibreBody_extractsTranslatedText() {
        assertEquals("譯文", parseLibreBody("""{"translatedText":"譯文"}"""))
    }

    @Test
    fun parseLibreBody_errorFieldReturnsNull() {
        assertNull(parseLibreBody("""{"error":"quota exceeded"}"""))
    }

    // ---- DeepL ----

    @Test
    fun deeplHost_switchesByFxSuffix() {
        assertEquals("https://api-free.deepl.com", deeplHost("abc:fx"))
        assertEquals("https://api.deepl.com", deeplHost("abc"))
    }

    @Test
    fun deeplCodes_mapVariants() {
        assertEquals("ZH-HANT", deeplTargetCode("zh-TW"))
        assertEquals("ZH-HANS", deeplTargetCode("zh-CN"))
        assertEquals("EN-US", deeplTargetCode("en"))
        assertNull(deeplSourceCode("auto"))
        assertEquals("ZH", deeplSourceCode("zh-TW"))
        assertEquals("JA", deeplSourceCode("ja"))
    }

    @Test
    fun parseDeeplBody_extractsFirstTranslation() {
        assertEquals(
            "譯文",
            parseDeeplBody("""{"translations":[{"detected_source_language":"JA","text":"譯文"}]}""")
        )
    }

    @Test
    fun parseDeeplBody_errorObjectReturnsNull() {
        assertNull(parseDeeplBody("""{"message":"Wrong auth key"}"""))
    }

    // ---- Microsoft ----

    @Test
    fun msLangCode_mapsVariants() {
        assertEquals("zh-Hant", msLangCode("zh-TW"))
        assertEquals("zh-Hans", msLangCode("zh-CN"))
        assertEquals("pt-br", msLangCode("pt"))
        assertEquals("nb", msLangCode("no"))
        assertEquals("ja", msLangCode("ja"))
    }

    @Test
    fun msSourceCode_autoReturnsNull() {
        assertNull(msSourceCode("auto"))
        assertEquals("ja", msSourceCode("ja"))
    }

    @Test
    fun parseMicrosoftBody_extractsFirstTranslation() {
        assertEquals(
            "譯文",
            parseMicrosoftBody("""[{"detectedLanguage":{"language":"ja"},"translations":[{"text":"譯文","to":"zh-Hant"}]}]""")
        )
    }

    @Test
    fun parseMicrosoftBody_errorObjectReturnsNull() {
        assertNull(parseMicrosoftBody("""{"error":{"code":401000,"message":"invalid key"}}"""))
    }
}
