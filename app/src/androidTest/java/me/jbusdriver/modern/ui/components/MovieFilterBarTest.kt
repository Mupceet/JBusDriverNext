package me.jbusdriver.modern.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import me.jbusdriver.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MovieFilterBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun clicking_inactive_segment_fires_toggle() {
        var toggled = false
        composeRule.setContent {
            MaterialTheme {
                MovieFilterBar(
                    magnetCount = 3,
                    totalCount = 10,
                    showAll = false,
                    onToggle = { toggled = true }
                )
            }
        }
        // showAll=false → "all movies" segment inactive → clicking it toggles
        composeRule.onNodeWithText(context.getString(R.string.all_movies_count, 10)).performClick()
        assertTrue(toggled)
    }

    @Test
    fun clicking_active_segment_does_not_toggle() {
        var toggled = false
        composeRule.setContent {
            MaterialTheme {
                MovieFilterBar(
                    magnetCount = 3,
                    totalCount = 10,
                    showAll = false,
                    onToggle = { toggled = true }
                )
            }
        }
        // showAll=false → "magnet" segment active → clicking it is a no-op
        composeRule.onNodeWithText(context.getString(R.string.magnet_count, 3)).performClick()
        assertFalse(toggled)
    }
}
