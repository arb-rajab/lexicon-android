package dev.arbrajab.lexiconandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.arbrajab.lexiconandroid.navigation.LexiconNavHost
import dev.arbrajab.lexiconandroid.sync.SyncQueueWorker
import dev.arbrajab.lexiconandroid.ui.theme.LexiconTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LexiconApplication
        // Opportunistic drain: if anything was queued from a previous session, try to sync it
        // as soon as the app is opened again (SyncQueueWorker also gets enqueued right when a
        // query is queued while offline — this covers the "app was closed the whole time" case).
        SyncQueueWorker.enqueue(applicationContext)
        setContent {
            LexiconTheme {
                LexiconNavHost(app = app)
            }
        }
    }
}
