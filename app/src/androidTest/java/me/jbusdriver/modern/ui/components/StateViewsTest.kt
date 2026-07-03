package me.jbusdriver.modern.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import me.jbusdriver.R
import org.junit.Rule
import org.junit.Test

class StateViewsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun emptyState_shows_message() {
        composeRule.setContent {
            MaterialTheme { EmptyStateView(message = "no data here") }
        }
        composeRule.onNodeWithText("no data here").assertIsDisplayed()
    }

    @Test
    fun noMoreItems_shows_resource_text() {
        composeRule.setContent {
            MaterialTheme { NoMoreItemsView() }
        }
        composeRule.onNodeWithText(context.getString(R.string.no_more)).assertIsDisplayed()
    }
}
