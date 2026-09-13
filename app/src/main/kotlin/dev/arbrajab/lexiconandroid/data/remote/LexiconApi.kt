package dev.arbrajab.lexiconandroid.data.remote

import dev.arbrajab.lexiconandroid.data.remote.dto.CorpusCreateRequest
import dev.arbrajab.lexiconandroid.data.remote.dto.CorpusDetailDto
import dev.arbrajab.lexiconandroid.data.remote.dto.CorpusDto
import dev.arbrajab.lexiconandroid.data.remote.dto.DocumentDto
import dev.arbrajab.lexiconandroid.data.remote.dto.QueryRequestDto
import dev.arbrajab.lexiconandroid.data.remote.dto.QueryResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit surface for the endpoints this app actually uses, per
 * lexicon's `docs/project-memory/05-api-contracts.md`. Document upload and
 * query-log audit endpoints are intentionally not mirrored here — this is a
 * consumer query client, not an admin console (see ADR-0002, non-goals).
 */
interface LexiconApi {
    @GET("api/v1/corpora")
    suspend fun listCorpora(): List<CorpusDto>

    @POST("api/v1/corpora")
    suspend fun createCorpus(@Body request: CorpusCreateRequest): CorpusDto

    @GET("api/v1/corpora/{corpusId}")
    suspend fun getCorpus(@Path("corpusId") corpusId: String): CorpusDetailDto

    @GET("api/v1/corpora/{corpusId}/documents")
    suspend fun listDocuments(@Path("corpusId") corpusId: String): List<DocumentDto>

    @POST("api/v1/corpora/{corpusId}/query")
    suspend fun askQuestion(
        @Path("corpusId") corpusId: String,
        @Body request: QueryRequestDto,
    ): QueryResponseDto
}
