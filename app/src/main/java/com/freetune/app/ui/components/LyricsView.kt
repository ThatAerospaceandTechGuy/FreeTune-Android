package com.freetune.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freetune.app.data.lyrics.Lyrics

/**
 * Shows the lyric lines around the current position. Synced files highlight the
 * line being sung; plain text files are shown in full.
 */
@Composable
fun LyricsView(
    lyrics: Lyrics,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    val lines = lyrics.lines
    if (lines.isEmpty()) return

    val current = lyrics.lineIndexAt(positionMs)
    val start = if (lyrics.synced) (current - 2).coerceAtLeast(0) else 0
    val end = if (lyrics.synced) (current + 4).coerceAtMost(lines.lastIndex) else lines.lastIndex

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (start > 0) {
            Text(
                text = "…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        for (index in start..end) {
            val line = lines[index]
            val isCurrent = lyrics.synced && index == current
            Text(
                text = line.text,
                style = if (isCurrent) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        if (end < lines.lastIndex) {
            Text(
                text = "…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
