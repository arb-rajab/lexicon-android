package dev.arbrajab.lexiconandroid.repository

import dev.arbrajab.lexiconandroid.connectivity.ConnectivityObserver
import dev.arbrajab.lexiconandroid.connectivity.ConnectivityState
import dev.arbrajab.lexiconandroid.data.remote.LexiconApi
import dev.arbrajab.lexiconandroid.data.remote.dto.CorpusCreateRequest
import dev.arbrajab.lexiconandroid.data.remote.dto.CorpusDetailDto
import dev.arbrajab.lexiconandroid.data.remote.dto.CorpusDto
import dev.arbrajab.lexiconandroid.data.remote.dto.DocumentDto
import dev.arbrajab.lexiconandroid.data.remote.dto.QueryRequestDto
import dev.arbrajab.lexiconandroid.data.remote.dto.QueryResponseDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

class FakeConnectivityObserver(initial: ConnectivityState) : ConnectivityObserver {
    val state = MutableStateFlow(initial)

    override fun observe(): StateFlow<ConnectivityState> = state
}

/**
 * A fake [LexiconApi] whose [askQuestion] behavior is driven per-test: return a
 * canned response, or throw [IOException] to simulate the live call failing
 * despite the transport reporting online (the case [QueryRepository.submitQuery]
 * falls back to queueing for).
 */
class FakeLexiconApi(
    var corpora: List<CorpusDto> = emptyList(),
    var corpusDetails: Map<String, CorpusDetailDto> = emptyMap(),
    var documents: Map<String, List<DocumentDto>> = emptyMap(),
    var askQuestionResult: (corpusId: String, question: String) -> QueryResponseDto = { _, _ ->
        error("askQuestionResult not configured")
    },
    var askQuestionShouldFail: Boolean = false,
) : LexiconApi {
    var askQuestionCallCount = 0
        private set

    override suspend fun listCorpora(): List<CorpusDto> = corpora

    override suspend fun createCorpus(request: CorpusCreateRequest): CorpusDto =
        error("not used in these tests")

    override suspend fun getCorpus(corpusId: String): CorpusDetailDto =
        corpusDetails.getValue(corpusId)

    override suspend fun listDocuments(corpusId: String): List<DocumentDto> =
        documents[corpusId].orEmpty()

    override suspend fun askQuestion(corpusId: String, request: QueryRequestDto): QueryResponseDto {
        askQuestionCallCount++
        if (askQuestionShouldFail) throw IOException("simulated network failure")
        return askQuestionResult(corpusId, request.question)
    }
}
