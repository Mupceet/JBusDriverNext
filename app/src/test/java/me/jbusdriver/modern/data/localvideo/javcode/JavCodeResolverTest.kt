package me.jbusdriver.modern.data.localvideo.javcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JavCodeResolverTest {

    // ---- Standard parser ----

    @Test
    fun standard_exactCode() {
        assertEquals("ABC-123", JavCodeResolver.resolve("ABC-123").code)
        assertEquals(MatchConfidence.CONFIDENT, JavCodeResolver.resolve("ABC-123").confidence)
    }

    @Test
    fun standard_noSeparator_keepsNameForm() {
        // 入库形态保留名称原始写法：无分隔符不补 `-`（站点番号本身可能无连字符，如 TAD8802）
        assertEquals("ABC123", JavCodeResolver.resolve("ABC123").code)
    }

    @Test
    fun standard_underscore_keepsNameForm() {
        assertEquals("ABC_123", JavCodeResolver.resolve("ABC_123").code)
    }

    @Test
    fun standard_siteFormWithoutDash_preserved() {
        // 回归用例：TAD8802 站点番号无连字符，统一补 `-` 会导致详情 URL /TAD-8802 不存在、信息无法加载
        assertEquals("TAD8802", JavCodeResolver.resolve("TAD8802").code)
        assertEquals(MatchConfidence.CONFIDENT, JavCodeResolver.resolve("TAD8802").confidence)
    }

    @Test
    fun standard_keepsAlphabeticSuffix() {
        assertEquals("ABC-123-C", JavCodeResolver.resolve("ABC-123-C").code)
        assertEquals("ABC-123D", JavCodeResolver.resolve("ABC-123D").code)
    }

    @Test
    fun standard_uppercasesInput() {
        assertEquals("ABC-123", JavCodeResolver.resolve("abc-123").code)
    }

    @Test
    fun standard_requiresAtLeastTwoDigits() {
        assertNull(JavCodeResolver.resolve("ABC-1").code)
    }

    // ---- Noise filter ----

    @Test
    fun noise_stripsResolutionParenthesis() {
        assertEquals("ABC-123", JavCodeResolver.resolve("ABC-123 (1080p)").code)
    }

    @Test
    fun noise_stripsResolutionSuffix() {
        assertEquals("ABC-123", JavCodeResolver.resolve("ABC-123 4K").code)
        assertEquals("ABC-123", JavCodeResolver.resolve("ABC-123_4K").code)
        assertEquals("ABC-123", JavCodeResolver.resolve("ABC-123-1080P").code)
    }

    @Test
    fun noise_keepsCodeInsideBrackets_butStripsGroupTag() {
        assertEquals("ABC-123", JavCodeResolver.resolve("[ABC-123]").code)
        assertEquals("ABC-123", JavCodeResolver.resolve("[Group] ABC-123").code)
    }

    @Test
    fun noise_stripsQualityWords() {
        assertEquals("ABC-123", JavCodeResolver.resolve("ABC-123 FHD").code)
        assertEquals("ABC-123", JavCodeResolver.resolve("ABC-123 x264").code)
    }

    // ---- FC2 parser ----

    @Test
    fun fc2_dashedForm() {
        assertEquals("FC2-PPV-1234567", JavCodeResolver.resolve("FC2-PPV-1234567").code)
    }

    @Test
    fun fc2_withoutDash_keepsNameForm() {
        assertEquals("FC2PPV-1234567", JavCodeResolver.resolve("FC2PPV-1234567").code)
    }

    @Test
    fun fc2_withSpaces() {
        assertEquals("FC2-PPV-1234567", JavCodeResolver.resolve("FC2 PPV 1234567").code)
    }

    @Test
    fun fc2_withoutPpv() {
        assertEquals("FC2-1234567", JavCodeResolver.resolve("FC2-1234567").code)
    }

    // ---- VR parser ----

    @Test
    fun vr_suffixMarkerStripped() {
        assertEquals("ABC-001", JavCodeResolver.resolve("ABC-001VR").code)
        assertEquals("ABC-001", JavCodeResolver.resolve("ABC-001 VR").code)
    }

    @Test
    fun vr_spacedPrefixMarkerStripped() {
        assertEquals("ABC-001", JavCodeResolver.resolve("VR ABC-001").code)
    }

    @Test
    fun vr_tightPrefixKeptAsPartOfCode() {
        // 紧贴的 `VR` 前缀与"站点番号本身以 VR 开头"（VRPRD-202、VRKM-1874）在字符串上无法区分，
        // 按"文件名照抄站点"的启发式保留原样；剥成 /PRD-202 会让详情 URL 取不到内容（同 TAD8802）。
        assertEquals("VRABC-001", JavCodeResolver.resolve("VRABC-001").code)
        assertEquals("VRPRD-202", JavCodeResolver.resolve("VRPRD-202").code)
        assertEquals(MatchConfidence.CONFIDENT, JavCodeResolver.resolve("VRKM-1874").confidence)
    }

    @Test
    fun vr_keepsVrAsPartOfKnownPrefix() {
        // SSVR 是真实厂商前缀，不应被剥离成 SS-001
        assertEquals("SSVR-001", JavCodeResolver.resolve("SSVR-001").code)
    }

    // ---- Legacy parser ----

    @Test
    fun legacy_heyzo() {
        assertEquals("HEYZO-1234", JavCodeResolver.resolve("HEYZO-1234").code)
    }

    @Test
    fun legacy_siro() {
        assertEquals("SIRO-1234", JavCodeResolver.resolve("SIRO-1234").code)
    }

    @Test
    fun legacy_gana() {
        assertEquals("200GANA-1234", JavCodeResolver.resolve("200GANA-1234").code)
    }

    @Test
    fun legacy_tokyoHot() {
        assertEquals("TOKYO-HOT-N0123", JavCodeResolver.resolve("TOKYO-HOT-N0123").code)
    }

    // ---- Site-specific parser ----

    @Test
    fun site_1pondo() {
        assertEquals("1PONDO-123456_789", JavCodeResolver.resolve("1pondo 123456_789").code)
    }

    @Test
    fun site_10musume() {
        assertEquals("10MUSUME-123456_789", JavCodeResolver.resolve("10musume 123456_789").code)
    }

    @Test
    fun site_caribbeancom() {
        assertEquals("CARIBBEANCOM-123456_789", JavCodeResolver.resolve("caribbeancom 123456_789").code)
    }

    @Test
    fun site_pacopacomama() {
        assertEquals("PACOPACOMAMA-123456_789", JavCodeResolver.resolve("pacopacomama-123456_789").code)
    }

    // ---- Legacy extractor fallback（分级 parser 无候选时兜底）----

    @Test
    fun fallback_digitPrefixedAmateurCode() {
        // 真实站点采样形态：分级 parser 的字母开头正则不覆盖，由 extractCode 兜底
        assertEquals("259LUXU-1895", JavCodeResolver.resolve("259LUXU-1895").code)
        assertEquals("300MIUM-1377", JavCodeResolver.resolve("300MIUM-1377").code)
        assertEquals(MatchConfidence.CONFIDENT, JavCodeResolver.resolve("390JAC-240").confidence)
    }

    @Test
    fun fallback_dateStyleUncensoredCode() {
        // 两种分隔符写法在站点并存为不同番号，入库保留名称原样
        assertEquals("080126_001", JavCodeResolver.resolve("080126_001").code)
        assertEquals("060126-001", JavCodeResolver.resolve("060126-001").code)
    }

    @Test
    fun fallback_singleLetterPrefixCode() {
        assertEquals("C-1534", JavCodeResolver.resolve("C-1534").code)
        assertEquals("P-2982", JavCodeResolver.resolve("P-2982").code)
    }

    @Test
    fun fallback_digitsPlusKCode() {
        assertEquals("054K", JavCodeResolver.resolve("054k").code)
    }

    // ---- Scoring / ambiguity ----

    @Test
    fun ambiguous_whenNoCandidate() {
        val r = JavCodeResolver.resolve("clip")
        assertNull(r.code)
        assertEquals(MatchConfidence.AMBIGUOUS, r.confidence)
    }

    @Test
    fun ambiguous_whenTwoCompetingCandidates() {
        val r = JavCodeResolver.resolve("ABC-123 DEF-456")
        assertNull(r.code)
        assertEquals(MatchConfidence.AMBIGUOUS, r.confidence)
    }

    @Test
    fun confident_knownPrefixBoosted() {
        assertEquals("SSIS-001", JavCodeResolver.resolve("SSIS-001").code)
        assertEquals(MatchConfidence.CONFIDENT, JavCodeResolver.resolve("SSIS-001").confidence)
    }

    @Test
    fun confident_resolutionOnlyNameIsAmbiguous() {
        val r = JavCodeResolver.resolve("1080p_ABC-123")
        assertEquals("ABC-123", r.code)
        assertEquals(MatchConfidence.CONFIDENT, r.confidence)
    }

    // ---- 真实站点样本：入库形态必须与文件名/站点原样一致 ----

    /**
     * 详情 URL 直接由番号拼成（`/番号`），而站点番号写法因厂商而异（TAD8802 无分隔符、
     * 080126_001 用下划线）。分级 parser 内部补齐的规范形态（补 `-`）一旦泄漏到入库番号，
     * URL 就会静默取不到内容，故用真实站点样本（见 test resources localvideo）锁死形态。
     */
    @Test
    fun realSiteCodes_keepVerbatimForm() {
        for (fileName in SAMPLE_FILES) {
            for (code in sampleCodes(fileName)) {
                val resolved = JavCodeResolver.resolve(code)
                assertEquals("$fileName 形态被改写: $code", code.uppercase(), resolved.code)
                assertEquals("$fileName 判定应为 confident: $code", MatchConfidence.CONFIDENT, resolved.confidence)
            }
        }
    }

    /** 站点番号本身以 `VR` 开头的厂商标签（样本里的 VRPRD/VRKM/VRNC）必须原样入库。 */
    @Test
    fun vrPrefixedSiteLabels_keepVerbatimForm() {
        for (code in sampleCodes("censored_codes.txt").filter { it.uppercase().startsWith("VR") }) {
            assertEquals(code.uppercase(), JavCodeResolver.resolve(code).code)
        }
    }

    /** 读取站点样本番号（过滤注释与空行），并校验样本量。 */
    private fun sampleCodes(fileName: String): List<String> {
        val codes = javaClass.getResourceAsStream("/localvideo/$fileName")!!
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue("$fileName 样本应至少 500 个，实际 ${codes.size}", codes.size >= 500)
        return codes
    }

    private companion object {
        val SAMPLE_FILES = listOf("censored_codes.txt", "uncensored_codes.txt")
    }
}
