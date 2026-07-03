package me.jbusdriver.modern.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import me.jbusdriver.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ErrorViewTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shows_message_and_retry_invokes_callback() {
        var retried = false
        composeRule.setContent {
            MaterialTheme { ErrorView(message = "boom", onRetry = { retried = true }) }
        }
        composeRule.onNodeWithText("boom").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.retry)).performClick()
        assertTrue(retried)
    }
}
