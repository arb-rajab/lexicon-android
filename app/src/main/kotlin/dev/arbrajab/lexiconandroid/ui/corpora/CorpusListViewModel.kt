package dev.arbrajab.lexiconandroid.ui.corpora

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.arbrajab.lexiconandroid.connectivity.ConnectivityObserver
import dev.arbrajab.lexiconandroid.connectivity.ConnectivityState
import dev.arbrajab.lexiconandroid.data.local.entity.CorpusEntity
import dev.arbrajab.lexiconandroid.data.repository.CorpusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CorpusListUiState(
    val corpora: List<CorpusEntity> = emptyList(),
    val connectivity: ConnectivityState = ConnectivityState.OFFLINE,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

class CorpusListViewModel(
    private val repository: CorpusRepository,
    connectivityObserver: ConnectivityObserver
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val uiState: StateFlow<CorpusListUiState> =
        combine(
            repository.observeCorpora(),
            connectivityObserver.observe(),
            _isRefreshing,
            _error
        ) { corpora, connectivity, refreshing, error ->
            CorpusListUiState(corpora, connectivity, refreshing, error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CorpusListUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            try {
                repository.refresh()
            } catch (exc: Exception) {
                // Cached data (already flowing from Room) stays visible even if this
                // refresh fails — offline-first means a failed refresh degrades to
                // "showing what we have," never to a blank/error screen.
                _error.value = exc.message ?: "Couldn't refresh from server"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    class Factory(
        private val repository: CorpusRepository,
        private val connectivityObserver: ConnectivityObserver
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CorpusListViewModel(repository, connectivityObserver) as T
    }
}
