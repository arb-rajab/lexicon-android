package dev.arbrajab.lexiconandroid

import android.app.Application
import dev.arbrajab.lexiconandroid.connectivity.ConnectivityObserver
import dev.arbrajab.lexiconandroid.connectivity.NetworkConnectivityObserver
import dev.arbrajab.lexiconandroid.data.ServerConfig
import dev.arbrajab.lexiconandroid.data.ServerConfigStore
import dev.arbrajab.lexiconandroid.data.local.AppDatabase
import dev.arbrajab.lexiconandroid.data.local.dao.PendingQueryDao
import dev.arbrajab.lexiconandroid.data.remote.LexiconApi
import dev.arbrajab.lexiconandroid.data.repository.CorpusRepository
import dev.arbrajab.lexiconandroid.data.repository.QueryRepository
import dev.arbrajab.lexiconandroid.di.ApiClientFactory
import dev.arbrajab.lexiconandroid.sync.SyncQueueWorker
import kotlinx.coroutines.runBlocking

/**
 * Composition root. This app is small and scoped enough that a hand-rolled
 * graph is clearer than pulling in a DI framework (Hilt/Dagger) purely for
 * its own sake — see ADR-0004.
 */
class LexiconApplication : Application() {
    lateinit var database: AppDatabase
        private set

    lateinit var serverConfigStore: ServerConfigStore
        private set

    lateinit var connectivityObserver: ConnectivityObserver
        private set

    lateinit var corpusRepository: CorpusRepository
        private set

    lateinit var queryRepository: QueryRepository
        private set

    val pendingQueryDao: PendingQueryDao
        get() = database.pendingQueryDao()

    /** Builds an API client against the currently saved server config. Never cached: the
     * config can change at runtime from the Server Settings screen. */
    private fun currentApi(): LexiconApi {
        val config: ServerConfig = runBlocking { serverConfigStore.current() }
        return ApiClientFactory.create(config)
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        serverConfigStore = ServerConfigStore(this)
        connectivityObserver = NetworkConnectivityObserver(this)
        corpusRepository =
            CorpusRepository(
                corpusDao = database.corpusDao(),
                documentDao = database.documentDao(),
                queryResultDao = database.queryResultDao(),
                apiProvider = ::currentApi,
            )
        queryRepository =
            QueryRepository(
                queryResultDao = database.queryResultDao(),
                pendingQueryDao = database.pendingQueryDao(),
                corpusDao = database.corpusDao(),
                documentDao = database.documentDao(),
                connectivityObserver = connectivityObserver,
                apiProvider = ::currentApi,
                onQueryQueued = { SyncQueueWorker.enqueue(this) },
            )
    }
}
