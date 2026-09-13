package dev.arbrajab.lexiconandroid.repository

import dev.arbrajab.lexiconandroid.data.local.entity.DocumentEntity
import dev.arbrajab.lexiconandroid.data.remote.dto.CorpusDetailDto
import dev.arbrajab.lexiconandroid.data.remote.dto.CorpusDto
import dev.arbrajab.lexiconandroid.data.remote.dto.DocumentDto
import dev.arbrajab.lexiconandroid.data.repository.CorpusRepository
import dev.arbrajab.lexiconandroid.data.repository.computeCorpusFingerprint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CorpusRepositoryTest {
    @Test
    fun `fingerprint changes when document count changes`() {
        val docs = listOf(doc(version = 1))
        assertNotEquals(
            computeCorpusFingerprint(1, docs),
            computeCorpusFingerprint(2, docs),
        )
    }

    @Test
    fun `fingerprint changes when a document is re-ingested at a new version`() {
        val v1 = computeCorpusFingerprint(1, listOf(doc(version = 1)))
        val v2 = computeCorpusFingerprint(1, listOf(doc(version = 2)))
        assertNotEquals(v1, v2)
    }

    @Test
    fun `fingerprint is stable for the same document count and versions`() {
        val docs = listOf(doc(version = 1), doc(id = "d2", version = 3))
        assertEquals(
            computeCorpusFingerprint(2, docs),
            computeCorpusFingerprint(2, docs),
        )
    }

    @Test
    fun `refresh caches corpora and documents and evaluates staleness`() = runTest {
        val corpusDao = FakeCorpusDao()
        val documentDao = FakeDocumentDao()
        val queryResultDao = FakeQueryResultDao()
        val corpusId = "c1"
        val api =
            FakeLexiconApi(
                corpora = listOf(CorpusDto(corpusId, "Corpus 1", "now")),
                corpusDetails = mapOf(corpusId to CorpusDetailDto(corpusId, "Corpus 1", "now", documentCount = 1)),
                documents = mapOf(corpusId to listOf(DocumentDto("d1", "a.txt", version = 1, status = "ready", chunkCount = 5))),
            )
        val repository = CorpusRepository(corpusDao, documentDao, queryResultDao) { api }

        repository.refresh()

        assertEquals(1, corpusDao.observeAll().value.size)
        assertEquals(1, documentDao.getForCorpus(corpusId).size)
    }

    private fun doc(id: String = "d1", version: Int) =
        DocumentEntity(
            id = id,
            corpusId = "c1",
            sourceFilename = "file.txt",
            version = version,
            status = "ready",
            chunkCount = 1,
            cachedAt = 0,
        )
}
