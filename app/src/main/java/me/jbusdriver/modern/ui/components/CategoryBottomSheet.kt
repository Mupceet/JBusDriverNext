package me.jbusdriver.modern.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.jbusdriver.R
import me.jbusdriver.modern.ui.GenreCategory
import me.jbusdriver.modern.ui.GenreUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBottomSheet(
    categories: List<GenreCategory>,
    selectedGenres: Set<GenreUiModel>,
    onSelectionChange: (Set<GenreUiModel>) -> Unit,
    modifier: Modifier = Modifier
) {
    var isMultiSelect by remember { mutableStateOf(false) }
    val expandedTitles = remember { mutableStateSetOf<String>() }

    Column(modifier = modifier.fillMaxWidth().fillMaxHeight(0.618f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.select_category), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                IconButton(
                    onClick = { expandedTitles.addAll(categories.map { it.title }) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.expand_all_24px),
                        contentDescription = stringResource(R.string.expand_all),
                        modifier = Modifier.graphicsLayer { rotationZ = 180f }.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { expandedTitles.clear() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.collapse_all_24px),
                        contentDescription = stringResource(R.string.collapse_all),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isMultiSelect && selectedGenres.isNotEmpty()) {
                    Text(
                        stringResource(R.string.selected_count, selectedGenres.size),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (selectedGenres.isNotEmpty()) {
                    TextButton(onClick = { onSelectionChange(emptySet()) }) {
                        Text(stringResource(R.string.reset), fontSize = 12.sp)
                    }
                }
//                Text("多選", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
//                Spacer(Modifier.width(4.dp))
//                Checkbox(
//                    checked = isMultiSelect,
//                    onCheckedChange = {
//                        isMultiSelect = it
//                        if (!it && selectedGenres.size > 1) {
//                            onSelectionChange(selectedGenres.lastOrNull()?.let { setOf(it) } ?: emptySet())
//                        }
//                    },
//                    modifier = Modifier.height(24.dp)
//                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(categories, key = { it.title }) { group ->
                val expanded = group.title in expandedTitles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (expanded) expandedTitles.remove(group.title)
                            else expandedTitles.add(group.title)
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        group.title,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${group.genres?.size ?: 0}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(R.drawable.arrow_circle_up_24px),
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { rotationZ = if (expanded) 180f else 90f },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (expanded) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    ) {
                        group.genres.orEmpty().forEach { genre ->
                            val isSelected = genre in selectedGenres
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val newSelection = if (isMultiSelect) {
                                        if (isSelected) selectedGenres - genre else selectedGenres + genre
                                    } else {
                                        if (isSelected) emptySet() else setOf(genre)
                                    }
                                    onSelectionChange(newSelection)
                                },
                                label = {
                                    Text(genre.name, fontSize = 12.sp)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
