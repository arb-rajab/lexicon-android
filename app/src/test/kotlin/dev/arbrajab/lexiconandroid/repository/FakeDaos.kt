package dev.arbrajab.lexiconandroid.repository

import dev.arbrajab.lexiconandroid.data.local.dao.CorpusDao
import dev.arbrajab.lexiconandroid.data.local.dao.DocumentDao
import dev.arbrajab.lexiconandroid.data.local.dao.PendingQueryDao
import dev.arbrajab.lexiconandroid.data.local.dao.QueryResultDao
import dev.arbrajab.lexiconandroid.data.local.entity.CorpusEntity
import dev.arbrajab.lexiconandroid.data.local.entity.DocumentEntity
import dev.arbrajab.lexiconandroid.data.local.entity.PendingQueryEntity
import dev.arbrajab.lexiconandroid.data.local.entity.QueryResultEntity
import dev.arbrajab.lexiconandroid.data.local.entity.QuerySyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** In-memory fakes standing in for Room — fast, no Robolectric/SQLite needed for pure logic tests. */

class FakeCorpusDao : CorpusDao {
    private val flow = MutableStateFlow<List<CorpusEntity>>(emptyList())

    override fun observeAll(): StateFlow<List<CorpusEntity>> = flow

    override suspend fun get(id: String): CorpusEntity? = flow.value.firstOrNull { it.id == id }

    override suspend fun upsertAll(corpora: List<CorpusEntity>) {
        val byId = flow.value.associateBy { it.id }.toMutableMap()
        corpora.forEach { byId[it.id] = it }
        flow.value = byId.values.toList()
    }

    override suspend fun upsert(corpus: CorpusEntity) {
        upsertAll(listOf(corpus))
    }
}

class FakeDocumentDao : DocumentDao {
    private val flows = mutableMapOf<String, MutableStateFlow<List<DocumentEntity>>>()

    private fun flowFor(corpusId: String) = flows.getOrPut(corpusId) { MutableStateFlow(emptyList()) }

    override fun observeForCorpus(corpusId: String): StateFlow<List<DocumentEntity>> = flowFor(corpusId)

    override suspend fun getForCorpus(corpusId: String): List<DocumentEntity> = flowFor(corpusId).value

    override suspend fun upsertAll(documents: List<DocumentEntity>) {
        documents.groupBy { it.corpusId }.forEach { (corpusId, docs) ->
            val existing = flowFor(corpusId).value.associateBy { it.id }.toMutableMap()
            docs.forEach { existing[it.id] = it }
            flowFor(corpusId).value = existing.values.toList()
        }
    }

    override suspend fun deleteForCorpus(corpusId: String) {
        flowFor(corpusId).value = emptyList()
    }
}

class FakeQueryResultDao : QueryResultDao {
    private val flows = mutableMapOf<String, MutableStateFlow<List<QueryResultEntity>>>()

    private fun flowFor(corpusId: String) = flows.getOrPut(corpusId) { MutableStateFlow(emptyList()) }

    override fun observeForCorpus(corpusId: String): StateFlow<List<QueryResultEntity>> = flowFor(corpusId)

    override suspend fun upsert(result: QueryResultEntity) {
        val existing = flowFor(result.corpusId).value.associateBy { it.id }.toMutableMap()
        existing[result.id] = result
        flowFor(result.corpusId).value = existing.values.sortedByDescending { it.createdAt }
    }

    override suspend fun getUnsyncedForCorpus(corpusId: String): List<QueryResultEntity> =
        flowFor(corpusId).value.filter { it.syncState != QuerySyncState.SYNCED }

    override suspend fun markStaleWhereFingerprintDiffers(corpusId: String, currentFingerprint: String) {
        flowFor(corpusId).value =
            flowFor(corpusId).value.map {
                if (it.syncState == QuerySyncState.SYNCED && it.corpusFingerprint != currentFingerprint) {
                    it.copy(possiblyStale = true)
                } else {
                    it
                }
            }
    }

    override suspend fun deleteById(id: String) {
        flows.keys.forEach { corpusId ->
            flowFor(corpusId).value = flowFor(corpusId).value.filterNot { it.id == id }
        }
    }
}

class FakePendingQueryDao : PendingQueryDao {
    private val flow = MutableStateFlow<List<PendingQueryEntity>>(emptyList())

    override fun observeAll(): StateFlow<List<PendingQueryEntity>> = flow

    override suspend fun getAll(): List<PendingQueryEntity> = flow.value

    override suspend fun insert(pending: PendingQueryEntity) {
        flow.value = flow.value + pending
    }

    override suspend fun update(pending: PendingQueryEntity) {
        flow.value = flow.value.map { if (it.localId == pending.localId) pending else it }
    }

    override suspend fun delete(pending: PendingQueryEntity) {
        flow.value = flow.value.filterNot { it.localId == pending.localId }
    }

    override fun observeCount(): Flow<Int> = flow.map { it.size }
}
