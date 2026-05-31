package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.jbusdriver.modern.data.db.MovieDBType

/**
 * 收藏筛选 Bottom Sheet。
 *
 * 首行：重置按钮（左，仅在有筛选条件时显示）+ 排序下拉（右）。
 * 主区域：筛选 Chip，点击即时生效。
 *
 * @param dbType 当前列表类型（MovieDBType 或 ActressDBType），决定显示哪些筛选维度
 * @param filterState 当前筛选/排序状态
 * @param availableYears 从数据中提取的可用年份
 * @param onFilterChange 筛选变更回调（即时生效）
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CollectionFilterSheet(
    dbType: Int,
    filterState: CollectionFilterState,
    availableYears: AvailableYears,
    onFilterChange: (CollectionFilterState) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Top row: Reset + Sort dropdown ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (filterState.hasActiveFilters) {
                    TextButton(onClick = { onFilterChange(CollectionFilterState()) }) {
                        Text("重置")
                    }
                }
                Spacer(Modifier.weight(1f))
                SortDropdown(
                    dbType = dbType,
                    current = filterState.sortOption,
                    onSelect = { onFilterChange(filterState.copy(sortOption = it)) }
                )
            }

            Spacer(Modifier.padding(top = 16.dp))

            // ── Censor filter (movie only) ──
            if (dbType == MovieDBType) {
                FilterSectionLabel("内容类型")
                Spacer(Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CensorChip(
                        label = "全部",
                        selected = filterState.censorFilter == CensorFilter.ALL,
                        onClick = { onFilterChange(filterState.copy(censorFilter = CensorFilter.ALL)) }
                    )
                    CensorChip(
                        label = "有碼",
                        selected = filterState.censorFilter == CensorFilter.CENSORED,
                        onClick = { onFilterChange(filterState.copy(censorFilter = CensorFilter.CENSORED)) }
                    )
                    CensorChip(
                        label = "無碼",
                        selected = filterState.censorFilter == CensorFilter.UNCENSORED,
                        onClick = { onFilterChange(filterState.copy(censorFilter = CensorFilter.UNCENSORED)) }
                    )
                }

                Spacer(Modifier.padding(top = 16.dp))

                // ── Publish date (movie only) ──
                FilterSectionLabel("发布日期")
                Spacer(Modifier.padding(top = 6.dp))
                YearChipRow(
                    selectedYear = filterState.publishYear,
                    years = availableYears.publishYears,
                    onSelect = { year ->
                        onFilterChange(filterState.copy(publishYear = year, publishMonth = null))
                    }
                )

                // Month row: only show when a specific year is selected (not "全部" or "更早")
                if (filterState.publishYear != null && filterState.publishYear > 0) {
                    Spacer(Modifier.padding(top = 8.dp))
                    MonthChipRow(
                        selectedMonth = filterState.publishMonth,
                        onSelect = { month ->
                            onFilterChange(filterState.copy(publishMonth = month))
                        }
                    )
                }

                Spacer(Modifier.padding(top = 16.dp))
            }

            // ── Collect time (both) ──
            FilterSectionLabel("收藏时间")
            Spacer(Modifier.padding(top = 6.dp))
            YearChipRow(
                selectedYear = filterState.collectYear,
                years = availableYears.collectYears,
                onSelect = { year ->
                    onFilterChange(filterState.copy(collectYear = year))
                }
            )

            Spacer(Modifier.padding(top = 32.dp))
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
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 14.sp) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YearChipRow(
    selectedYear: Int?,
    years: List<Int>,
    onSelect: (Int?) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selectedYear == null,
            onClick = { onSelect(null) },
            label = { Text("全部", fontSize = 13.sp) }
        )
        years.forEach { year ->
            FilterChip(
                selected = selectedYear == year,
                onClick = { onSelect(year) },
                label = { Text(year.toString(), fontSize = 13.sp) }
            )
        }
        if (years.isNotEmpty()) {
            FilterChip(
                selected = selectedYear == -1,
                onClick = { onSelect(-1) },
                label = { Text("更早", fontSize = 13.sp) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthChipRow(
    selectedMonth: Int?,
    onSelect: (Int?) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = selectedMonth == null,
            onClick = { onSelect(null) },
            label = { Text("全部", fontSize = 12.sp) }
        )
        (1..12).forEach { month ->
            FilterChip(
                selected = selectedMonth == month,
                onClick = { onSelect(month) },
                label = { Text("${month}月", fontSize = 12.sp) }
            )
        }
    }
}

@Composable
private fun SortDropdown(
    dbType: Int,
    current: SortOption,
    onSelect: (SortOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = if (dbType == MovieDBType) SortOption.movieOptions else SortOption.actressOptions

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(current.label, fontSize = 13.sp)
            Spacer(Modifier.width(2.dp))
            Text("▾", fontSize = 10.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, fontSize = 14.sp) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

// endregion
