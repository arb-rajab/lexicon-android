package dev.arbrajab.lexiconandroid.remote

import dev.arbrajab.lexiconandroid.data.ServerConfig
import dev.arbrajab.lexiconandroid.data.remote.dto.QueryRequestDto
import dev.arbrajab.lexiconandroid.di.ApiClientFactory
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * Round-trips real HTTP requests through [ApiClientFactory] and
 * [dev.arbrajab.lexiconandroid.data.remote.LexiconApi] against a [MockWebServer], catching
 * serialization/URL-construction bugs that unit tests faking the API interface directly can't
 * (see backlog).
 */
class LexiconApiIntegrationTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun apiFor(config: ServerConfig = ServerConfig(baseUrl = server.url("/").toString())) =
        ApiClientFactory.create(config)

    @Test
    fun `listCorpora hits the right path and parses snake_case fields`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    [{"id":"c1","name":"Handbook","created_at":"2026-01-01T00:00:00Z"}]
                    """.trimIndent()
                )
            )

            val corpora = apiFor().listCorpora()

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/corpora", request.path)
            assertEquals(1, corpora.size)
            assertEquals("c1", corpora.first().id)
            assertEquals("Handbook", corpora.first().name)
            assertEquals("2026-01-01T00:00:00Z", corpora.first().createdAt)
        }

    @Test
    fun `askQuestion posts the question body to the corpus-scoped path`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "query_log_id": "q1",
                      "answered": true,
                      "answer": "42",
                      "citations": [],
                      "refusal_reason": null,
                      "retrieved_chunk_count": 3
                    }
                    """.trimIndent()
                )
            )

            val response = apiFor().askQuestion("corpus-1", QueryRequestDto("What is the answer?"))

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/corpora/corpus-1/query", request.path)
            assertTrue(request.body.readUtf8().contains("\"question\":\"What is the answer?\""))
            assertEquals("q1", response.queryLogId)
            assertEquals(true, response.answered)
            assertEquals("42", response.answer)
            assertNull(response.refusalReason)
            assertEquals(3, response.retrievedChunkCount)
        }

    @Test
    fun `getCorpus URL-encodes the corpus id path segment`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {"id":"c/1","name":"Weird Id","created_at":"2026-01-01T00:00:00Z",
                    "document_count":2}
                    """.trimIndent()
                )
            )

            apiFor().getCorpus("c/1")

            val request = server.takeRequest()
            assertEquals("/api/v1/corpora/c%2F1", request.path)
        }

    @Test
    fun `configured static auth header is attached to every request`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
            val config =
                ServerConfig(
                    baseUrl = server.url("/").toString(),
                    authHeaderName = "X-Api-Key",
                    authHeaderValue = "secret-token"
                )

            apiFor(config).listCorpora()

            val request = server.takeRequest()
            assertEquals("secret-token", request.getHeader("X-Api-Key"))
        }

    @Test
    fun `no auth header is sent when none is configured`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

            apiFor().listCorpora()

            val request = server.takeRequest()
            assertNull(request.getHeader("X-Api-Key"))
        }

    @Test
    fun `a 4xx response surfaces as an HttpException rather than being swallowed`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(422).setBody(
                    """{"error":{"code":"invalid_question","message":"too short"}}"""
                )
            )

            try {
                apiFor().askQuestion("corpus-1", QueryRequestDto(""))
                throw AssertionError("expected HttpException")
            } catch (exc: HttpException) {
                assertEquals(422, exc.code())
            }
        }
}
