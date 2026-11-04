package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.jbusdriver.R

/**
 * LTX 篩選 Bottom Sheet。
 *
 * 布局参考复用 [CollectionFilterSheet]：高度 0.618f，标题居左，重置+排序居右，
 * 筛选 chip 点击即时生效。包含：确认状态、补全状态两组筛选与排序下拉。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LtxFilterSheet(
    sortOption: LtxSortOption,
    statusFilter: LtxStatusFilter,
    resolveFilter: LtxResolveFilter,
    hasActiveFilters: Boolean,
    onSortChange: (LtxSortOption) -> Unit,
    onStatusFilterChange: (LtxStatusFilter) -> Unit,
    onResolveFilterChange: (LtxResolveFilter) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.618f)
        ) {
            // ── Header: title (left) + reset + sort (right) ──
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
                    if (hasActiveFilters) {
                        TextButton(onClick = onReset) {
                            Text(stringResource(R.string.reset), fontSize = 12.sp)
                        }
                    }
                    LtxSortDropdown(
                        current = sortOption,
                        onSelect = onSortChange
                    )
                }
            }

            // ── 确认状态 ──
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                FilterSectionLabel(stringResource(R.string.ltx_filter_confirm_label))
                Spacer(Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LtxFilterChip(
                        label = stringResource(LtxStatusFilter.ALL.labelRes),
                        selected = statusFilter == LtxStatusFilter.ALL,
                        onClick = { onStatusFilterChange(LtxStatusFilter.ALL) }
                    )
                    LtxFilterChip(
                        label = stringResource(LtxStatusFilter.CONFIRMED.labelRes),
                        selected = statusFilter == LtxStatusFilter.CONFIRMED,
                        onClick = { onStatusFilterChange(LtxStatusFilter.CONFIRMED) }
                    )
                    LtxFilterChip(
                        label = stringResource(LtxStatusFilter.PENDING.labelRes),
                        selected = statusFilter == LtxStatusFilter.PENDING,
                        onClick = { onStatusFilterChange(LtxStatusFilter.PENDING) }
                    )
                }
            }

            Spacer(Modifier.padding(top = 16.dp))

            // ── 补全状态 ──
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                FilterSectionLabel(stringResource(R.string.ltx_filter_resolve_label))
                Spacer(Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LtxFilterChip(
                        label = stringResource(LtxResolveFilter.ALL.labelRes),
                        selected = resolveFilter == LtxResolveFilter.ALL,
                        onClick = { onResolveFilterChange(LtxResolveFilter.ALL) }
                    )
                    LtxFilterChip(
                        label = stringResource(LtxResolveFilter.RESOLVED.labelRes),
                        selected = resolveFilter == LtxResolveFilter.RESOLVED,
                        onClick = { onResolveFilterChange(LtxResolveFilter.RESOLVED) }
                    )
                    LtxFilterChip(
                        label = stringResource(LtxResolveFilter.UNRESOLVED.labelRes),
                        selected = resolveFilter == LtxResolveFilter.UNRESOLVED,
                        onClick = { onResolveFilterChange(LtxResolveFilter.UNRESOLVED) }
                    )
                }
            }
        }
    }
}

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
private fun LtxFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) })
}

@Composable
private fun LtxSortDropdown(
    current: LtxSortOption,
    onSelect: (LtxSortOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
        ) {
            Text(stringResource(current.labelRes), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(2.dp))
            Text("▾", fontSize = 20.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LtxSortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes), fontSize = 14.sp) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}
