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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import me.jbusdriver.R
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.jbusdriver.modern.ui.settings.UiPrefsViewModel
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
    modifier: Modifier = Modifier,
    defaultSearchType: String = "",
    onMovieClick: (MovieUiModel, String?) -> Unit = { _, _ -> },
    onActressClick: (ActressUiModel, String?) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    onLabSettingsClick: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val censorType = when (uiState.searchType) {
        SearchType.UNCENSORED -> "UNCENSORED"
        else -> null
    }
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val isGrid by hiltViewModel<UiPrefsViewModel>().store.isGrid.collectAsStateWithLifecycle()
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
                    Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = stringResource(R.string.back))
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
                            contentDescription = stringResource(R.string.clear)
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

        // Lab entry card — appears when searchInput matches keywords
        val showLabEntry = searchInput.trim().lowercase().let { q ->
            q == "setting" || q == "settings" ||
                    q == "设置" || q == "实验室" || q == "论坛" ||
                    q == "設置" || q == "實驗室" || q == "論壇"
        }
        if (showLabEntry) {
            Card(
                onClick = {
                    focusManager.clearFocus()
                    onLabSettingsClick()
                },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painterResource(R.drawable.science_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            stringResource(R.string.lab_settings),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.lab_experimental_settings),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                    message = stringResource(R.string.search_failed),
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
                            Text(stringResource(R.string.search_history), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (isDeletingHistory) {
                                Row {
                                    TextButton(onClick = {
                                        viewModel.clearHistory()
                                        isDeletingHistory = false
                                    }) {
                                        Text(stringResource(R.string.delete_all), fontSize = 12.sp)
                                    }
                                    TextButton(onClick = { isDeletingHistory = false }) {
                                        Text(stringResource(R.string.done), fontSize = 12.sp)
                                    }
                                }
                            } else {
                                IconButton(onClick = { isDeletingHistory = true }) {
                                    Icon(
                                        painterResource(R.drawable.delete_24px),
                                        contentDescription = stringResource(R.string.delete),
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
                                                    contentDescription = stringResource(R.string.delete),
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
                        Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            !hasResults -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            isActress -> ActressGrid(
                actresses = uiState.actressResults,
                hasMore = uiState.hasMore,
                isLoadingMore = uiState.isLoadingMore,
                onLoadMore = { viewModel.loadMore() },
                onActressClick = { actress, _ -> onActressClick(actress, censorType) },
                modifier = dismissKeyboardModifier
            )

            else -> MovieList(
                movies = uiState.results,
                hasMore = uiState.hasMore,
                isLoadingMore = uiState.isLoadingMore,
                onLoadMore = { viewModel.loadMore() },
                onMovieClick = { movie, _ -> onMovieClick(movie, censorType) },
                isGrid = isGrid,
                modifier = dismissKeyboardModifier
            )
        }
    }
}
