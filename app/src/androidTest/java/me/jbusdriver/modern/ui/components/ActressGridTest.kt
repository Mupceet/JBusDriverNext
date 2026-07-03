package me.jbusdriver.modern.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.jbusdriver.modern.test.anActress
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ActressGridTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renders_names_and_click_fires_onActressClick() {
        val a1 = anActress(name = "Alpha", link = "https://test/actress/1")
        val a2 = anActress(name = "Beta", link = "https://test/actress/2")
        var clicked = ""
        composeRule.setContent {
            MaterialTheme {
                ActressGrid(
                    actresses = listOf(a1, a2),
                    hasMore = false,
                    onLoadMore = {},
                    onActressClick = { actress, _ -> clicked = actress.name }
                )
            }
        }
        composeRule.onNodeWithText("Alpha").assertIsDisplayed()
        composeRule.onNodeWithText("Beta").performClick()
        assertEquals("Beta", clicked)
    }
}
