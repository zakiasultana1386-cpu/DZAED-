package com.example

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.ui.viewmodel.LedgerViewModel
import com.example.ui.viewmodel.ShareableLinkSession
import com.example.ui.viewmodel.ShareableViewUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShareableViewTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shareable link session detects expiration accurately`() {
        val now = 1_700_000_000_000L
        val session = ShareableLinkSession(
            username = "rahman_textiles",
            token = "abc1234567",
            deepLinkUrl = "tradeledger://share?u=rahman_textiles&token=abc1234567&exp=${now + 60_000L}",
            webShareUrl = "http://127.0.0.1:8765/share?u=rahman_textiles&token=abc1234567",
            expiresAtMillis = now + 60_000L
        )

        assertFalse(session.isExpired(now + 30_000L))
        assertTrue(session.isExpired(now + 60_001L))
    }

    @Test
    fun `generating link and handling valid and expired deep links updates state`() =
        runBlocking(Dispatchers.Default) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val viewModel = LedgerViewModel(app)
            try {
                val session = viewModel.generateShareableLink("@Rahman Textiles", ttlMinutes = 15L)
                assertEquals("rahman_textiles", session.username)
                assertTrue(session.deepLinkUrl.startsWith("tradeledger://share?u=rahman_textiles&token="))

                // Open valid deep-link via local navigation handler
                viewModel.handleIncomingDeepLink(Uri.parse(session.deepLinkUrl))
                val activeState = withTimeout(5_000L) {
                    viewModel.shareableViewState.first { it is ShareableViewUiState.Active }
                } as ShareableViewUiState.Active

                assertEquals("rahman_textiles", activeState.username)
                assertFalse(activeState.isExpired)
                assertEquals(session.deepLinkUrl, activeState.deepLinkUrl)

                // Expired deep-link should flag isExpired = true
                val pastExp = System.currentTimeMillis() - 60_000L
                viewModel.handleIncomingDeepLink(
                    Uri.parse("tradeledger://share?u=rahman_textiles&token=expired&exp=$pastExp")
                )
                val expiredState = withTimeout(5_000L) {
                    viewModel.shareableViewState.first {
                        it is ShareableViewUiState.Active && it.isExpired
                    }
                } as ShareableViewUiState.Active
                assertTrue(expiredState.isExpired)

                // Short host tradeledger://u/<username> should also resolve
                viewModel.handleIncomingDeepLink(Uri.parse("tradeledger://u/apex_supplies"))
                val shortState = withTimeout(5_000L) {
                    viewModel.shareableViewState.first {
                        it is ShareableViewUiState.Active && it.username == "apex_supplies"
                    }
                } as ShareableViewUiState.Active
                assertFalse(shortState.isExpired)

                // Closing returns to Closed state
                viewModel.closeShareableView()
                val closedState = withTimeout(5_000L) {
                    viewModel.shareableViewState.first { it is ShareableViewUiState.Closed }
                }
                assertTrue(closedState is ShareableViewUiState.Closed)
            } finally {
                viewModel.stopWebPortal()
            }
        }
}
