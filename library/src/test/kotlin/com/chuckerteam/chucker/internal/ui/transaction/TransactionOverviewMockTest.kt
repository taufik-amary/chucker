package com.chuckerteam.chucker.internal.ui.transaction

import android.app.Application
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.internal.data.entity.HttpTransaction
import com.chuckerteam.chucker.internal.data.entity.createRequest
import com.chuckerteam.chucker.internal.data.repository.HttpTransactionRepository
import com.chuckerteam.chucker.internal.data.repository.RepositoryProvider
import com.chuckerteam.chucker.internal.ui.mock.MockEndpointActivity
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
internal class TransactionOverviewMockTest {
    private val repository = mockk<HttpTransactionRepository>()

    @Before
    fun setUp() {
        mockkObject(RepositoryProvider)
        every { RepositoryProvider.initialize(any()) } just runs
        every { RepositoryProvider.transaction() } returns repository
    }

    @After
    fun tearDown() = unmockkAll()

    private fun launchWith(transaction: HttpTransaction): TransactionActivity {
        every { repository.getTransaction(any()) } returns MutableLiveData(transaction)
        val application = ApplicationProvider.getApplicationContext<Application>()
        val activity =
            Robolectric
                .buildActivity(TransactionActivity::class.java, Intent(application, TransactionActivity::class.java))
                .setup()
                .get()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    private fun TransactionActivity.mockRow() = findViewById<TextView>(R.id.mockValue)

    @Test
    fun `endpoint screen is opened with the encoded url and the recorded response of the call`() {
        val transaction =
            createRequest().apply {
                id = 42L
                populateUrl("https://example.com/users/a%2Fb?page=1".toHttpUrl())
            }
        val activity = launchWith(transaction)

        activity.findViewById<View>(R.id.setupMock).performClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertThat(intent.getStringExtra("request_url")).isEqualTo("https://example.com/users/a%2Fb?page=1")
        assertThat(intent.getLongExtra("transaction_id", -1L)).isEqualTo(42L)
        assertThat(intent.component?.className).isEqualTo(MockEndpointActivity::class.java.name)
    }

    @Test
    fun `endpoint screen is told when the call was recorded by a network interceptor`() {
        val activity = launchWith(createRequest().apply { isNetworkInterceptor = true })

        activity.findViewById<View>(R.id.setupMock).performClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertThat(intent.getBooleanExtra("network_interceptor", false)).isTrue()
    }

    @Test
    fun `row is hidden when no rule matched`() {
        val activity = launchWith(createRequest())

        assertThat(activity.mockRow().visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `row shows where a map remote rule sent the call`() {
        val activity = launchWith(createRequest().apply { mappedUrl = "https://mock.example.com/" })

        assertThat(activity.mockRow().visibility).isEqualTo(View.VISIBLE)
        assertThat(activity.mockRow().text.toString()).isEqualTo("Map Remote → https://mock.example.com/")
    }

    @Test
    fun `row shows which mock answered the call, and how`() {
        val activity =
            launchWith(
                createRequest().apply {
                    mapLocalMode = "MOCK"
                    mockName = "Server down"
                },
            )

        assertThat(activity.mockRow().text.toString()).isEqualTo("Server down · Map Local · Mock")
    }

    @Test
    fun `row tells that a matching rule was not applied`() {
        val activity = launchWith(createRequest().apply { isMockIgnored = true })

        assertThat(activity.mockRow().visibility).isEqualTo(View.VISIBLE)
        assertThat(activity.mockRow().text.toString()).isEqualTo("Not applied: mocking requires addInterceptor")
    }
}
