package dev.arbrajab.lexiconandroid.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import dev.arbrajab.lexiconandroid.connectivity.ConnectivityState
import dev.arbrajab.lexiconandroid.ui.components.ConnectivityBanner
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI test, now running for real on a hosted emulator in CI (see
 * `.github/workflows/android-ci.yml`'s `instrumented-tests` job and
 * `docs/project-memory/07-testing-strategy.md`).
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

        composeTestRule.onAllNodesWithText("Offline — showing cached data").assertCountEquals(0)
    }
}
