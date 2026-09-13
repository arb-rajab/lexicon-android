package dev.arbrajab.lexiconandroid.di

import dev.arbrajab.lexiconandroid.data.ServerConfig
import dev.arbrajab.lexiconandroid.data.remote.LexiconApi
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Attaches the user-configured static auth header, if any (see ServerConfigStore). */
private class StaticHeaderInterceptor(private val configProvider: () -> ServerConfig) :
    Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val config = configProvider()
        val request = chain.request().newBuilder()
        if (config.authHeaderName.isNotBlank()) {
            request.header(config.authHeaderName, config.authHeaderValue)
        }
        return chain.proceed(request.build())
    }
}

/**
 * Builds a [LexiconApi] for the given server config. Not a singleton: the
 * base URL and auth header are user-configurable at runtime (ServerConfig
 * screen), so callers ask [ApiClientFactory] for a fresh client whenever the
 * config might have changed rather than caching one at process start.
 */
object ApiClientFactory {
    private val json: Json = Json { ignoreUnknownKeys = true }

    fun create(config: ServerConfig): LexiconApi {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client =
            OkHttpClient.Builder()
                .addInterceptor(StaticHeaderInterceptor { config })
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(LexiconApi::class.java)
    }
}
