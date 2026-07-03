package me.jbusdriver.modern.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import me.jbusdriver.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CollectButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun reflects_uncollected_state_and_fires_toggle_on_click() {
        var toggles = 0
        composeRule.setContent {
            MaterialTheme {
                CollectButton(isCollected = false, onToggle = { toggles++ })
            }
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.collect))
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, toggles)
    }
}
