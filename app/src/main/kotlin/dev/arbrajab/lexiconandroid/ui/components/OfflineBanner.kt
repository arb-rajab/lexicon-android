package dev.arbrajab.lexiconandroid.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.arbrajab.lexiconandroid.connectivity.ConnectivityState

/**
 * A persistent, honest online/offline/syncing indicator — never silently
 * degraded functionality (see project brief's "clear, honest UI indication"
 * requirement).
 */
@Composable
fun ConnectivityBanner(
    connectivityState: ConnectivityState,
    pendingCount: Int,
    modifier: Modifier = Modifier,
) {
    val visible = connectivityState == ConnectivityState.OFFLINE || pendingCount > 0
    AnimatedVisibility(visible = visible, modifier = modifier) {
        val (background, label) =
            when {
                connectivityState == ConnectivityState.OFFLINE && pendingCount > 0 ->
                    MaterialTheme.colorScheme.errorContainer to
                        "Offline — $pendingCount ${if (pendingCount == 1) "query" else "queries"} queued"
                connectivityState == ConnectivityState.OFFLINE ->
                    MaterialTheme.colorScheme.errorContainer to "Offline — showing cached data"
                pendingCount > 0 ->
                    MaterialTheme.colorScheme.tertiaryContainer to
                        "Syncing $pendingCount queued ${if (pendingCount == 1) "query" else "queries"}…"
                else -> MaterialTheme.colorScheme.surface to ""
            }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(background)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
