package me.jbusdriver.modern.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import me.jbusdriver.R

@Composable
fun MovieFilterBar(
    magnetCount: Int,
    totalCount: Int,
    showAll: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterSegment(
                text = stringResource(R.string.magnet_count, magnetCount),
                active = !showAll,
                onClick = { if (showAll) onToggle() },
                modifier = Modifier.weight(1f)
            )
            FilterSegment(
                text = stringResource(R.string.all_movies_count, totalCount),
                active = showAll,
                onClick = { if (!showAll) onToggle() },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FilterSegment(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
