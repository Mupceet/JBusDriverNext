package me.jbusdriver.modern.ui.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import me.jbusdriver.modern.ui.HeaderUiModel
import me.jbusdriver.modern.ui.MovieDetailUiModel
import org.junit.Rule
import org.junit.Test

class DetailContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renders_title_and_code_header() {
        val detail = MovieDetailUiModel(
            title = "Smoke Title",
            content = "",
            cover = "",
            headers = listOf(HeaderUiModel("識別碼", "SMOKE-001")),
            genres = emptyList(),
            actresses = emptyList(),
            imageSamples = emptyList(),
            relatedMovies = emptyList()
        )
        composeRule.setContent {
            MaterialTheme {
                DetailContent(
                    detail = detail,
                    padding = PaddingValues(0.dp),
                    onMovieClick = { },
                    onActressClick = { },
                    onGenreClick = { },
                    onHeaderClick = { },
                    onImageClick = { _, _ -> },
                    onMagnetClick = {}
                )
            }
        }
        composeRule.onNodeWithText("Smoke Title").assertIsDisplayed()
        composeRule.onNodeWithText("SMOKE-001").assertIsDisplayed()
    }
}
