package dev.arbrajab.lexiconandroid.ui.serverconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.arbrajab.lexiconandroid.data.ServerConfig
import dev.arbrajab.lexiconandroid.data.ServerConfigStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServerConfigViewModel(private val store: ServerConfigStore) : ViewModel() {
    private val _uiState = MutableStateFlow(ServerConfig())
    val uiState: StateFlow<ServerConfig> = _uiState.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = store.current()
        }
    }

    fun onBaseUrlChanged(value: String) {
        _uiState.value = _uiState.value.copy(baseUrl = value)
        _saved.value = false
    }

    fun onHeaderNameChanged(value: String) {
        _uiState.value = _uiState.value.copy(authHeaderName = value)
        _saved.value = false
    }

    fun onHeaderValueChanged(value: String) {
        _uiState.value = _uiState.value.copy(authHeaderValue = value)
        _saved.value = false
    }

    fun save() {
        viewModelScope.launch {
            store.save(_uiState.value)
            _saved.value = true
        }
    }

    class Factory(private val store: ServerConfigStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ServerConfigViewModel(store) as T
    }
}
