package me.jbusdriver.modern.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test fun parsesKnownValues() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreferenceValue("system"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromPreferenceValue("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromPreferenceValue("dark"))
    }
    @Test fun unknownOrNullFallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreferenceValue(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreferenceValue("nonsense"))
    }
    @Test fun preferenceValueRoundTrips() {
        ThemeMode.entries.forEach {
            assertEquals(it, ThemeMode.fromPreferenceValue(it.preferenceValue))
        }
    }
}
