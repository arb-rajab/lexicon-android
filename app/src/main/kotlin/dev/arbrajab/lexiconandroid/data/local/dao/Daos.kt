package dev.arbrajab.lexiconandroid.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.arbrajab.lexiconandroid.data.local.entity.CorpusEntity
import dev.arbrajab.lexiconandroid.data.local.entity.DocumentEntity
import dev.arbrajab.lexiconandroid.data.local.entity.PendingQueryEntity
import dev.arbrajab.lexiconandroid.data.local.entity.QueryResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CorpusDao {
    @Query("SELECT * FROM corpora ORDER BY name ASC")
    fun observeAll(): Flow<List<CorpusEntity>>

    @Query("SELECT * FROM corpora WHERE id = :id")
    suspend fun get(id: String): CorpusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(corpora: List<CorpusEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(corpus: CorpusEntity)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE corpusId = :corpusId ORDER BY sourceFilename ASC")
    fun observeForCorpus(corpusId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE corpusId = :corpusId")
    suspend fun getForCorpus(corpusId: String): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(documents: List<DocumentEntity>)

    @Query("DELETE FROM documents WHERE corpusId = :corpusId")
    suspend fun deleteForCorpus(corpusId: String)
}

@Dao
interface QueryResultDao {
    @Query("SELECT * FROM query_results WHERE corpusId = :corpusId ORDER BY createdAt DESC")
    fun observeForCorpus(corpusId: String): Flow<List<QueryResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: QueryResultEntity)

    @Query("SELECT * FROM query_results WHERE corpusId = :corpusId AND syncState != 'SYNCED'")
    suspend fun getUnsyncedForCorpus(corpusId: String): List<QueryResultEntity>

    @Query(
        "UPDATE query_results SET possiblyStale = 1 " +
            "WHERE corpusId = :corpusId AND corpusFingerprint != :currentFingerprint AND syncState = 'SYNCED'"
    )
    suspend fun markStaleWhereFingerprintDiffers(corpusId: String, currentFingerprint: String)

    @Query("DELETE FROM query_results WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE query_results SET syncState = 'FAILED' WHERE id = :id")
    suspend fun markFailed(id: String)
}

@Dao
interface PendingQueryDao {
    @Query("SELECT * FROM pending_queries ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingQueryEntity>>

    @Query("SELECT * FROM pending_queries ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingQueryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pending: PendingQueryEntity)

    @Update
    suspend fun update(pending: PendingQueryEntity)

    @Delete
    suspend fun delete(pending: PendingQueryEntity)

    @Query("SELECT COUNT(*) FROM pending_queries")
    fun observeCount(): Flow<Int>
}
