package me.jbusdriver.modern.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import me.jbusdriver.R
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import me.jbusdriver.modern.domain.model.SearchType
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.components.ActressGrid
import me.jbusdriver.modern.ui.components.ErrorView
import me.jbusdriver.modern.ui.components.MovieList

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    defaultSearchType: String = "",
    onMovieClick: (MovieUiModel) -> Unit = {},
    onActressClick: (ActressUiModel) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var searchInput by rememberSaveable { mutableStateOf(uiState.query) }
    var isDeletingHistory by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val query = searchInput.trim()
            if (query.isBlank()) {
                focusRequester.requestFocus()
            }
        }
    }

    LaunchedEffect(uiState.query) {
        if (uiState.query != searchInput) {
            searchInput = uiState.query
        }
    }

    LaunchedEffect(defaultSearchType) {
        if (defaultSearchType.isNotBlank()) {
            try {
                val type = SearchType.valueOf(defaultSearchType)
                viewModel.setSearchType(type)
            } catch (_: IllegalArgumentException) {
                // Ignore invalid search type
            }
        }
    }

    fun doSearch() {
        val query = searchInput.trim()
        if (query.isNotBlank()) {
            focusManager.clearFocus()
            viewModel.search(query)
        }
    }

    val dismissKeyboardModifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.any { it.pressed }) {
                    focusManager.clearFocus()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        // Search input with back button
        OutlinedTextField(
            value = searchInput,
            onValueChange = { searchInput = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doSearch() }),
            shape = RoundedCornerShape(25),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(horizontal = 16.dp),
            leadingIcon = {
                IconButton(onClick = {
                    focusManager.clearFocus()
                    onBack()
                }) {
                    Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = "返回")
                }
            },
            trailingIcon = {
                if (searchInput.isNotEmpty()) {
                    IconButton(onClick = {
                        searchInput = ""
                        viewModel.clearSearch()
                        focusRequester.requestFocus()
                    }) {
                        Icon(
                            painterResource(R.drawable.close_24px),
                            contentDescription = "清除"
                        )
                    }
                }
            }
        )

        // Search type chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(SearchType.entries) { type ->
                FilterChip(
                    selected = uiState.searchType == type,
                    onClick = {
                        val query = searchInput.trim()
                        if (query.isNotBlank()) {
                            viewModel.search(query, type)
                        } else {
                            viewModel.setSearchType(type)
                        }
                    },
                    label = { Text(type.title, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Results
        val isActress = uiState.searchType == SearchType.ACTRESS
        val hasResults =
            if (isActress) uiState.actressResults.isNotEmpty() else uiState.results.isNotEmpty()

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null && !hasResults -> {
                ErrorView(
                    message = "搜尋失敗，請重試",
                    onRetry = { doSearch() }
                )
            }

            !hasResults && uiState.query.isBlank() -> {
                if (searchHistory.isNotEmpty()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("搜索歷史", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (isDeletingHistory) {
                                Row {
                                    TextButton(onClick = {
                                        viewModel.clearHistory()
                                        isDeletingHistory = false
                                    }) {
                                        Text("全部刪除", fontSize = 12.sp)
                                    }
                                    TextButton(onClick = { isDeletingHistory = false }) {
                                        Text("完成", fontSize = 12.sp)
                                    }
                                }
                            } else {
                                IconButton(onClick = { isDeletingHistory = true }) {
                                    Icon(
                                        painterResource(R.drawable.delete_24px),
                                        contentDescription = "刪除",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            searchHistory.forEach { query ->
                                if (isDeletingHistory) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(query, fontSize = 12.sp)
                                                Icon(
                                                    painterResource(R.drawable.close_24px),
                                                    contentDescription = "刪除",
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable { viewModel.removeHistoryItem(query) },
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    )
                                } else {
                                    SuggestionChip(
                                        onClick = {
                                            searchInput = query
                                            focusManager.clearFocus()
                                            viewModel.search(query, uiState.searchType)
                                        },
                                        label = { Text(query, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("輸入關鍵詞開始搜尋", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            !hasResults -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("沒有找到相關結果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            isActress -> ActressGrid(
                actresses = uiState.actressResults,
                hasMore = uiState.hasMore,
                isLoadingMore = uiState.isLoadingMore,
                onLoadMore = { viewModel.loadMore() },
                onActressClick = onActressClick,
                modifier = dismissKeyboardModifier
            )

            else -> MovieList(
                movies = uiState.results,
                hasMore = uiState.hasMore,
                isLoadingMore = uiState.isLoadingMore,
                onLoadMore = { viewModel.loadMore() },
                onMovieClick = onMovieClick,
                modifier = dismissKeyboardModifier
            )
        }
    }
}
