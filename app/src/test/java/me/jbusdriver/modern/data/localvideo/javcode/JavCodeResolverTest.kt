package me.jbusdriver.modern.data.localvideo.javcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JavCodeResolverTest {

    // ---- Standard parser ----

    @Test
    fun standard_exactCode() {
        assertEquals("ABC-123", JavCodeResolver.resolve("ABC-123").code)
        assertEquals(MatchConfidence.CONFIDENT, JavCodeResolver.resolve("ABC-123").confidence)
    }

    @Test
    fun standard_noSeparator_insetsDash() {
        assertEquals("ABC-123", JavCodeResolver.resolve("ABC123").code)
    }

    @Test
    fun standard_underscoreIsSameAsDash() {
        assertEquals("ABC-123", JavCodeResolver.resolve("ABC_123").code)
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
    fun fc2_withoutDash() {
        assertEquals("FC2-PPV-1234567", JavCodeResolver.resolve("FC2PPV-1234567").code)
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
    fun vr_prefixMarkerStripped() {
        assertEquals("ABC-001", JavCodeResolver.resolve("VRABC-001").code)
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
}
