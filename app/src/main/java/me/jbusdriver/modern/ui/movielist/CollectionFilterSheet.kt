package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.jbusdriver.R
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.ui.components.DropdownButton

/**
 * 收藏篩選 Bottom Sheet。
 *
 * 佈局參考 CategoryBottomSheet：高度限制 0.618f，標題居左，
 * 右上角為重置 + 排序雙下拉（內容/順序拆分，兩按鈕相鄰）。
 * 篩選 Chip 點擊即時生效。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CollectionFilterSheet(
    dbType: Int,
    filterState: CollectionFilterState,
    availableYears: AvailableYears,
    availablePublishMonths: Set<Int>,
    availableCollectMonths: Set<Int>,
    hasLocalVideoFolder: Boolean,
    onFilterChange: (CollectionFilterState) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 演員 tab 僅支持收藏時間排序；當前 sortOption 超出可用範圍時按 COLLECT_DESC 展示
    // （與 CollectionListViewModel 的 comparator 回退一致）。
    val sortOptions = if (dbType == MovieDBType) SortOption.movieOptions else SortOption.actressOptions
    val effectiveSort = filterState.sortOption.takeIf { it in sortOptions } ?: sortOptions.first()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.618f)
        ) {
            // ── Header: title (left) + reset + sort field/direction (right) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.filter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (filterState.hasActiveFilters) {
                        TextButton(onClick = {
                            // 重置只清筛选条件，保留当前排序设定
                            onFilterChange(CollectionFilterState(sortOption = filterState.sortOption))
                        }) {
                            Text(stringResource(R.string.reset), fontSize = 12.sp)
                        }
                    }
                    // 排序內容/順序拆成兩個相鄰下拉；field 選項從可用排序選項派生（單一真源）
                    DropdownButton(
                        options = sortOptions.map { it.field }.distinct(),
                        selected = effectiveSort.field,
                        label = { stringResource(it.labelRes) },
                        onSelect = { field ->
                            onFilterChange(
                                filterState.copy(sortOption = SortOption.of(field, effectiveSort.direction))
                            )
                        }
                    )
                    DropdownButton(
                        options = SortDirection.entries.toList(),
                        selected = effectiveSort.direction,
                        label = { stringResource(it.labelRes) },
                        onSelect = { direction ->
                            onFilterChange(
                                filterState.copy(sortOption = SortOption.of(effectiveSort.field, direction))
                            )
                        }
                    )
                }
            }

            // ── Scrollable content ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                // ── Censor filter (movie + actress) ──
                FilterSectionLabel(stringResource(R.string.content_type))
                Spacer(Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CensorChip(
                        label = stringResource(R.string.all),
                        selected = filterState.censorFilter == CensorFilter.ALL,
                        onClick = { onFilterChange(filterState.copy(censorFilter = CensorFilter.ALL)) }
                    )
                    CensorChip(
                        label = stringResource(R.string.censored),
                        selected = filterState.censorFilter == CensorFilter.CENSORED,
                        onClick = { onFilterChange(filterState.copy(censorFilter = CensorFilter.CENSORED)) }
                    )
                    CensorChip(
                        label = stringResource(R.string.uncensored),
                        selected = filterState.censorFilter == CensorFilter.UNCENSORED,
                        onClick = { onFilterChange(filterState.copy(censorFilter = CensorFilter.UNCENSORED)) }
                    )
                }

                if (dbType == MovieDBType) {
                    Spacer(Modifier.padding(top = 16.dp))

                    // ── Publish date (movie only) ──
                    FilterSectionLabel(stringResource(R.string.release_date))
                    Spacer(Modifier.padding(top = 6.dp))
                    YearChipRow(
                        selectedYear = filterState.publishYear,
                        years = availableYears.publishYears,
                        onSelect = { year ->
                            onFilterChange(
                                filterState.copy(
                                    publishYear = year,
                                    publishMonth = null
                                )
                            )
                        }
                    )

                    // Month row: only show when a specific year is selected
                    if (filterState.publishYear != null && filterState.publishYear > 0) {
                        Spacer(Modifier.padding(top = 8.dp))
                        MonthChipRow(
                            selectedMonth = filterState.publishMonth,
                            availableMonths = availablePublishMonths,
                            onSelect = { month ->
                                if (month == null || month in availablePublishMonths) {
                                    onFilterChange(filterState.copy(publishMonth = month))
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.padding(top = 16.dp))

                // ── Collect time (both) ──
                FilterSectionLabel(stringResource(R.string.collect_time))
                Spacer(Modifier.padding(top = 6.dp))
                YearChipRow(
                    selectedYear = filterState.collectYear,
                    years = availableYears.collectYears,
                    onSelect = { year ->
                        onFilterChange(filterState.copy(collectYear = year, collectMonth = null))
                    }
                )

                if (filterState.collectYear != null && filterState.collectYear > 0) {
                    Spacer(Modifier.padding(top = 8.dp))
                    MonthChipRow(
                        selectedMonth = filterState.collectMonth,
                        availableMonths = availableCollectMonths,
                        onSelect = { month ->
                            if (month == null || month in availableCollectMonths) {
                                onFilterChange(filterState.copy(collectMonth = month))
                            }
                        }
                    )
                }

                // ── Local video (movie only; shown only when a folder is configured) ──
                if (dbType == MovieDBType && hasLocalVideoFolder) {
                    Spacer(Modifier.padding(top = 16.dp))
                    FilterSectionLabel(stringResource(R.string.local_video))
                    Spacer(Modifier.padding(top = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = filterState.localVideoFilter == LocalVideoFilter.ALL,
                            onClick = {
                                onFilterChange(filterState.copy(localVideoFilter = LocalVideoFilter.ALL))
                            },
                            label = { Text(stringResource(R.string.all), fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = filterState.localVideoFilter == LocalVideoFilter.DOWNLOADED,
                            onClick = {
                                onFilterChange(filterState.copy(localVideoFilter = LocalVideoFilter.DOWNLOADED))
                            },
                            label = { Text(stringResource(R.string.local_video_downloaded), fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = filterState.localVideoFilter == LocalVideoFilter.NOT_DOWNLOADED,
                            onClick = {
                                onFilterChange(filterState.copy(localVideoFilter = LocalVideoFilter.NOT_DOWNLOADED))
                            },
                            label = { Text(stringResource(R.string.local_video_not_downloaded), fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
    }
}

// region Internal composables

@Composable
private fun FilterSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CensorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YearChipRow(
    selectedYear: Int?,
    years: List<Int>,
    onSelect: (Int?) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = selectedYear == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.all), fontSize = 12.sp) }
        )
        years.forEach { year ->
            FilterChip(
                selected = selectedYear == year,
                onClick = { onSelect(year) },
                label = { Text(year.toString(), fontSize = 12.sp) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthChipRow(
    selectedMonth: Int?,
    availableMonths: Set<Int>,
    onSelect: (Int?) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = selectedMonth == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.all), fontSize = 12.sp) }
        )
        (1..12).forEach { month ->
            val available = month in availableMonths
            FilterChip(
                selected = selectedMonth == month,
                onClick = { if (available) onSelect(month) },
                label = {
                    Text(
                        stringResource(R.string.month_format, month),
                        fontSize = 12.sp,
                        color = if (available) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                },
                enabled = available
            )
        }
    }
}

// endregion
