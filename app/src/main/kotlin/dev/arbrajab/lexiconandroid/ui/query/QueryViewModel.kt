package dev.arbrajab.lexiconandroid.ui.query

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.arbrajab.lexiconandroid.connectivity.ConnectivityObserver
import dev.arbrajab.lexiconandroid.connectivity.ConnectivityState
import dev.arbrajab.lexiconandroid.data.local.entity.DocumentEntity
import dev.arbrajab.lexiconandroid.data.local.entity.QueryResultEntity
import dev.arbrajab.lexiconandroid.data.repository.CorpusRepository
import dev.arbrajab.lexiconandroid.data.repository.QueryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QueryScreenUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val results: List<QueryResultEntity> = emptyList(),
    val connectivity: ConnectivityState = ConnectivityState.OFFLINE,
    val pendingCount: Int = 0,
    val questionInput: String = "",
    val isSubmitting: Boolean = false,
    val isRefreshing: Boolean = false
)

class QueryViewModel(
    private val corpusId: String,
    private val corpusRepository: CorpusRepository,
    private val queryRepository: QueryRepository,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {
    private val _questionInput = MutableStateFlow("")
    val questionInput: StateFlow<String> = _questionInput.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val uiState: StateFlow<QueryScreenUiState> =
        combine(
            corpusRepository.observeDocuments(corpusId),
            queryRepository.observeResults(corpusId),
            connectivityObserver.observe(),
            queryRepository.observePendingCount(),
            _questionInput
        ) { documents, results, connectivity, pendingCount, question ->
            QueryScreenUiState(
                documents = documents,
                results = results,
                connectivity = connectivity,
                pendingCount = pendingCount,
                questionInput = question
            )
        }.combine(_isRefreshing) { state, isRefreshing -> state.copy(isRefreshing = isRefreshing) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QueryScreenUiState())

    fun onQuestionChanged(value: String) {
        _questionInput.value = value
    }

    /** Reuses [CorpusRepository.refresh]'s corpus-scoped path — no parallel refresh logic. */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                corpusRepository.refresh(corpusId)
            } catch (_: Exception) {
                // Cached documents (already flowing from Room) stay visible even if this
                // refresh fails, matching CorpusListViewModel's offline-first behavior.
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun retryFailed(result: QueryResultEntity) {
        viewModelScope.launch { queryRepository.retryFailed(result) }
    }

    fun submit() {
        val question = _questionInput.value.trim()
        if (question.isEmpty() || _isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                queryRepository.submitQuery(corpusId, question)
                _questionInput.value = ""
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    class Factory(
        private val corpusId: String,
        private val corpusRepository: CorpusRepository,
        private val queryRepository: QueryRepository,
        private val connectivityObserver: ConnectivityObserver
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            QueryViewModel(corpusId, corpusRepository, queryRepository, connectivityObserver) as T
    }
}
