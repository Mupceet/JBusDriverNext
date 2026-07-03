package me.jbusdriver.modern.ui.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import me.jbusdriver.R
import me.jbusdriver.modern.test.aMagnet
import org.junit.Rule
import org.junit.Test

class MagnetBottomSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun empty_state_shows_no_magnet_notice() {
        composeRule.setContent {
            MaterialTheme { MagnetBottomSheet(uiState = MovieDetailUiState(), onDismiss = {}) }
        }
        composeRule.onNodeWithText(context.getString(R.string.no_magnet)).assertExists()
    }

    @Test
    fun list_state_shows_title_and_magnet_names() {
        val state = MovieDetailUiState(magnets = listOf(aMagnet(name = "MAG-NAME-1")))
        composeRule.setContent {
            MaterialTheme { MagnetBottomSheet(uiState = state, onDismiss = {}) }
        }
        composeRule.onNodeWithText(context.getString(R.string.magnet_links)).assertExists()
        composeRule.onNodeWithText("MAG-NAME-1").assertExists()
    }
}
