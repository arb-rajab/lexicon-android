package dev.arbrajab.lexiconandroid.data.repository

import dev.arbrajab.lexiconandroid.connectivity.ConnectivityObserver
import dev.arbrajab.lexiconandroid.connectivity.ConnectivityState
import dev.arbrajab.lexiconandroid.data.local.dao.CorpusDao
import dev.arbrajab.lexiconandroid.data.local.dao.DocumentDao
import dev.arbrajab.lexiconandroid.data.local.dao.PendingQueryDao
import dev.arbrajab.lexiconandroid.data.local.dao.QueryResultDao
import dev.arbrajab.lexiconandroid.data.local.entity.PendingQueryEntity
import dev.arbrajab.lexiconandroid.data.local.entity.QueryResultEntity
import dev.arbrajab.lexiconandroid.data.local.entity.QuerySyncState
import dev.arbrajab.lexiconandroid.data.remote.LexiconApi
import dev.arbrajab.lexiconandroid.data.remote.dto.CitationDto
import dev.arbrajab.lexiconandroid.data.remote.dto.QueryRequestDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

sealed interface SubmitQueryOutcome {
    data class Answered(val result: QueryResultEntity) : SubmitQueryOutcome
    data class Queued(val pending: PendingQueryEntity) : SubmitQueryOutcome
}

/**
 * Owns the query submit → cache → offline-queue → replay lifecycle described
 * in ADR-0003. This is the class the unit tests in `src/test` exercise most
 * heavily: it's the actual point of this app (see project brief), not
 * incidental plumbing.
 */
class QueryRepository(
    private val queryResultDao: QueryResultDao,
    private val pendingQueryDao: PendingQueryDao,
    private val corpusDao: CorpusDao,
    private val documentDao: DocumentDao,
    private val connectivityObserver: ConnectivityObserver,
    private val apiProvider: () -> LexiconApi,
    private val onQueryQueued: () -> Unit = {},
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun observeResults(corpusId: String): Flow<List<QueryResultEntity>> =
        queryResultDao.observeForCorpus(corpusId)

    fun observePendingCount(): Flow<Int> = pendingQueryDao.observeCount()

    /**
     * Submits a question. If the device is online, calls the API directly
     * and caches the answer. If offline (or the live call fails with a
     * network error), queues it as a [PendingQueryEntity] plus a placeholder
     * [QueryResultEntity] in PENDING state so the UI can show "queued"
     * immediately — `SyncQueueWorker` replays it later.
     */
    suspend fun submitQuery(corpusId: String, question: String): SubmitQueryOutcome {
        val isOnline = connectivityObserver.observe().first() == ConnectivityState.ONLINE
        if (isOnline) {
            try {
                return answerLive(corpusId, question)
            } catch (_: java.io.IOException) {
                // Network call itself failed (timeout, DNS, connection reset) despite the
                // transport reporting "online" — fall through to queueing rather than
                // surfacing a transient error for something the sync worker will retry.
            }
        }
        return queueOffline(corpusId, question)
    }

    private suspend fun answerLive(corpusId: String, question: String): SubmitQueryOutcome.Answered {
        val api = apiProvider()
        val response = api.askQuestion(corpusId, QueryRequestDto(question))
        val result = cacheAnsweredResult(corpusId, question, response.let {
            CachedAnswer(
                id = it.queryLogId,
                answered = it.answered,
                answerText = it.answer,
                refusalReason = it.refusalReason,
                retrievedChunkCount = it.retrievedChunkCount,
                citations = it.citations,
            )
        })
        return SubmitQueryOutcome.Answered(result)
    }

    private suspend fun queueOffline(corpusId: String, question: String): SubmitQueryOutcome.Queued {
        val now = System.currentTimeMillis()
        val localId = UUID.randomUUID().toString()
        val pending =
            PendingQueryEntity(
                localId = localId,
                corpusId = corpusId,
                questionText = question,
                createdAt = now,
            )
        pendingQueryDao.insert(pending)
        queryResultDao.upsert(
            QueryResultEntity(
                id = localId,
                corpusId = corpusId,
                questionText = question,
                answered = false,
                answerText = null,
                refusalReason = null,
                retrievedChunkCount = 0,
                citationsJson = "[]",
                createdAt = now,
                cachedAt = now,
                syncState = QuerySyncState.PENDING,
                possiblyStale = false,
                corpusFingerprint = currentFingerprint(corpusId),
            ),
        )
        onQueryQueued()
        return SubmitQueryOutcome.Queued(pending)
    }

    /** Replays one queued query. Returns true if it was successfully answered and dequeued. */
    suspend fun replayPending(pending: PendingQueryEntity): Boolean {
        val api = apiProvider()
        return try {
            val response = api.askQuestion(pending.corpusId, QueryRequestDto(pending.questionText))
            // Replace the PENDING placeholder (keyed by localId) with the real, server-assigned
            // query_log_id row, then drop the placeholder + dequeue — the server's answer always
            // wins over the optimistic placeholder (see ADR-0003, "queued queries never conflict").
            cacheAnsweredResult(
                pending.corpusId,
                pending.questionText,
                CachedAnswer(
                    id = response.queryLogId,
                    answered = response.answered,
                    answerText = response.answer,
                    refusalReason = response.refusalReason,
                    retrievedChunkCount = response.retrievedChunkCount,
                    citations = response.citations,
                ),
            )
            queryResultDao.deleteById(pending.localId)
            pendingQueryDao.delete(pending)
            true
        } catch (exc: java.io.IOException) {
            pendingQueryDao.update(
                pending.copy(attempts = pending.attempts + 1, lastError = exc.message ?: "network error"),
            )
            false
        }
    }

    private data class CachedAnswer(
        val id: String,
        val answered: Boolean,
        val answerText: String?,
        val refusalReason: String?,
        val retrievedChunkCount: Int,
        val citations: List<CitationDto>,
    )

    private suspend fun cacheAnsweredResult(
        corpusId: String,
        question: String,
        answer: CachedAnswer,
    ): QueryResultEntity {
        val now = System.currentTimeMillis()
        val result =
            QueryResultEntity(
                id = answer.id,
                corpusId = corpusId,
                questionText = question,
                answered = answer.answered,
                answerText = answer.answerText,
                refusalReason = answer.refusalReason,
                retrievedChunkCount = answer.retrievedChunkCount,
                citationsJson = json.encodeToString(answer.citations),
                createdAt = now,
                cachedAt = now,
                syncState = QuerySyncState.SYNCED,
                possiblyStale = false,
                corpusFingerprint = currentFingerprint(corpusId),
            )
        queryResultDao.upsert(result)
        return result
    }

    private suspend fun currentFingerprint(corpusId: String): String {
        val corpus = corpusDao.get(corpusId) ?: return ""
        val documents = documentDao.getForCorpus(corpusId)
        return computeCorpusFingerprint(corpus.documentCount, documents)
    }
}
