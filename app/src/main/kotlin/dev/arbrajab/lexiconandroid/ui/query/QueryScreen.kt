package dev.arbrajab.lexiconandroid.ui.query

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.arbrajab.lexiconandroid.data.local.entity.QueryResultEntity
import dev.arbrajab.lexiconandroid.data.local.entity.QuerySyncState
import dev.arbrajab.lexiconandroid.ui.components.ConnectivityBanner

@Composable
fun QueryScreen(viewModel: QueryViewModel, corpusName: String, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text(corpusName) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ConnectivityBanner(
                connectivityState = state.connectivity,
                pendingCount = state.pendingCount
            )
            Text(
                "${state.documents.size} document(s) cached locally",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyColumn(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
                items(state.results, key = { it.id }) { result -> QueryResultCard(result) }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedTextField(
                    value = state.questionInput,
                    onValueChange = viewModel::onQuestionChanged,
                    label = { Text("Ask a question") },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = viewModel::submit,
                    enabled = !isSubmitting && state.questionInput.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    } else {
                        Text("Ask")
                    }
                }
            }
        }
    }
}

@Composable
private fun QueryResultCard(result: QueryResultEntity) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(result.questionText, style = MaterialTheme.typography.titleSmall)
            when (result.syncState) {
                QuerySyncState.PENDING ->
                    Text(
                        "Queued — will answer when back online",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                QuerySyncState.FAILED ->
                    Text(
                        "Failed to sync — will retry automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                QuerySyncState.SYNCED -> {
                    if (result.possiblyStale) {
                        Text(
                            "This corpus changed since this answer was generated — may be outdated",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = result.answerText
                            ?: refusalMessage(result.refusalReason),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private fun refusalMessage(reason: String?): String = when (reason) {
    "self_refused" -> "The model declined to answer from the retrieved documents."
    "verification_failed" ->
        "An answer was generated but failed citation verification, so it was withheld."
    "no_candidates_retrieved" -> "No relevant documents were found for this question."
    else -> "No answer available."
}
