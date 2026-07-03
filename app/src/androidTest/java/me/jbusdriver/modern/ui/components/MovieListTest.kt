package me.jbusdriver.modern.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.jbusdriver.modern.test.aMovie
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MovieListTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renders_titles_and_click_fires_onMovieClick() {
        val m1 = aMovie(title = "First Movie", code = "AAA-001")
        val m2 = aMovie(title = "Second Movie", code = "AAA-002")
        var clicked = ""
        composeRule.setContent {
            MaterialTheme {
                MovieList(movies = listOf(m1, m2), onMovieClick = { movie, _ -> clicked = movie.title })
            }
        }
        composeRule.onNodeWithText("First Movie").assertIsDisplayed()
        composeRule.onNodeWithText("Second Movie").assertIsDisplayed()
        composeRule.onNodeWithText("Second Movie").performClick()
        assertEquals("Second Movie", clicked)
    }
}
