package dev.arbrajab.lexiconandroid.repository

import dev.arbrajab.lexiconandroid.connectivity.ConnectivityState
import dev.arbrajab.lexiconandroid.data.ServerConfig
import dev.arbrajab.lexiconandroid.data.local.entity.QuerySyncState
import dev.arbrajab.lexiconandroid.data.repository.QueryRepository
import dev.arbrajab.lexiconandroid.data.repository.ReplayResult
import dev.arbrajab.lexiconandroid.data.repository.SubmitQueryOutcome
import dev.arbrajab.lexiconandroid.di.ApiClientFactory
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression test for the crash this fixes: Retrofit throws [retrofit2.HttpException] (not
 * [java.io.IOException]) for any non-2xx response, and neither [QueryRepository.submitQuery]
 * nor [QueryRepository.replayPending] used to catch it — so a real 4xx/5xx from the server
 * propagated straight out instead of going through the documented graceful-retry/backoff-cap
 * model (ADR-0006). This wires the repository against a real Retrofit client (via
 * [ApiClientFactory]) talking to a [MockWebServer] rather than a fake API, so it exercises a
 * genuine [retrofit2.HttpException] the way production traffic would.
 */
class QueryRepositoryHttpExceptionTest {
    private lateinit var server: MockWebServer
    private lateinit var corpusDao: FakeCorpusDao
    private lateinit var documentDao: FakeDocumentDao
    private lateinit var queryResultDao: FakeQueryResultDao
    private lateinit var pendingQueryDao: FakePendingQueryDao
    private lateinit var connectivity: FakeConnectivityObserver
    private lateinit var repository: QueryRepository

    private val corpusId = "corpus-1"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        corpusDao = FakeCorpusDao()
        documentDao = FakeDocumentDao()
        queryResultDao = FakeQueryResultDao()
        pendingQueryDao = FakePendingQueryDao()
        connectivity = FakeConnectivityObserver(ConnectivityState.ONLINE)
        val api = ApiClientFactory.create(ServerConfig(baseUrl = server.url("/").toString()))
        repository =
            QueryRepository(
                queryResultDao = queryResultDao,
                pendingQueryDao = pendingQueryDao,
                corpusDao = corpusDao,
                documentDao = documentDao,
                connectivityObserver = connectivity,
                apiProvider = { api }
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `submitQuery does not crash on a real 4xx response and queues instead`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(422).setBody(
                """{"error":{"code":"invalid_question","message":"too short"}}"""
            )
        )

        val outcome = repository.submitQuery(corpusId, "?")

        check(outcome is SubmitQueryOutcome.Queued)
        assertEquals(1, pendingQueryDao.getAll().size)
        val cached = queryResultDao.observeForCorpus(corpusId).value
        assertEquals(QuerySyncState.PENDING, cached.first().syncState)
    }

    @Test
    fun `replayPending does not crash on a real 5xx response and keeps retrying`() = runTest {
        connectivity.state.value = ConnectivityState.OFFLINE
        val queued = repository.submitQuery(corpusId, "Why?") as SubmitQueryOutcome.Queued
        server.enqueue(MockResponse().setResponseCode(503).setBody("service unavailable"))

        val result = repository.replayPending(queued.pending)

        assertEquals(ReplayResult.RETRYING, result)
        val remaining = pendingQueryDao.getAll()
        assertEquals(1, remaining.size)
        assertEquals(1, remaining.first().attempts)
        val cached = queryResultDao.observeForCorpus(corpusId).value.first()
        assertEquals(QuerySyncState.PENDING, cached.syncState)
    }

    @Test
    fun `replayPending gives up after MAX_SYNC_ATTEMPTS of real 5xx responses`() = runTest {
        connectivity.state.value = ConnectivityState.OFFLINE
        val queued = repository.submitQuery(corpusId, "Why?") as SubmitQueryOutcome.Queued

        var pending = queued.pending
        var lastResult: ReplayResult? = null
        repeat(QueryRepository.MAX_SYNC_ATTEMPTS) {
            server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
            lastResult = repository.replayPending(pending)
            pending = pendingQueryDao.getAll().firstOrNull() ?: pending
        }

        assertEquals(ReplayResult.PERMANENTLY_FAILED, lastResult)
        assertTrue(
            "pending entry should be dequeued for good",
            pendingQueryDao.getAll().isEmpty()
        )
        val cached = queryResultDao.observeForCorpus(corpusId).value
        assertEquals(1, cached.size)
        assertEquals(QuerySyncState.FAILED, cached.first().syncState)
    }
}
