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
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface SubmitQueryOutcome {
    data class Answered(val result: QueryResultEntity) : SubmitQueryOutcome
    data class Queued(val pending: PendingQueryEntity) : SubmitQueryOutcome
}

/** Outcome of one [QueryRepository.replayPending] attempt. */
enum class ReplayResult {
    /** The server answered; the pending entry was dequeued and the result cached as SYNCED. */
    SUCCESS,

    /** The call failed but attempts remain — the entry stays queued for a later retry. */
    RETRYING,

    /**
     * The call failed and [QueryRepository.MAX_SYNC_ATTEMPTS] was reached — the entry is
     * dequeued for good and the cached result is marked FAILED so the user sees it rather than
     * having it retry silently forever.
     */
    PERMANENTLY_FAILED
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
    private val json: Json = Json { ignoreUnknownKeys = true }
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

    private suspend fun answerLive(
        corpusId: String,
        question: String
    ): SubmitQueryOutcome.Answered {
        val api = apiProvider()
        val response = api.askQuestion(corpusId, QueryRequestDto(question))
        val result = cacheAnsweredResult(
            corpusId,
            question,
            response.let {
                CachedAnswer(
                    id = it.queryLogId,
                    answered = it.answered,
                    answerText = it.answer,
                    refusalReason = it.refusalReason,
                    retrievedChunkCount = it.retrievedChunkCount,
                    citations = it.citations
                )
            }
        )
        return SubmitQueryOutcome.Answered(result)
    }

    private suspend fun queueOffline(
        corpusId: String,
        question: String
    ): SubmitQueryOutcome.Queued {
        val now = System.currentTimeMillis()
        val localId = UUID.randomUUID().toString()
        val pending =
            PendingQueryEntity(
                localId = localId,
                corpusId = corpusId,
                questionText = question,
                createdAt = now
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
                corpusFingerprint = currentFingerprint(corpusId)
            )
        )
        onQueryQueued()
        return SubmitQueryOutcome.Queued(pending)
    }

    /** Replays one queued query. See [ReplayResult] for what each outcome means. */
    suspend fun replayPending(pending: PendingQueryEntity): ReplayResult {
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
                    citations = response.citations
                )
            )
            queryResultDao.deleteById(pending.localId)
            pendingQueryDao.delete(pending)
            ReplayResult.SUCCESS
        } catch (exc: java.io.IOException) {
            val attempts = pending.attempts + 1
            val lastError = exc.message ?: "network error"
            if (attempts >= MAX_SYNC_ATTEMPTS) {
                // Give up for good rather than retry silently forever (see backlog): dequeue and
                // surface the failure on the cached PENDING placeholder so the user can see and
                // re-ask it, instead of it looking "stuck" with no explanation.
                pendingQueryDao.delete(pending)
                queryResultDao.markFailed(pending.localId)
                ReplayResult.PERMANENTLY_FAILED
            } else {
                pendingQueryDao.update(pending.copy(attempts = attempts, lastError = lastError))
                ReplayResult.RETRYING
            }
        }
    }

    private data class CachedAnswer(
        val id: String,
        val answered: Boolean,
        val answerText: String?,
        val refusalReason: String?,
        val retrievedChunkCount: Int,
        val citations: List<CitationDto>
    )

    private suspend fun cacheAnsweredResult(
        corpusId: String,
        question: String,
        answer: CachedAnswer
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
                corpusFingerprint = currentFingerprint(corpusId)
            )
        queryResultDao.upsert(result)
        return result
    }

    private suspend fun currentFingerprint(corpusId: String): String {
        val corpus = corpusDao.get(corpusId) ?: return ""
        val documents = documentDao.getForCorpus(corpusId)
        return computeCorpusFingerprint(corpus.documentCount, documents)
    }

    companion object {
        /**
         * Cap on replay attempts for one queued query before it's given up on for good (see
         * [ReplayResult.PERMANENTLY_FAILED]) rather than retried forever. WorkManager itself
         * applies exponential backoff between drain passes (default: doubling from 10s, capped
         * at ~5h), so five attempts already spans a long window before the user sees a stuck
         * query surfaced as failed.
         */
        const val MAX_SYNC_ATTEMPTS = 5
    }
}
