package dev.arbrajab.lexiconandroid.ui.corpora

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.arbrajab.lexiconandroid.ui.components.ConnectivityBanner

@Composable
fun CorpusListScreen(
    viewModel: CorpusListViewModel,
    pendingCount: Int,
    onOpenCorpus: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your corpora") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Text("⚙")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ConnectivityBanner(connectivityState = state.connectivity, pendingCount = pendingCount)
            if (state.error != null) {
                Text(
                    "Couldn't refresh: ${state.error}. Showing cached results.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.corpora.isEmpty()) {
                Text(
                    if (state.isRefreshing) "Loading…" else "No corpora cached yet.",
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn {
                items(state.corpora, key = { it.id }) { corpus ->
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { onOpenCorpus(corpus.id) },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(corpus.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${corpus.documentCount} document(s)",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
