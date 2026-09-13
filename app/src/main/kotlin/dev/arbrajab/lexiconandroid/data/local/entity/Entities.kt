package dev.arbrajab.lexiconandroid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A cached snapshot of a corpus, refreshed whenever the app talks to the server. */
@Entity(tableName = "corpora")
data class CorpusEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: String,
    val documentCount: Int,
    val cachedAt: Long,
)

/** A cached snapshot of a document's ingestion metadata (not its content — see ADR-0003). */
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val corpusId: String,
    val sourceFilename: String,
    val version: Int,
    val status: String,
    val chunkCount: Int,
    val cachedAt: Long,
)

enum class QuerySyncState { SYNCED, PENDING, FAILED }

/**
 * A query result, either fetched live or reconstructed from a completed sync.
 * Citations are stored denormalized as JSON since they're only ever read back
 * alongside their parent answer, never queried independently (ADR-0003).
 */
@Entity(tableName = "query_results")
data class QueryResultEntity(
    @PrimaryKey val id: String,
    val corpusId: String,
    val questionText: String,
    val answered: Boolean,
    val answerText: String?,
    val refusalReason: String?,
    val retrievedChunkCount: Int,
    val citationsJson: String,
    val createdAt: Long,
    val cachedAt: Long,
    val syncState: QuerySyncState,
    /** True if corpus/document metadata changed since this result was produced — see ADR-0003. */
    val possiblyStale: Boolean,
    /** documentCount + max(version) fingerprint of the corpus at answer time, for staleness checks. */
    val corpusFingerprint: String,
)

/**
 * The offline queue: one row per query submitted while offline (or while the
 * live call failed), waiting for `SyncQueueWorker` to replay it once
 * connectivity returns.
 */
@Entity(tableName = "pending_queries")
data class PendingQueryEntity(
    @PrimaryKey val localId: String,
    val corpusId: String,
    val questionText: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)
