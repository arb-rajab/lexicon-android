package dev.arbrajab.lexiconandroid.ui.serverconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * lexicon v1 has no login of its own (see ADR-0001 / ServerConfigStore) so
 * this screen collects a server URL and an optional static header instead of
 * a username/password form.
 */
@Composable
fun ServerConfigScreen(viewModel: ServerConfigViewModel, onContinue: () -> Unit) {
    val config by viewModel.uiState.collectAsState()
    val saved by viewModel.saved.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Connect to lexicon") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "lexicon has no built-in login in v1 — enter your deployment's URL and, " +
                    "if your operator requires one, an auth header.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = config.baseUrl,
                onValueChange = viewModel::onBaseUrlChanged,
                label = { Text("Server URL") },
                placeholder = { Text("https://lexicon.example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = config.authHeaderName,
                onValueChange = viewModel::onHeaderNameChanged,
                label = { Text("Auth header name (optional)") },
                placeholder = { Text("Authorization") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = config.authHeaderValue,
                onValueChange = viewModel::onHeaderValueChanged,
                label = { Text("Auth header value (optional)") },
                placeholder = { Text("Bearer …") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = {
                    viewModel.save()
                    onContinue()
                },
                enabled = config.baseUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saved) "Saved — continue" else "Save and continue")
            }
        }
    }
}
