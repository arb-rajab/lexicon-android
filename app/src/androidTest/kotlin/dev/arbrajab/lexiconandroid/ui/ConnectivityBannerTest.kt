package dev.arbrajab.lexiconandroid.ui

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.arbrajab.lexiconandroid.connectivity.ConnectivityState
import dev.arbrajab.lexiconandroid.ui.components.ConnectivityBanner
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI test. NOTE: this environment has no Android
 * emulator/device available to actually run `connectedAndroidTest` — see
 * `docs/project-memory/07-testing-strategy.md` and the backlog item tracking
 * getting this executing in CI (e.g. via a hosted emulator runner). The test
 * is written to the real contract so it's ready to run as soon as that's
 * wired up; it has not been executed in this session.
 */
class ConnectivityBannerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun offlineBanner_showsOfflineMessage_whenOffline() {
        composeTestRule.setContent {
            ConnectivityBanner(connectivityState = ConnectivityState.OFFLINE, pendingCount = 0)
        }

        composeTestRule.onNodeWithText("Offline — showing cached data").assertIsDisplayed()
    }

    @Test
    fun offlineBanner_showsQueuedCount_whenOfflineWithPendingQueries() {
        composeTestRule.setContent {
            ConnectivityBanner(connectivityState = ConnectivityState.OFFLINE, pendingCount = 3)
        }

        composeTestRule.onNodeWithText("Offline — 3 queries queued").assertIsDisplayed()
    }

    @Test
    fun offlineBanner_isHidden_whenOnlineWithNoPendingQueries() {
        composeTestRule.setContent {
            ConnectivityBanner(connectivityState = ConnectivityState.ONLINE, pendingCount = 0)
        }

        composeTestRule.onNodeWithText("Offline — showing cached data").assertDoesNotExist()
    }
}
