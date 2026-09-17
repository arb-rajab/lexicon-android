package dev.arbrajab.lexiconandroid.repository

import dev.arbrajab.lexiconandroid.connectivity.ConnectivityState
import dev.arbrajab.lexiconandroid.data.local.entity.CorpusEntity
import dev.arbrajab.lexiconandroid.data.local.entity.QuerySyncState
import dev.arbrajab.lexiconandroid.data.remote.dto.QueryResponseDto
import dev.arbrajab.lexiconandroid.data.repository.QueryRepository
import dev.arbrajab.lexiconandroid.data.repository.ReplayResult
import dev.arbrajab.lexiconandroid.data.repository.SubmitQueryOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QueryRepositoryTest {
    private lateinit var corpusDao: FakeCorpusDao
    private lateinit var documentDao: FakeDocumentDao
    private lateinit var queryResultDao: FakeQueryResultDao
    private lateinit var pendingQueryDao: FakePendingQueryDao
    private lateinit var connectivity: FakeConnectivityObserver
    private lateinit var api: FakeLexiconApi
    private lateinit var repository: QueryRepository
    private var queuedCallbackFired = false

    private val corpusId = "corpus-1"

    @Before
    fun setUp() {
        corpusDao = FakeCorpusDao()
        documentDao = FakeDocumentDao()
        queryResultDao = FakeQueryResultDao()
        pendingQueryDao = FakePendingQueryDao()
        connectivity = FakeConnectivityObserver(ConnectivityState.ONLINE)
        api = FakeLexiconApi()
        queuedCallbackFired = false
        repository =
            QueryRepository(
                queryResultDao = queryResultDao,
                pendingQueryDao = pendingQueryDao,
                corpusDao = corpusDao,
                documentDao = documentDao,
                connectivityObserver = connectivity,
                apiProvider = { api },
                onQueryQueued = { queuedCallbackFired = true }
            )
    }

    @Test
    fun `submitQuery online caches an answered result as SYNCED`() = runTest {
        api.askQuestionResult = { _, _ ->
            QueryResponseDto(
                queryLogId = "log-1",
                answered = true,
                answer = "Because of X.",
                citations = emptyList(),
                refusalReason = null,
                retrievedChunkCount = 2
            )
        }

        val outcome = repository.submitQuery(corpusId, "Why?")

        check(outcome is SubmitQueryOutcome.Answered)
        assertEquals(QuerySyncState.SYNCED, outcome.result.syncState)
        assertEquals("Because of X.", outcome.result.answerText)
        assertFalse(queuedCallbackFired)
    }

    @Test
    fun `submitQuery while offline queues a PENDING placeholder and notifies the sync trigger`() =
        runTest {
            connectivity.state.value = ConnectivityState.OFFLINE

            val outcome = repository.submitQuery(corpusId, "Why?")

            check(outcome is SubmitQueryOutcome.Queued)
            assertTrue(queuedCallbackFired)
            assertEquals(1, pendingQueryDao.getAll().size)
            val cached = queryResultDao.observeForCorpus(corpusId).value
            assertEquals(1, cached.size)
            assertEquals(QuerySyncState.PENDING, cached.first().syncState)
            assertEquals(0, api.askQuestionCallCount)
        }

    @Test
    fun `submitQuery online falls back to queueing when the live call throws IOException`() =
        runTest {
            api.askQuestionShouldFail = true

            val outcome = repository.submitQuery(corpusId, "Why?")

            check(outcome is SubmitQueryOutcome.Queued)
            assertEquals(1, pendingQueryDao.getAll().size)
        }

    @Test
    fun `replayPending on success answers the question and removes the pending entry`() = runTest {
        connectivity.state.value = ConnectivityState.OFFLINE
        val queued = repository.submitQuery(corpusId, "Why?") as SubmitQueryOutcome.Queued

        api.askQuestionResult = { _, _ ->
            QueryResponseDto(
                queryLogId = "log-2",
                answered = true,
                answer = "Resolved answer.",
                citations = emptyList(),
                refusalReason = null,
                retrievedChunkCount = 1
            )
        }

        val result = repository.replayPending(queued.pending)

        assertEquals(ReplayResult.SUCCESS, result)
        assertTrue(pendingQueryDao.getAll().isEmpty())
        val cached = queryResultDao.observeForCorpus(corpusId).value
        // The PENDING placeholder (keyed by localId) is replaced by the server-assigned row.
        assertEquals(1, cached.size)
        assertEquals(QuerySyncState.SYNCED, cached.first().syncState)
        assertEquals("Resolved answer.", cached.first().answerText)
    }

    @Test
    fun `replayPending on failure increments attempts and keeps the entry queued`() = runTest {
        connectivity.state.value = ConnectivityState.OFFLINE
        val queued = repository.submitQuery(corpusId, "Why?") as SubmitQueryOutcome.Queued
        api.askQuestionShouldFail = true

        val result = repository.replayPending(queued.pending)

        assertEquals(ReplayResult.RETRYING, result)
        val remaining = pendingQueryDao.getAll()
        assertEquals(1, remaining.size)
        assertEquals(1, remaining.first().attempts)
        val cached = queryResultDao.observeForCorpus(corpusId).value.first()
        assertEquals(QuerySyncState.PENDING, cached.syncState)
    }

    @Test
    fun `replayPending gives up after MAX_SYNC_ATTEMPTS, dequeuing and marking the result FAILED`() =
        runTest {
            connectivity.state.value = ConnectivityState.OFFLINE
            val queued = repository.submitQuery(corpusId, "Why?") as SubmitQueryOutcome.Queued
            api.askQuestionShouldFail = true

            var pending = queued.pending
            var lastResult: ReplayResult? = null
            repeat(QueryRepository.MAX_SYNC_ATTEMPTS) {
                lastResult = repository.replayPending(pending)
                pending = pendingQueryDao.getAll().firstOrNull() ?: pending
            }

            assertEquals(ReplayResult.PERMANENTLY_FAILED, lastResult)
            assertTrue("pending entry should be dequeued for good", pendingQueryDao.getAll().isEmpty())
            val cached = queryResultDao.observeForCorpus(corpusId).value
            assertEquals(1, cached.size)
            assertEquals(QuerySyncState.FAILED, cached.first().syncState)
        }

    @Test
    fun `a cached synced result is marked possibly stale once the corpus fingerprint changes`() =
        runTest {
            corpusDao.upsert(
                CorpusEntity(corpusId, "Corpus", "now", documentCount = 1, cachedAt = 0)
            )
            api.askQuestionResult = { _, _ ->
                QueryResponseDto("log-3", true, "Answer.", emptyList(), null, 1)
            }
            repository.submitQuery(corpusId, "Why?")

            // Corpus grows a second document — the fingerprint (documentCount:maxVersion) changes.
            corpusDao.upsert(
                CorpusEntity(corpusId, "Corpus", "now", documentCount = 2, cachedAt = 1)
            )
            queryResultDao.markStaleWhereFingerprintDiffers(corpusId, "2:0")

            val cached = queryResultDao.observeForCorpus(corpusId).value
            assertTrue(cached.first().possiblyStale)
        }
}
