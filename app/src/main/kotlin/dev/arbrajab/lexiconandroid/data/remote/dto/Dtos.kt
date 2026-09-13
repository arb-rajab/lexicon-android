package dev.arbrajab.lexiconandroid.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types mirroring lexicon's `05-api-contracts.md` response shapes.
 * Field names match the backend's JSON exactly (snake_case) so no custom
 * serial-name mapping is needed beyond what's declared here.
 */

@Serializable
data class CorpusDto(
    val id: String,
    val name: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class CorpusDetailDto(
    val id: String,
    val name: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("document_count") val documentCount: Int,
)

@Serializable
data class CorpusCreateRequest(val name: String)

@Serializable
data class DocumentDto(
    val id: String,
    @SerialName("source_filename") val sourceFilename: String,
    val version: Int,
    val status: String,
    @SerialName("chunk_count") val chunkCount: Int,
)

@Serializable
data class DocumentDetailDto(
    val id: String,
    @SerialName("source_filename") val sourceFilename: String,
    val version: Int,
    val status: String,
    @SerialName("chunk_count") val chunkCount: Int,
    @SerialName("uploaded_at") val uploadedAt: String,
)

@Serializable
data class QueryRequestDto(val question: String)

@Serializable
data class CitationDto(
    @SerialName("chunk_id") val chunkId: String,
    @SerialName("document_id") val documentId: String,
    @SerialName("source_filename") val sourceFilename: String,
    @SerialName("section_heading") val sectionHeading: String,
    @SerialName("claim_text") val claimText: String,
)

@Serializable
data class QueryResponseDto(
    @SerialName("query_log_id") val queryLogId: String,
    val answered: Boolean,
    val answer: String?,
    val citations: List<CitationDto> = emptyList(),
    @SerialName("refusal_reason") val refusalReason: String?,
    @SerialName("retrieved_chunk_count") val retrievedChunkCount: Int,
)

@Serializable
data class ApiErrorEnvelope(val error: ApiErrorBody)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
    val field: String? = null,
)
