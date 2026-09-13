package dev.arbrajab.lexiconandroid.data.repository

import dev.arbrajab.lexiconandroid.data.local.dao.CorpusDao
import dev.arbrajab.lexiconandroid.data.local.dao.DocumentDao
import dev.arbrajab.lexiconandroid.data.local.dao.QueryResultDao
import dev.arbrajab.lexiconandroid.data.local.entity.CorpusEntity
import dev.arbrajab.lexiconandroid.data.local.entity.DocumentEntity
import dev.arbrajab.lexiconandroid.data.remote.LexiconApi
import kotlinx.coroutines.flow.Flow

/**
 * A fingerprint of a corpus's contents cheap enough to compute on every
 * refresh and compare against what a cached query result was answered
 * against. It changes exactly when the answer to a question against this
 * corpus could plausibly have changed: a document was added/removed
 * (`documentCount`) or re-ingested at a new version (`maxVersion`). See
 * ADR-0003 for why this — rather than deleting stale cache entries — is the
 * chosen staleness signal.
 */
fun computeCorpusFingerprint(documentCount: Int, documents: List<DocumentEntity>): String {
    val maxVersion = documents.maxOfOrNull { it.version } ?: 0
    return "$documentCount:$maxVersion"
}

class CorpusRepository(
    private val corpusDao: CorpusDao,
    private val documentDao: DocumentDao,
    private val queryResultDao: QueryResultDao,
    private val apiProvider: () -> LexiconApi
) {
    fun observeCorpora(): Flow<List<CorpusEntity>> = corpusDao.observeAll()

    fun observeDocuments(corpusId: String): Flow<List<DocumentEntity>> =
        documentDao.observeForCorpus(corpusId)

    /**
     * Refreshes cached corpus + document metadata from the server, then
     * re-evaluates staleness of any cached query results for [corpusId]
     * (or all corpora, if null) against the freshly computed fingerprint.
     */
    suspend fun refresh(corpusId: String? = null) {
        val api = apiProvider()
        val corpora = api.listCorpora()
        val now = System.currentTimeMillis()
        corpusDao.upsertAll(
            corpora.map {
                CorpusEntity(
                    id = it.id,
                    name = it.name,
                    createdAt = it.createdAt,
                    documentCount = 0,
                    cachedAt = now
                )
            }
        )

        val targets = if (corpusId != null) corpora.filter { it.id == corpusId } else corpora
        for (corpus in targets) {
            val detail = api.getCorpus(corpus.id)
            val documents = api.listDocuments(corpus.id)
            corpusDao.upsert(
                CorpusEntity(
                    id = detail.id,
                    name = detail.name,
                    createdAt = detail.createdAt,
                    documentCount = detail.documentCount,
                    cachedAt = now
                )
            )
            documentDao.deleteForCorpus(corpus.id)
            documentDao.upsertAll(
                documents.map {
                    DocumentEntity(
                        id = it.id,
                        corpusId = corpus.id,
                        sourceFilename = it.sourceFilename,
                        version = it.version,
                        status = it.status,
                        chunkCount = it.chunkCount,
                        cachedAt = now
                    )
                }
            )
            val fingerprint = computeCorpusFingerprint(
                detail.documentCount,
                documents.map {
                    DocumentEntity(
                        it.id,
                        corpus.id,
                        it.sourceFilename,
                        it.version,
                        it.status,
                        it.chunkCount,
                        now
                    )
                }
            )
            queryResultDao.markStaleWhereFingerprintDiffers(corpus.id, fingerprint)
        }
    }
}
